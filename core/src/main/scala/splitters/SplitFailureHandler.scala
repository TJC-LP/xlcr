package com.tjclp.xlcr
package splitters

import org.slf4j.LoggerFactory

import models.FileContent
import types.MimeType

/**
 * Trait that provides failure handling capabilities for document splitters.
 * Mix this trait into DocumentSplitter implementations to get consistent
 * failure handling behavior based on the configured SplitFailureMode.
 */
trait SplitFailureHandler {
  
  protected def logger: org.slf4j.Logger = LoggerFactory.getLogger(getClass)
  
  /**
   * Handle a splitting failure according to the configured failure mode.
   * This method implements the core logic for each failure mode type.
   * 
   * @param mode The failure mode to use
   * @param content The document content that failed to split
   * @param error The error message
   * @param cause The underlying exception (optional)
   * @param context Additional context about the failure
   * @tparam T The MIME type of the content
   * @return A sequence of chunks based on the failure mode
   */
  protected def handleSplitFailure[T <: MimeType](
    mode: SplitFailureMode,
    content: FileContent[T],
    error: String,
    cause: Throwable = null,
    context: Map[String, String] = Map.empty
  ): Seq[DocChunk[_ <: MimeType]] = mode match {
    
    case SplitFailureMode.ThrowException =>
      logger.error(s"Split failed for ${content.mimeType.mimeType}: $error", cause)
      val ex = cause match {
        case e: SplitException => 
          // Enrich existing SplitException with additional context
          e.withContext(context)
        case _ =>
          // Create new SplitException
          new SplitException(error, cause, content.mimeType.mimeType, context = context)
      }
      throw ex
      
    case SplitFailureMode.PreserveAsChunk =>
      logger.warn(s"Split failed for ${content.mimeType.mimeType}, preserving as single chunk: $error")
      Seq(DocChunk(content, "document", 0, 1))
      
    case SplitFailureMode.DropDocument =>
      logger.warn(s"Split failed for ${content.mimeType.mimeType}, dropping document: $error")
      Seq.empty
      
    case SplitFailureMode.TagAndPreserve =>
      val errorType = Option(cause).map(_.getClass.getSimpleName).getOrElse("SplitError")
      logger.warn(s"Split failed for ${content.mimeType.mimeType}, tagging and preserving: $error")
      
      val metadata = Map(
        "split_status" -> "failed",
        "error_message" -> error,
        "error_type" -> errorType,
        "mime_type" -> content.mimeType.mimeType,
        "failed_at" -> java.time.Instant.now().toString
      ) ++ context
      
      Seq(DocChunk(
        content,
        "failed_document",
        0,
        1,
        attrs = metadata
      ))
  }
  
  /**
   * Wrap splitting logic with failure handling.
   * This method should be used to wrap the main splitting logic in DocumentSplitter implementations.
   * It automatically handles exceptions based on the configured failure mode.
   * 
   * @param content The document content to split
   * @param config The split configuration including failure mode
   * @param splitLogic The actual splitting logic to execute
   * @tparam T The MIME type of the content
   * @return A sequence of chunks, with failures handled according to the mode
   */
  protected def withFailureHandling[T <: MimeType](
    content: FileContent[T],
    config: SplitConfig
  )(
    splitLogic: => Seq[DocChunk[_ <: MimeType]]
  ): Seq[DocChunk[_ <: MimeType]] = {
    try {
      val chunks = splitLogic
      
      // Add success metadata if in tag mode
      if (config.failureMode == SplitFailureMode.TagAndPreserve) {
        chunks.map(_.withMetadata("split_status", "success"))
      } else {
        chunks
      }
    } catch {
      // Handle SplitException specially to preserve its context
      case e: SplitException => 
        handleSplitFailure(
          config.failureMode,
          content,
          e.getMessage,
          e,
          config.failureContext ++ e.context
        )
        
      // Handle other expected exceptions with specific messages
      case e: IllegalArgumentException =>
        handleSplitFailure(
          config.failureMode,
          content,
          s"Invalid configuration or input: ${e.getMessage}",
          e,
          config.failureContext
        )
        
      case e: java.io.IOException =>
        handleSplitFailure(
          config.failureMode,
          content,
          s"IO error during splitting: ${e.getMessage}",
          e,
          config.failureContext
        )
        
      case e: OutOfMemoryError =>
        handleSplitFailure(
          config.failureMode,
          content,
          s"Out of memory during splitting (document may be too large)",
          e,
          config.failureContext + ("error_severity" -> "critical")
        )
        
      // Catch-all for unexpected exceptions
      case e: Exception =>
        handleSplitFailure(
          config.failureMode,
          content,
          s"Unexpected error during splitting: ${e.getMessage}",
          e,
          config.failureContext
        )
    }
  }
  
  /**
   * Helper method to handle invalid strategy errors.
   * Provides consistent error handling when a splitter doesn't support the requested strategy.
   * 
   * @param content The document content
   * @param config The split configuration
   * @param requestedStrategy The strategy that was requested
   * @param supportedStrategies List of strategies this splitter supports
   * @tparam T The MIME type of the content
   * @return A sequence of chunks based on the failure mode
   */
  protected def handleInvalidStrategy[T <: MimeType](
    content: FileContent[T],
    config: SplitConfig,
    requestedStrategy: String,
    supportedStrategies: Seq[String] = Seq.empty
  ): Seq[DocChunk[_ <: MimeType]] = {
    val error = s"Strategy '$requestedStrategy' is not supported for ${content.mimeType.mimeType}"
    val context = config.failureContext ++ Map(
      "requested_strategy" -> requestedStrategy,
      "supported_strategies" -> supportedStrategies.mkString(", ")
    )
    
    val exception = new InvalidStrategyException(
      content.mimeType.mimeType,
      requestedStrategy
    ).withContext(context)
    
    handleSplitFailure(
      config.failureMode,
      content,
      error,
      exception,
      context
    )
  }
  
  /**
   * Helper method to handle empty document errors.
   * Provides consistent handling when a document has no content to split.
   * 
   * @param content The document content
   * @param config The split configuration
   * @param details Additional details about what was expected
   * @tparam T The MIME type of the content
   * @return A sequence of chunks based on the failure mode
   */
  protected def handleEmptyDocument[T <: MimeType](
    content: FileContent[T],
    config: SplitConfig,
    details: String = "Document contains no splittable content"
  ): Seq[DocChunk[_ <: MimeType]] = {
    val exception = new EmptyDocumentException(content.mimeType.mimeType, details)
    
    handleSplitFailure(
      config.failureMode,
      content,
      exception.getMessage,
      exception,
      config.failureContext
    )
  }
}