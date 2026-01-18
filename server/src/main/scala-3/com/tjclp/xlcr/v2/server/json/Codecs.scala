package com.tjclp.xlcr.v2.server.json

import zio.json._

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
  availableConversions: List[String]
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
 * Health check response.
 *
 * @param status
 *   Health status ("healthy" or "unhealthy")
 * @param version
 *   Server version
 */
final case class HealthResponse(
  status: String,
  version: String = "2.0.0"
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
  export HealthResponse.given
