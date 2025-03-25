# XLCR Scala MCP Server Implementation Plan

This document outlines our plan to refactor the current Kotlin-based MCP server to a pure Scala implementation that directly uses the Java MCP SDK. This approach will allow us to better support Excel and PowerPoint editing capabilities through a standardized protocol while maintaining compatibility with the existing XLCR core.

## 1. Project Setup

First, we'll update the build.sbt to include the Java MCP SDK dependencies, replacing the Kotlin MCP:

```scala
// New MCP module
lazy val server = (project in file("server"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "xlcr-server",
    libraryDependencies ++= Seq(
      // MCP Java SDK
      "io.modelcontextprotocol.sdk" % "mcp" % "0.8.0",
      "io.modelcontextprotocol.sdk" % "mcp-stdlib" % "0.8.0",
      
      // HTTP server for SSE transport (optional)
      "org.http4s" %% "http4s-dsl" % "0.23.24",
      "org.http4s" %% "http4s-ember-server" % "0.23.24",
      
      // JSON handling
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      
      // Jackson for MCP
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.16.0",
      
      // Testing
      "org.scalatest" %% "scalatest" % "3.2.18" % Test
    )
  )
```

## 2. Core Server Implementation

Create the main server implementation in Scala, which will replace the Kotlin MCP server:

```scala
// File: server/src/main/scala/com/tjclp/xlcr/server/XlcrMcpServer.scala
package com.tjclp.xlcr.server

import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.sdk.server.{McpServer, McpSyncServer, ServerCapabilities, StdioServerTransportProvider}
import io.modelcontextprotocol.sdk.server.features.{CallToolResult, McpServerFeatures}
import io.modelcontextprotocol.sdk.Tool
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}
import java.nio.file.{Files, Path, Paths}
import java.util.{Map => JMap}

object XlcrMcpServer {
  private val logger = LoggerFactory.getLogger(getClass)
  
  def createServer(): McpSyncServer = {
    // Create transport provider (STDIO)
    val transportProvider = new StdioServerTransportProvider(new ObjectMapper())
    
    // Build server with capabilities
    val server = McpServer.sync(transportProvider)
      .serverInfo("xlcr-server", "1.0.0")
      .capabilities(ServerCapabilities.builder()
        .tools(true)
        .resources(true)
        .logging()
        .build())
      .build()
      
    // Register tools
    registerTools(server)
    
    logger.info("XLCR MCP Server initialized")
    server
  }
  
  private def registerTools(server: McpSyncServer): Unit = {
    // Register Excel Editor tool
    val excelToolSpec = new McpServerFeatures.SyncToolSpecification(
      new Tool("Excel Editor", 
               "Edit Excel files with markdown, view as SVG, and perform file transformations",
               """{"type":"object","properties":{"action":{"type":"string"},"sessionId":{"type":"string"},"path":{"type":"string"},"content":{"type":"string"}}}"""),
      (exchange, args) => handleExcelTool(args)
    )
    server.addTool(excelToolSpec)
    
    // Register PowerPoint Editor tool (similar structure)
    val pptToolSpec = new McpServerFeatures.SyncToolSpecification(
      new Tool("PowerPoint Editor",
               "Edit PowerPoint presentations, modify slides, and convert formats",
               """{"type":"object","properties":{"action":{"type":"string"},"sessionId":{"type":"string"},"path":{"type":"string"},"slideIndex":{"type":"number"},"content":{"type":"string"}}}"""),
      (exchange, args) => handlePowerPointTool(args)
    )
    server.addTool(pptToolSpec)
  }
  
  private def handleExcelTool(args: JMap[String, AnyRef]): CallToolResult = {
    val action = args.get("action").asInstanceOf[String]
    
    Try {
      action match {
        case "open" => 
          val path = args.get("path").asInstanceOf[String]
          val sessionId = DocumentSession.create(Paths.get(path))
          new CallToolResult(Map("sessionId" -> sessionId, "status" -> "open").asJava, false)
          
        case "edit" =>
          val sessionId = args.get("sessionId").asInstanceOf[String]
          val content = args.get("content").asInstanceOf[String]
          // Find session and apply edits
          DocumentSession.get(sessionId) match {
            case Some(session) => 
              session.applyEdit(content)
              new CallToolResult(Map("status" -> "success").asJava, false)
            case None => 
              new CallToolResult(Map("error" -> "Session not found").asJava, false)
          }
          
        case "save" =>
          val sessionId = args.get("sessionId").asInstanceOf[String]
          DocumentSession.get(sessionId) match {
            case Some(session) =>
              session.save()
              new CallToolResult(Map("status" -> "saved").asJava, false)
            case None =>
              new CallToolResult(Map("error" -> "Session not found").asJava, false)
          }
          
        case "getJson" =>
          val sessionId = args.get("sessionId").asInstanceOf[String]
          DocumentSession.get(sessionId) match {
            case Some(session) =>
              val json = session.toJson()
              new CallToolResult(json, false)
            case None =>
              new CallToolResult(Map("error" -> "Session not found").asJava, false)
          }
          
        case "transform" =>
          val sessionId = args.get("sessionId").asInstanceOf[String]
          val format = args.get("format").asInstanceOf[String] 
          DocumentSession.get(sessionId) match {
            case Some(session) =>
              val result = session.transform(format)
              new CallToolResult(Map("status" -> "transformed", "result" -> result).asJava, false)
            case None =>
              new CallToolResult(Map("error" -> "Session not found").asJava, false)
          }
          
        case _ =>
          new CallToolResult(Map("error" -> s"Unknown action: $action").asJava, false)
      }
    } match {
      case Success(result) => result
      case Failure(error) => 
        logger.error(s"Error handling Excel tool action '$action'", error)
        new CallToolResult(Map("error" -> error.getMessage).asJava, false)
    }
  }
  
  private def handlePowerPointTool(args: JMap[String, AnyRef]): CallToolResult = {
    // Similar implementation to handleExcelTool but for PowerPoint operations
    // Will handle slide-specific operations
    new CallToolResult(Map("status" -> "not_implemented").asJava, false)
  }
}
```

