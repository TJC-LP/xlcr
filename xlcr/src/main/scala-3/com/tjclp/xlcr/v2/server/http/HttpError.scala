package com.tjclp.xlcr.v2.server.http

import zio.http.Status

import com.tjclp.xlcr.v2.transform._

/**
 * HTTP error representation for API responses.
 *
 * @param status
 *   HTTP status code
 * @param message
 *   Error message for the client
 * @param details
 *   Optional additional details
 */
final case class HttpError(
  status: Status,
  message: String,
  details: Option[String] = None
)

object HttpError:

  /**
   * Convert a TransformError to an HttpError.
   *
   * Maps domain errors to appropriate HTTP status codes:
   *   - UnsupportedConversion -> 415 Unsupported Media Type
   *   - ParseError -> 400 Bad Request
   *   - ValidationError -> 422 Unprocessable Entity
   *   - ResourceError -> 503 Service Unavailable
   *   - TimeoutError -> 504 Gateway Timeout
   *   - CancellationError -> 499 Client Closed Request
   *   - SplitError -> 422 Unprocessable Entity
   *   - CompositeError -> 500 Internal Server Error (with details)
   *   - GeneralError -> 500 Internal Server Error
   */
  def fromTransformError(err: TransformError): HttpError = err match
    case UnsupportedConversion(from, to) =>
      HttpError(
        Status.UnsupportedMediaType,
        s"Cannot convert ${from.value} to ${to.value}",
        Some(s"No conversion path available from source format to target format")
      )

    case ParseError(msg, cause) =>
      HttpError(
        Status.BadRequest,
        s"Invalid input: $msg",
        cause.map(_.getMessage)
      )

    case ValidationError(msg, cause) =>
      HttpError(
        Status.UnprocessableEntity,
        msg,
        cause.map(_.getMessage)
      )

    case ResourceError(msg, resourceType, cause) =>
      HttpError(
        Status.ServiceUnavailable,
        msg,
        Some(s"Missing resource type: $resourceType")
      )

    case TimeoutError(msg, durationMs) =>
      HttpError(
        Status.GatewayTimeout,
        msg,
        Some(s"Operation timed out after ${durationMs}ms")
      )

    case CancellationError(msg) =>
      HttpError(
        Status.Custom(499), // Client Closed Request
        msg,
        None
      )

    case SplitError(msg, inputMime, cause) =>
      HttpError(
        Status.UnprocessableEntity,
        s"Failed to split ${inputMime.value}: $msg",
        cause.map(_.getMessage)
      )

    case CompositeError(errors) =>
      HttpError(
        Status.InternalServerError,
        s"${errors.size} errors occurred",
        Some(errors.map(_.message).mkString("; "))
      )

    case RenderError(msg, cause) =>
      HttpError(
        Status.InternalServerError,
        s"Output generation failed: $msg",
        cause.map(_.getMessage)
      )

    case GeneralError(msg, cause) =>
      HttpError(
        Status.InternalServerError,
        msg,
        cause.map(_.getMessage)
      )

  /** Create a bad request error */
  def badRequest(message: String): HttpError =
    HttpError(Status.BadRequest, message)

  /** Create a not found error */
  def notFound(message: String): HttpError =
    HttpError(Status.NotFound, message)

  /** Create an internal server error */
  def internalError(message: String): HttpError =
    HttpError(Status.InternalServerError, message)

  /** Create an unsupported media type error */
  def unsupportedMediaType(message: String): HttpError =
    HttpError(Status.UnsupportedMediaType, message)
