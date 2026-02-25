package com.tjclp.xlcr.transform

import com.tjclp.xlcr.types.Mime

/**
 * Base trait for all transform errors in the v2 algebra.
 *
 * Extends Exception for interoperability with existing code while providing a typed error hierarchy
 * for ZIO effect handling.
 */
sealed trait TransformError extends Exception:
  /** Human-readable error message */
  def message: String

  /** Optional underlying cause */
  def cause: Option[Throwable]

  override def getMessage: String = message

  override def getCause: Throwable | Null = cause.orNull

/**
 * Error during parsing/reading input content.
 *
 * @param message
 *   Description of what went wrong
 * @param cause
 *   Optional underlying exception
 */
final case class ParseError(
  message: String,
  cause: Option[Throwable] = None
) extends TransformError

object ParseError:
  def apply(message: String, cause: Throwable): ParseError =
    ParseError(message, Some(cause))

  def fromThrowable(t: Throwable): ParseError =
    ParseError(t.getMessage, Some(t))

/**
 * Error during rendering/writing output content.
 *
 * @param message
 *   Description of what went wrong
 * @param cause
 *   Optional underlying exception
 */
final case class RenderError(
  message: String,
  cause: Option[Throwable] = None
) extends TransformError

object RenderError:
  def apply(message: String, cause: Throwable): RenderError =
    RenderError(message, Some(cause))

  def fromThrowable(t: Throwable): RenderError =
    RenderError(t.getMessage, Some(t))

/**
 * Error when a transform encounters invalid input data.
 *
 * @param message
 *   Description of the validation failure
 * @param cause
 *   Optional underlying exception
 */
final case class ValidationError(
  message: String,
  cause: Option[Throwable] = None
) extends TransformError

object ValidationError:
  def apply(message: String, cause: Throwable): ValidationError =
    ValidationError(message, Some(cause))

/**
 * Error when no transform path exists between two MIME types.
 *
 * @param from
 *   Source MIME type
 * @param to
 *   Target MIME type
 */
final case class UnsupportedConversion(
  from: Mime,
  to: Mime
) extends TransformError:
  val message: String          = s"No conversion path from ${from.value} to ${to.value}"
  val cause: Option[Throwable] = None

/**
 * Error when a transform times out.
 *
 * @param message
 *   Description of the timeout
 * @param durationMs
 *   The duration in milliseconds before timeout
 */
final case class TimeoutError(
  message: String,
  durationMs: Long
) extends TransformError:
  val cause: Option[Throwable] = None

/**
 * Error when transform is cancelled.
 *
 * @param message
 *   Description of why it was cancelled
 */
final case class CancellationError(
  message: String
) extends TransformError:
  val cause: Option[Throwable] = None

/**
 * Error when a required resource is not available.
 *
 * @param message
 *   Description of the missing resource
 * @param resourceType
 *   Type of resource (e.g., "file", "license", "library")
 */
final case class ResourceError(
  message: String,
  resourceType: String,
  cause: Option[Throwable] = None
) extends TransformError

object ResourceError:
  def missingFile(path: String): ResourceError =
    ResourceError(s"File not found: $path", "file")

  def missingLibrary(name: String): ResourceError =
    ResourceError(s"Required library not available: $name", "library")

  def missingLicense(product: String): ResourceError =
    ResourceError(s"License not found for: $product", "license")

/**
 * Error during split operation.
 *
 * @param message
 *   Description of the split failure
 * @param inputMime
 *   The MIME type of the input that failed to split
 * @param cause
 *   Optional underlying exception
 */
final case class SplitError(
  message: String,
  inputMime: Mime,
  cause: Option[Throwable] = None
) extends TransformError

object SplitError:
  def apply(message: String, inputMime: Mime, cause: Throwable): SplitError =
    SplitError(message, inputMime, Some(cause))

/**
 * Wrapper for multiple errors (for batch operations).
 *
 * @param errors
 *   The list of errors that occurred
 */
final case class CompositeError(
  errors: List[TransformError]
) extends TransformError:
  val message: String =
    s"${errors.size} errors occurred: ${errors.map(_.message).mkString("; ")}"
  val cause: Option[Throwable] = errors.headOption

object CompositeError:
  def of(first: TransformError, rest: TransformError*): CompositeError =
    CompositeError(first :: rest.toList)

/**
 * General transform error for cases not covered by specific types.
 *
 * @param message
 *   Description of the error
 * @param cause
 *   Optional underlying exception
 */
final case class GeneralError(
  message: String,
  cause: Option[Throwable] = None
) extends TransformError

object GeneralError:
  def apply(message: String, cause: Throwable): GeneralError =
    GeneralError(message, Some(cause))

  def fromThrowable(t: Throwable): GeneralError =
    GeneralError(t.getMessage, Some(t))

/** Utility methods for working with TransformError */
object TransformError:
  /** Lift a Throwable to a TransformError */
  def fromThrowable(t: Throwable): TransformError = t match
    case te: TransformError => te
    case _                  => GeneralError.fromThrowable(t)

  /** Create a ParseError */
  def parse(message: String): ParseError = ParseError(message)

  /** Create a RenderError */
  def render(message: String): RenderError = RenderError(message)

  /** Create a ValidationError */
  def validation(message: String): ValidationError = ValidationError(message)

  /** Create an UnsupportedConversion error */
  def unsupported(from: Mime, to: Mime): UnsupportedConversion =
    UnsupportedConversion(from, to)
end TransformError