## 3. Document Session Management

Create a Document Session class to handle file operations and editing:

```scala
// File: server/src/main/scala/com/tjclp/xlcr/server/DocumentSession.scala
package com.tjclp.xlcr.server

import com.tjclp.xlcr.Pipeline
import com.tjclp.xlcr.models.excel.{SheetData, SheetsData}
import com.tjclp.xlcr.utils.FileUtils

import java.nio.file.{Files, Path}
import java.util.{Map => JMap, UUID}
import scala.collection.mutable
import scala.util.{Try, Success, Failure}
import scala.jdk.CollectionConverters._

/**
 * Manages document editing sessions
 */
class DocumentSession private (val originalFile: Path) {
  val sessionId: String = UUID.randomUUID().toString()
  
  private val tempDir: Path = Files.createTempDirectory(s"xlcr_session_$sessionId")
  private val workingFile: Path = tempDir.resolve("working.xlsx")
  private val workingJson: Path = tempDir.resolve("working.json")
  
  // Initialize by copying the original file
  Files.copy(originalFile, workingFile)
  
  /**
   * Convert the working document to JSON representation
   */
  def toJson(): JMap[String, AnyRef] = {
    // Use Pipeline to convert Excel to JSON
    Pipeline.run(workingFile.toString, workingJson.toString, false)
    
    // Read the JSON and parse it (simplified)
    val jsonBytes = Files.readAllBytes(workingJson)
    val jsonStr = new String(jsonBytes)
    
    // Here we would parse the JSON properly
    // For now, return a simple structure
    Map(
      "status" -> "success",
      "content" -> jsonStr
    ).asJava
  }
  
  /**
   * Apply edits from JSON patch to the document
   */
  def applyEdit(editJson: String): Boolean = {
    // Write edit JSON to temp file
    val diffFile = tempDir.resolve("diff.json")
    Files.write(diffFile, editJson.getBytes)
    
    // Use Pipeline in diff mode to apply changes
    Try(Pipeline.run(diffFile.toString, workingFile.toString, true)) match {
      case Success(_) => true
      case Failure(e) => 
        // Log error and return false
        false
    }
  }
  
  /**
   * Save changes back to original file
   */
  def save(): Boolean = {
    Try(Files.copy(workingFile, originalFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)) match {
      case Success(_) => true 
      case Failure(_) => false
    }
  }
  
  /**
   * Transform document to another format
   */
  def transform(format: String): String = {
    val outputFile = tempDir.resolve(s"output.$format")
    Pipeline.run(workingFile.toString, outputFile.toString, false)
    
    // Return path or content depending on format
    outputFile.toString
  }
  
  /**
   * Clean up temp files
   */
  def cleanup(): Unit = {
    FileUtils.deleteRecursively(tempDir)
  }
}

/**
 * Companion object for session management
 */
object DocumentSession {
  private val activeSessions = mutable.Map[String, DocumentSession]()
  
  /**
   * Create a new document session
   */
  def create(file: Path): String = {
    val session = new DocumentSession(file)
    activeSessions(session.sessionId) = session
    session.sessionId
  }
  
  /**
   * Get an existing session
   */
  def get(sessionId: String): Option[DocumentSession] = {
    activeSessions.get(sessionId)
  }
  
  /**
   * Remove a session and clean up
   */
  def remove(sessionId: String): Unit = {
    activeSessions.get(sessionId).foreach { session =>
      session.cleanup()
      activeSessions.remove(sessionId)
    }
  }
}
```

