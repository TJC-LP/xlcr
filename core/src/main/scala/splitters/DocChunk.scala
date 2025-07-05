package com.tjclp.xlcr
package splitters

import models.FileContent
import types.MimeType

/**
 * Metadata‑enriched chunk produced by a DocumentSplitter. Includes support for failure tracking and
 * custom metadata.
 */
case class DocChunk[T <: MimeType](
  content: FileContent[T],
  label: String, // human readable (e.g. Sheet name, "Page 3")
  index: Int,    // 0‑based position within parent
  total: Int,    // total number of chunks produced
  attrs: Map[String, String] = Map.empty
) extends Serializable {

  /**
   * Alias for attrs to provide a more intuitive name. New code should use metadata instead of
   * attrs.
   */
  def metadata: Map[String, String] = attrs

  /**
   * Check if this chunk represents a splitting failure. A chunk is considered failed if it has a
   * "split_status" of "failed".
   *
   * @return
   *   true if this chunk represents a failure
   */
  def isFailed: Boolean = metadata.get("split_status").contains("failed")

  /**
   * Get the error message if this is a failed chunk.
   *
   * @return
   *   Some(error message) if this is a failed chunk, None otherwise
   */
  def errorMessage: Option[String] = metadata.get("error_message")

  /**
   * Get the error type if this is a failed chunk.
   *
   * @return
   *   Some(error type) if this is a failed chunk, None otherwise
   */
  def errorType: Option[String] = metadata.get("error_type")

  /**
   * Add metadata to this chunk.
   *
   * @param key
   *   The metadata key
   * @param value
   *   The metadata value
   * @return
   *   A new DocChunk with the added metadata
   */
  def withMetadata(key: String, value: String): DocChunk[T] =
    copy(attrs = attrs + (key -> value))

  /**
   * Add multiple metadata entries to this chunk.
   *
   * @param metadata
   *   Map of metadata key-value pairs
   * @return
   *   A new DocChunk with the added metadata
   */
  def withMetadata(metadata: Map[String, String]): DocChunk[T] =
    copy(attrs = attrs ++ metadata)

  /**
   * Mark this chunk as a failure with error details. Sets standard metadata fields for error
   * tracking.
   *
   * @param error
   *   The error message
   * @param errorType
   *   The type of error (e.g., exception class name)
   * @return
   *   A new DocChunk marked as failed
   */
  def asFailure(error: String, errorType: String = "Unknown"): DocChunk[T] =
    copy(attrs =
      attrs ++ Map(
        "split_status"  -> "failed",
        "error_message" -> error,
        "error_type"    -> errorType,
        "failed_at"     -> java.time.Instant.now().toString
      )
    )

  /**
   * Mark this chunk as successfully split. Useful when using TagAndPreserve mode to explicitly mark
   * successes.
   *
   * @return
   *   A new DocChunk marked as successful
   */
  def asSuccess: DocChunk[T] =
    copy(attrs = attrs + ("split_status" -> "success"))
}
