package com.tjclp.xlcr.server.mcp

import com.tjclp.xlcr.Pipeline
import com.tjclp.xlcr.models.excel.{SheetData, SheetsData}
import com.tjclp.xlcr.utils.FileUtils

import java.nio.file.{Files, Path}
import java.util.{Map => JMap, UUID}
import scala.collection.mutable
import scala.util.{Try, Success, Failure}
import scala.jdk.CollectionConverters._
import org.slf4j.LoggerFactory

/**
 * Manages document editing sessions
 */
class DocumentSession private (val originalFile: Path) {
  private val logger = LoggerFactory.getLogger(getClass)
  
  val sessionId: String = UUID.randomUUID().toString()
  
  private val tempDir: Path = Files.createTempDirectory(s"xlcr_session_$sessionId")
  private val workingFile: Path = tempDir.resolve("working.xlsx")
  private val workingJson: Path = tempDir.resolve("working.json")
  
  // Initialize by copying the original file
  Files.copy(originalFile, workingFile)
  logger.info(s"Created session $sessionId for file ${originalFile.getFileName}")
  
  /**
   * Convert the working document to JSON representation
   */
  def toJson(): Map[String, Any] = {
    // Use Pipeline to convert Excel to JSON
    logger.debug(s"Converting ${workingFile.getFileName} to JSON")
    Pipeline.run(workingFile.toString, workingJson.toString, false)
    
    // Read the JSON and parse it
    val jsonBytes = Files.readAllBytes(workingJson)
    val jsonStr = new String(jsonBytes)
    
    // Return as a map - in the future we'd parse the JSON properly
    Map(
      "status" -> "success",
      "content" -> jsonStr
    )
  }
  
  /**
   * Apply edits from JSON patch to the document
   */
  def applyEdit(editJson: String): Boolean = {
    logger.debug(s"Applying edit to session $sessionId")
    // Write edit JSON to temp file
    val diffFile = tempDir.resolve("diff.json")
    Files.write(diffFile, editJson.getBytes)
    
    // Use Pipeline in diff mode to apply changes
    Try(Pipeline.run(diffFile.toString, workingFile.toString, true)) match {
      case Success(_) => 
        logger.info(s"Successfully applied edit to session $sessionId")
        true
      case Failure(e) => 
        logger.error(s"Failed to apply edit to session $sessionId", e)
        false
    }
  }
  
  /**
   * Save changes back to original file
   */
  def save(): Boolean = {
    logger.info(s"Saving session $sessionId to ${originalFile.getFileName}")
    Try(Files.copy(workingFile, originalFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)) match {
      case Success(_) => 
        logger.info(s"Successfully saved session $sessionId")
        true 
      case Failure(e) => 
        logger.error(s"Failed to save session $sessionId", e)
        false
    }
  }
  
  /**
   * Transform document to another format
   */
  def transform(format: String): String = {
    logger.info(s"Transforming session $sessionId to format $format")
    val outputFile = tempDir.resolve(s"output.$format")
    Pipeline.run(workingFile.toString, outputFile.toString, false)
    
    // Return path or content depending on format
    outputFile.toString
  }
  
  /**
   * Clean up temp files
   */
  def cleanup(): Unit = {
    logger.info(s"Cleaning up session $sessionId")
    FileUtils.deleteRecursively(tempDir)
  }
}

/**
 * Companion object for session management
 */
object DocumentSession {
  private val logger = LoggerFactory.getLogger(getClass)
  private val activeSessions = mutable.Map[String, DocumentSession]()
  
  /**
   * Create a new document session
   */
  def create(file: Path): String = {
    logger.info(s"Creating new session for file ${file.getFileName}")
    val session = new DocumentSession(file)
    activeSessions(session.sessionId) = session
    session.sessionId
  }
  
  /**
   * Get an existing session
   */
  def get(sessionId: String): Option[DocumentSession] = {
    logger.debug(s"Getting session $sessionId")
    activeSessions.get(sessionId)
  }
  
  /**
   * Remove a session and clean up
   */
  def remove(sessionId: String): Unit = {
    logger.info(s"Removing session $sessionId")
    activeSessions.get(sessionId).foreach { session =>
      session.cleanup()
      activeSessions.remove(sessionId)
    }
  }
}