## 4. Main Application Entry Point

Create the main application class to start the server:

```scala
// File: server/src/main/scala/com/tjclp/xlcr/server/main.scala
package com.tjclp.xlcr.server

import org.slf4j.LoggerFactory

object Main {
  private val logger = LoggerFactory.getLogger(getClass)
  
  def main(args: Array[String]): Unit = {
    // Parse command line arguments
    val mode = args.headOption.getOrElse("--stdio")
    val port = args.drop(1).headOption.map(_.toInt).getOrElse(3000)
    
    mode match {
      case "--stdio" => runStdioServer()
      case "--sse" => runSseServer(port)
      case _ => 
        logger.error(s"Unknown mode: $mode")
        System.exit(1)
    }
  }
  
  private def runStdioServer(): Unit = {
    logger.info("Starting XLCR MCP Server in STDIO mode")
    val server = XlcrMcpServer.createServer()
    
    // Create a never-ending Future to keep the server running
    // until the parent process terminates us
    try {
      logger.info("Server running, waiting for STDIO messages")
      // Block until server is done (parent process terminates)
      Thread.sleep(Long.MaxValue)
    } catch {
      case _: InterruptedException =>
        logger.info("Server interrupted, shutting down")
    } finally {
      server.close()
    }
  }
  
  private def runSseServer(port: Int): Unit = {
    logger.info(s"Starting XLCR MCP Server in SSE mode on port $port")
    // Implementation for SSE server (using HTTP4S or similar)
    // Not detailed here for brevity
    
    logger.error("SSE server mode not implemented yet")
    System.exit(1)
  }
}
```

## 5. Excel and PowerPoint Specific Handlers

We'll create specialized handlers for each document type:

