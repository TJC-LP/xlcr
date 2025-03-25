package com.tjclp.xlcr.server.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.server.{McpServer, McpSyncServer, McpSyncServerExchange}
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpSchema.{ServerCapabilities, Tool}
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}
import java.nio.file.{Files, Path, Paths}
import java.util.{Map => JMap, HashMap => JHashMap, List => JList, ArrayList => JArrayList}
import scala.jdk.CollectionConverters._

/**
 * Main MCP server implementation for XLCR
 * Provides Excel and PowerPoint editing capabilities through MCP protocol
 * Uses the Java MCP SDK
 */
object XlcrMcpServer {
  private val logger = LoggerFactory.getLogger(getClass)
  
  /**
   * Create and configure the MCP server
   * @return Configured MCP server
   */
  def createServer(): McpSyncServer = {
    try {
      // Create transport provider (STDIO)
      val transportProvider = new StdioServerTransportProvider(new ObjectMapper())
      
      // Build server with capabilities
      val server = McpServer.sync(transportProvider)
        .serverInfo("xlcr-server", "1.0.0")
        .capabilities(ServerCapabilities.builder()
          .tools(true)
          .resources(true, false)
          .build())
        .tool(
          new Tool(
            "Excel Editor", 
            "Edit Excel files with markdown, view as SVG, and perform file transformations",
            """{"type":"object","properties":{"action":{"type":"string"},"sessionId":{"type":"string"},"path":{"type":"string"},"content":{"type":"string"}}}"""
          ),
          (exchange, args) => handleExcelTool(exchange, args)
        )
        .tool(
          new Tool(
            "PowerPoint Editor",
            "Edit PowerPoint presentations, modify slides, and convert formats",
            """{"type":"object","properties":{"action":{"type":"string"},"sessionId":{"type":"string"},"path":{"type":"string"},"slideIndex":{"type":"number"},"content":{"type":"string"}}}"""
          ),
          (exchange, args) => handlePowerPointTool(exchange, args)
        )
        .build()
        
      logger.info("XLCR MCP Server initialized")
      server
    } catch {
      case e: Exception =>
        logger.error(s"Failed to create MCP server: ${e.getMessage}", e)
        throw e
    }
  }
  
  /**
   * Helper method to create a content list for tool responses
   */
  private def createResponseContent(map: Map[String, Any]): JList[McpSchema.Content] = {
    // Convert our Scala map to a Java map
    val dataMap = new JHashMap[String, Any]()
    map.foreach { case (key, value) => 
      dataMap.put(key, value match {
        case s: String => s
        case l: List[_] => l.asJava
        case other => other.toString
      })
    }
    
    // Create a text content with JSON
    val jsonString = new ObjectMapper().writeValueAsString(dataMap)
    
    // Convert to a List of one Content item
    val contentList = new JArrayList[McpSchema.Content]()
    contentList.add(new McpSchema.TextContent(jsonString))
    
    contentList
  }
  
  /**
   * Handle Excel tool requests
   * @param exchange Server exchange context
   * @param args Tool arguments
   * @return Tool result
   */
  private def handleExcelTool(exchange: McpSyncServerExchange, args: JMap[String, AnyRef]): McpSchema.CallToolResult = {
    val action = args.get("action").asInstanceOf[String]
    
    try {
      val result = action match {
        case "open" => 
          val path = args.get("path").asInstanceOf[String]
          val sessionId = DocumentSession.create(Paths.get(path))
          new McpSchema.CallToolResult(
            createResponseContent(Map(
              "sessionId" -> sessionId, 
              "status" -> "open"
            )),
            false
          )
          
        case "edit" =>
          val sessionId = args.get("sessionId").asInstanceOf[String]
          val content = args.get("content").asInstanceOf[String]
          // Find session and apply edits
          DocumentSession.get(sessionId) match {
            case Some(session) => 
              session.applyEdit(content)
              new McpSchema.CallToolResult(
                createResponseContent(Map(
                  "status" -> "success"
                )),
                false
              )
            case None => 
              new McpSchema.CallToolResult(
                createResponseContent(Map(
                  "error" -> "Session not found"
                )),
                true
              )
          }
          
        case "save" =>
          val sessionId = args.get("sessionId").asInstanceOf[String]
          DocumentSession.get(sessionId) match {
            case Some(session) =>
              session.save()
              new McpSchema.CallToolResult(
                createResponseContent(Map(
                  "status" -> "saved"
                )),
                false
              )
            case None =>
              new McpSchema.CallToolResult(
                createResponseContent(Map(
                  "error" -> "Session not found"
                )),
                true
              )
          }
          
        case "getJson" =>
          val sessionId = args.get("sessionId").asInstanceOf[String]
          DocumentSession.get(sessionId) match {
            case Some(session) =>
              val json = session.toJson()
              new McpSchema.CallToolResult(
                createResponseContent(json),
                false
              )
            case None =>
              new McpSchema.CallToolResult(
                createResponseContent(Map(
                  "error" -> "Session not found"
                )),
                true
              )
          }
          
        case "transform" =>
          val sessionId = args.get("sessionId").asInstanceOf[String]
          val format = args.get("format").asInstanceOf[String] 
          DocumentSession.get(sessionId) match {
            case Some(session) =>
              val result = session.transform(format)
              new McpSchema.CallToolResult(
                createResponseContent(Map(
                  "status" -> "transformed", 
                  "result" -> result
                )),
                false
              )
            case None =>
              new McpSchema.CallToolResult(
                createResponseContent(Map(
                  "error" -> "Session not found"
                )),
                true
              )
          }
          
        case _ =>
          new McpSchema.CallToolResult(
            createResponseContent(Map(
              "error" -> s"Unknown action: $action"
            )),
            true
          )
      }
      
      result
    } catch {
      case e: Exception => 
        logger.error(s"Error handling Excel tool action '$action'", e)
        new McpSchema.CallToolResult(
          createResponseContent(Map(
            "error" -> e.getMessage
          )),
          true
        )
    }
  }
  
  /**
   * Handle PowerPoint tool requests
   * @param exchange Server exchange context
   * @param args Tool arguments
   * @return Tool result
   */
  private def handlePowerPointTool(exchange: McpSyncServerExchange, args: JMap[String, AnyRef]): McpSchema.CallToolResult = {
    val action = args.get("action").asInstanceOf[String]
    
    try {
      val result = action match {
        case "open" => 
          val path = args.get("path").asInstanceOf[String]
          val sessionId = DocumentSession.create(Paths.get(path))
          new McpSchema.CallToolResult(
            createResponseContent(Map(
              "sessionId" -> sessionId, 
              "status" -> "open"
            )),
            false
          )
          
        case "getSlides" =>
          val sessionId = args.get("sessionId").asInstanceOf[String]
          DocumentSession.get(sessionId) match {
            case Some(session) =>
              // This would be implemented when we add PowerPoint support
              new McpSchema.CallToolResult(
                createResponseContent(Map(
                  "slides" -> List.empty[String]
                )),
                false
              )
            case None =>
              new McpSchema.CallToolResult(
                createResponseContent(Map(
                  "error" -> "Session not found"
                )),
                true
              )
          }
          
        case _ =>
          new McpSchema.CallToolResult(
            createResponseContent(Map(
              "error" -> s"Unknown action: $action"
            )),
            true
          )
      }
      
      result
    } catch {
      case e: Exception => 
        logger.error(s"Error handling PowerPoint tool action '$action'", e)
        new McpSchema.CallToolResult(
          createResponseContent(Map(
            "error" -> e.getMessage
          )),
          true
        )
    }
  }
}