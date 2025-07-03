package com.tjclp.xlcr
package splitters

/**
 * Base exception for all document splitting failures.
 * Provides rich context about the failure including MIME type, strategy, and custom metadata.
 * 
 * @param message The error message
 * @param cause The underlying cause (optional)
 * @param mimeType The MIME type of the document that failed to split
 * @param strategy The splitting strategy that was attempted (optional)
 * @param context Additional context information about the failure
 */
class SplitException(
  message: String,
  cause: Throwable = null,
  val mimeType: String = "",
  val strategy: Option[String] = None,
  val context: Map[String, String] = Map.empty
) extends RuntimeException(message, cause) with Serializable {
  
  /**
   * Add additional context to this exception.
   * 
   * @param key The context key
   * @param value The context value
   * @return A new SplitException with the added context
   */
  def withContext(key: String, value: String): SplitException =
    new SplitException(message, cause, mimeType, strategy, context + (key -> value))
    
  /**
   * Add multiple context entries to this exception.
   * 
   * @param newContext Map of context key-value pairs
   * @return A new SplitException with the added context
   */
  def withContext(newContext: Map[String, String]): SplitException =
    new SplitException(message, cause, mimeType, strategy, context ++ newContext)
    
  /**
   * Override getMessage to include rich context information.
   * This ensures that logs and error reports contain all relevant details.
   */
  override def getMessage: String = {
    val contextStr = if (context.isEmpty) "" else s" [${context.map { case (k, v) => s"$k=$v" }.mkString(", ")}]"
    val strategyStr = strategy.map(s => s", strategy: $s").getOrElse("")
    s"$message (mime: $mimeType$strategyStr)$contextStr"
  }
}

/**
 * Thrown when the requested splitting strategy doesn't match the document type.
 * For example, requesting page splitting on an Excel file.
 * 
 * @param mimeType The MIME type of the document
 * @param requestedStrategy The strategy that was requested
 */
class InvalidStrategyException(
  mimeType: String,
  requestedStrategy: String
) extends SplitException(
  s"Strategy '$requestedStrategy' is not supported for MIME type '$mimeType'",
  mimeType = mimeType,
  strategy = Some(requestedStrategy)
) {
  /**
   * Add suggestions for valid strategies if known.
   * 
   * @param validStrategies List of strategies that would work for this MIME type
   * @return A new exception with suggestions in the context
   */
  def withSuggestions(validStrategies: Seq[String]): InvalidStrategyException = {
    new InvalidStrategyException(mimeType, requestedStrategy)
      .withContext("suggestions", validStrategies.mkString(", "))
      .asInstanceOf[InvalidStrategyException]
  }
}

/**
 * Thrown when a document appears to be corrupted or malformed.
 * This helps distinguish between configuration errors and actual document problems.
 * 
 * @param mimeType The MIME type of the document
 * @param details Specific details about the corruption
 * @param cause The underlying cause (often from the parsing library)
 */
class CorruptedDocumentException(
  mimeType: String,
  details: String,
  cause: Throwable = null
) extends SplitException(
  s"Document appears to be corrupted: $details",
  cause,
  mimeType = mimeType
)

/**
 * Thrown when a document is empty or has no content to split.
 * Common for emails with no body/attachments or blank PDFs.
 * 
 * @param mimeType The MIME type of the document
 * @param details Additional details about what was expected
 */
class EmptyDocumentException(
  mimeType: String,
  details: String = "Document contains no splittable content"
) extends SplitException(
  s"Empty document: $details",
  mimeType = mimeType
)

/**
 * Thrown when splitting times out (for UDF-wrapped operations).
 * 
 * @param mimeType The MIME type of the document
 * @param timeoutMs The timeout duration in milliseconds
 * @param details Additional details about the operation
 */
class SplitTimeoutException(
  mimeType: String,
  timeoutMs: Long,
  details: String = ""
) extends SplitException(
  s"Splitting operation timed out after ${timeoutMs}ms${if (details.nonEmpty) s": $details" else ""}",
  mimeType = mimeType
) {
  override val context: Map[String, String] = Map("timeout_ms" -> timeoutMs.toString)
}

/**
 * Thrown when document exceeds configured size limits.
 * Helps prevent memory issues and zipbomb attacks.
 * 
 * @param mimeType The MIME type of the document
 * @param actualSize The actual size that exceeded limits
 * @param maxSize The configured maximum size
 * @param sizeType What was measured (e.g., "bytes", "chunks", "files")
 */
class SizeLimitExceededException(
  mimeType: String,
  actualSize: Long,
  maxSize: Long,
  sizeType: String = "bytes"
) extends SplitException(
  s"Document exceeds size limit: $actualSize $sizeType (max: $maxSize)",
  mimeType = mimeType
) {
  override val context: Map[String, String] = Map(
    "actual_size" -> actualSize.toString,
    "max_size" -> maxSize.toString,
    "size_type" -> sizeType
  )
}