```scala
// File: server/src/main/scala/com/tjclp/xlcr/server/ExcelDocumentHandler.scala
package com.tjclp.xlcr.server

import com.tjclp.xlcr.models.excel.SheetsData
import com.tjclp.xlcr.parsers.excel.SheetsDataExcelParser
import com.tjclp.xlcr.renderers.excel.SheetsDataJsonRenderer

import java.nio.file.{Files, Path}
import java.util.{Map => JMap}
import scala.jdk.CollectionConverters._

/**
 * Handles Excel-specific operations
 */
class ExcelDocumentHandler(workingFile: Path) {
  
  def getWorkbook(): SheetsData = {
    // Use XLCR parsers to read Excel file
    // Simplified implementation
    null
  }
  
  def applyChanges(changes: JMap[String, Any]): Boolean = {
    // Apply changes to Excel workbook
    // Handle cell updates, formatting, etc.
    true
  }
  
  def toJson(): String = {
    // Convert workbook to JSON using XLCR renderers
    ""
  }
  
  def save(outputPath: Path): Boolean = {
    // Save workbook to file
    true
  }
}
```

```scala
// File: server/src/main/scala/com/tjclp/xlcr/server/PowerPointDocumentHandler.scala
package com.tjclp.xlcr.server

import java.nio.file.Path
import java.util.{Map => JMap}

/**
 * Handles PowerPoint-specific operations
 */
class PowerPointDocumentHandler(workingFile: Path) {
  
  def getPresentation(): Any = {
    // Read PowerPoint file
    null
  }
  
  def editSlide(slideIndex: Int, content: String): Boolean = {
    // Apply edits to a slide
    true
  }
  
  def addSlide(content: String): Int = {
    // Add a new slide
    0
  }
  
  def toJson(): String = {
    // Convert presentation to JSON
    ""
  }
  
  def renderSlideImage(slideIndex: Int): Array[Byte] = {
    // Render slide as PNG image
    Array.emptyByteArray
  }
  
  def save(outputPath: Path): Boolean = {
    // Save presentation to file
    true
  }
}
```

## 6. Implementation Strategy and Timeline

### Phase 1: Core Server (2 weeks)
- Implement basic MCP server structure
- Create document session management
- Support Excel file opening and basic JSON conversion

### Phase 2: Excel Implementation (3 weeks)
- Extend Excel editing capabilities
- Implement formula handling
- Add cell formatting and styling

### Phase 3: PowerPoint Implementation (3 weeks)
- Add basic PowerPoint document handling
- Implement slide creation and editing
- Support image rendering of slides

### Phase 4: Transport & Integration (2 weeks)
- Implement SSE transport
- Add WebSocket support (optional)
- Integrate with existing XLCR pipeline components

## 7. Future Enhancements (Post-Initial Implementation)

1. **Advanced Transport Options**
   - Full SSE implementation with reconnection handling
   - WebSocket transport for bidirectional communication
   - Cluster support for multi-node deployments

2. **Enhanced Excel Features**
   - Real-time formula calculation
   - Chart manipulation and creation
   - Conditional formatting and advanced styling
   - Cell validation and data entry forms

3. **Advanced PowerPoint Support**
   - Slide layout templates and master slides
   - Shape and image manipulation
   - Animation and transition control
   - Embedded Excel charts and tables

4. **Framework Integration**
   - Spring Boot integration for enterprise deployments
   - Akka-based actor system for scalability
   - Event-driven architecture for real-time collaboration

5. **LLM-Assisted Editing**
   - AI-powered formula suggestions
   - Natural language to chart conversion
   - Layout optimization for presentations
   - Data analysis and insight generation

## 8. Benefits of Scala MCP Implementation

1. **Direct Integration**: Seamless integration with existing Scala XLCR core
2. **Performance**: Native JVM integration without cross-language overhead
3. **Type Safety**: Leverage Scala's type system for robust implementation
4. **Functional Approach**: Use Scala's functional programming features for cleaner code
5. **Maintainability**: Unified codebase in a single language
6. **Extensibility**: Easier to extend with Scala's powerful abstractions

This implementation will completely replace the Kotlin-based server with a pure Scala solution that directly uses the Java MCP SDK. It maintains compatibility with existing XLCR core components while providing a more integrated experience and better support for Excel and PowerPoint functionality.