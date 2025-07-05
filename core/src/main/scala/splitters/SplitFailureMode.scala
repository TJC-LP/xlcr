package com.tjclp.xlcr
package splitters

/**
 * Defines how document splitters should handle failures during the splitting process. This allows
 * users to configure behavior based on their specific use case requirements.
 */
sealed trait SplitFailureMode {

  /** A short name for this mode, used in configuration and logging */
  def name: String
}

object SplitFailureMode {

  /**
   * Preserve document as single chunk when splitting fails (current default behavior). This
   * maintains backward compatibility and ensures documents are never lost. Best for: General
   * purpose pipelines where data preservation is critical.
   */
  case object PreserveAsChunk extends SplitFailureMode {
    val name = "preserve"
  }

  /**
   * Throw an exception immediately when splitting fails. This provides fail-fast behavior for data
   * quality validation. Best for: Data validation pipelines, testing, and scenarios where incorrect
   * splitting indicates a critical problem.
   */
  case object ThrowException extends SplitFailureMode {
    val name = "throw"
  }

  /**
   * Drop documents that cannot be split, returning an empty sequence. This can improve performance
   * by skipping problematic documents. Best for: High-throughput pipelines where occasional
   * document loss is acceptable and performance is critical.
   */
  case object DropDocument extends SplitFailureMode {
    val name = "drop"
  }

  /**
   * Tag failed documents with error metadata and preserve them. This enables monitoring and
   * debugging while maintaining data flow. Best for: Production pipelines that need observability,
   * allowing downstream filtering and error analysis.
   */
  case object TagAndPreserve extends SplitFailureMode {
    val name = "tag"
  }

  /**
   * Parse a failure mode from a string configuration value. Case-insensitive to support various
   * configuration formats.
   *
   * @param s
   *   The string to parse
   * @return
   *   Some(mode) if valid, None if unrecognized
   */
  def fromString(s: String): Option[SplitFailureMode] = s.toLowerCase.trim match {
    case "preserve" => Some(PreserveAsChunk)
    case "throw"    => Some(ThrowException)
    case "drop"     => Some(DropDocument)
    case "tag"      => Some(TagAndPreserve)
    case _          => None
  }

  /**
   * Get all available failure modes. Useful for configuration validation and documentation.
   */
  def values: Set[SplitFailureMode] = Set(
    PreserveAsChunk,
    ThrowException,
    DropDocument,
    TagAndPreserve
  )

  /**
   * Get a comma-separated list of valid mode names. Useful for error messages and documentation.
   */
  def validNames: String = values.map(_.name).mkString(", ")
}
