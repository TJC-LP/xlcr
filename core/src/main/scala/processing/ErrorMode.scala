package com.tjclp.xlcr
package processing

/**
 * Error handling strategy for batch directory processing operations.
 */
sealed trait ErrorMode

object ErrorMode {

  /**
   * Stop processing immediately on the first error encountered. Any in-progress tasks will be
   * allowed to complete, but no new tasks will be started.
   */
  case object FailFast extends ErrorMode

  /**
   * Continue processing all files even when errors occur. Failed files are logged but don't stop
   * the batch. A summary of all errors is provided at the end.
   */
  case object ContinueOnError extends ErrorMode

  /**
   * Skip files that encounter errors and continue processing. Similar to ContinueOnError but failed
   * files are not counted in final statistics and are treated as if they were filtered out.
   */
  case object SkipOnError extends ErrorMode

  /**
   * Parse error mode from string, returning None for invalid values.
   */
  def fromString(s: String): Option[ErrorMode] =
    s.toLowerCase match {
      case "fail-fast" | "failfast" => Some(FailFast)
      case "continue" | "continue-on-error" =>
        Some(ContinueOnError)
      case "skip" | "skip-on-error" => Some(SkipOnError)
      case _                        => None
    }

  /**
   * Default error mode for directory processing.
   */
  val default: ErrorMode = ContinueOnError
}
