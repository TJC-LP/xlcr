package com.tjclp.xlcr.server.json

import zio.json.*

/**
 * JSON response models and codecs for the XLCR API.
 */

/**
 * Error response body.
 *
 * @param error
 *   Error message
 * @param details
 *   Optional additional details
 * @param status
 *   HTTP status code
 */
final case class ErrorResponse(
  error: String,
  details: Option[String],
  status: Int
)

object ErrorResponse:
  given JsonEncoder[ErrorResponse] = DeriveJsonEncoder.gen[ErrorResponse]
  given JsonDecoder[ErrorResponse] = DeriveJsonDecoder.gen[ErrorResponse]

/**
 * Document info response.
 *
 * @param mimeType
 *   Detected MIME type
 * @param size
 *   Content size in bytes
 * @param canSplit
 *   Whether the document can be split
 * @param fragmentCount
 *   Estimated number of fragments (if splittable)
 * @param availableConversions
 *   List of MIME types this document can be converted to
 */
final case class InfoResponse(
  mimeType: String,
  size: Long,
  canSplit: Boolean,
  fragmentCount: Option[Int],
  availableConversions: List[String],
  metadata: Option[Map[String, String]] = None
)

object InfoResponse:
  given JsonEncoder[InfoResponse] = DeriveJsonEncoder.gen[InfoResponse]
  given JsonDecoder[InfoResponse] = DeriveJsonDecoder.gen[InfoResponse]

/**
 * Single conversion capability.
 *
 * @param from
 *   Source MIME type
 * @param to
 *   Target MIME type
 */
final case class ConversionCapability(
  from: String,
  to: String
)

object ConversionCapability:
  given JsonEncoder[ConversionCapability] = DeriveJsonEncoder.gen[ConversionCapability]
  given JsonDecoder[ConversionCapability] = DeriveJsonDecoder.gen[ConversionCapability]

/**
 * Splitter capability.
 *
 * @param mimeType
 *   MIME type that can be split
 * @param outputMimeType
 *   Output MIME type of fragments
 */
final case class SplitCapability(
  mimeType: String,
  outputMimeType: String
)

object SplitCapability:
  given JsonEncoder[SplitCapability] = DeriveJsonEncoder.gen[SplitCapability]
  given JsonDecoder[SplitCapability] = DeriveJsonDecoder.gen[SplitCapability]

/**
 * Full capabilities response.
 *
 * @param conversions
 *   Available conversion paths
 * @param splits
 *   Available split operations
 * @param supportedInputTypes
 *   List of all input MIME types
 * @param supportedOutputTypes
 *   List of all output MIME types
 */
final case class CapabilitiesResponse(
  conversions: List[ConversionCapability],
  splits: List[SplitCapability],
  supportedInputTypes: List[String],
  supportedOutputTypes: List[String]
)

object CapabilitiesResponse:
  given JsonEncoder[CapabilitiesResponse] = DeriveJsonEncoder.gen[CapabilitiesResponse]
  given JsonDecoder[CapabilitiesResponse] = DeriveJsonDecoder.gen[CapabilitiesResponse]

/**
 * LibreOffice backend status for health reporting.
 *
 * @param available
 *   Whether LibreOffice is installed and detectable
 * @param running
 *   Whether the OfficeManager is currently running
 * @param instances
 *   Number of configured LibreOffice processes
 * @param maxTasksPerProcess
 *   Conversions before automatic process restart
 * @param ready
 *   Optional runtime readiness probe result (present only when explicitly requested)
 */
final case class LibreOfficeStatus(
  available: Boolean,
  running: Boolean,
  instances: Int,
  maxTasksPerProcess: Int,
  ready: Option[Boolean] = None
)

object LibreOfficeStatus:
  given JsonEncoder[LibreOfficeStatus] = DeriveJsonEncoder.gen[LibreOfficeStatus]
  given JsonDecoder[LibreOfficeStatus] = DeriveJsonDecoder.gen[LibreOfficeStatus]

/**
 * Health check response.
 *
 * @param status
 *   Health status ("healthy" or "unhealthy")
 * @param version
 *   Server version
 * @param libreoffice
 *   LibreOffice backend status (if available)
 */
final case class HealthResponse(
  status: String,
  version: String = "2.0.0",
  libreoffice: Option[LibreOfficeStatus] = None
)

object HealthResponse:
  given JsonEncoder[HealthResponse] = DeriveJsonEncoder.gen[HealthResponse]
  given JsonDecoder[HealthResponse] = DeriveJsonDecoder.gen[HealthResponse]

/**
 * Convenience object for importing all codecs.
 */
object Codecs:
  export ErrorResponse.given
  export InfoResponse.given
  export ConversionCapability.given
  export SplitCapability.given
  export CapabilitiesResponse.given
  export LibreOfficeStatus.given
  export HealthResponse.given
