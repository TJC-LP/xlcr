package com.tjclp

/**
 * This package object holds custom exceptions common to the XLCR application.
 */
package object xlcr {

  /**
   * Defines Tika-specific error types.
   */
  sealed trait TikaError extends BridgeError

  /**
   * Sealed trait for all bridging errors.
   */
  sealed trait BridgeError extends Exception {
    def message: String

    def causeOpt: Option[Throwable]

    override def getMessage: String = message

    // Scala 2.12 doesn't have union types, use plain Throwable
    override def getCause: Throwable = causeOpt.orNull
  }

  case class TikaParseError(message: String, causeOpt: Option[Throwable] = None)
      extends TikaError

  case class TikaRenderError(
    message: String,
    causeOpt: Option[Throwable] = None
  ) extends TikaError

  case class UnsupportedTikaFormatError(
    message: String,
    causeOpt: Option[Throwable] = None
  ) extends TikaError

  case class ParserError(message: String, causeOpt: Option[Throwable] = None)
      extends BridgeError

  case class RendererError(message: String, causeOpt: Option[Throwable] = None)
      extends BridgeError

  case class UnsupportedConversionError(
    message: String,
    causeOpt: Option[Throwable] = None
  ) extends BridgeError

  /**
   * Thrown when an input file is not found (e.g., missing or invalid path).
   */
  case class InputFileNotFoundException(path: String)
      extends RuntimeException(s"Input file '$path' does not exist")

  /**
   * Thrown when a matching parser cannot be found for the given input and output MIME types.
   */
  case class ParserNotFoundException(inputType: String, outputType: String)
      extends RuntimeException(
        s"No parser found for input type $inputType and output type $outputType"
      )

  /**
   * Thrown when content extraction fails internally.
   */
  case class ContentExtractionException(message: String, cause: Throwable)
      extends RuntimeException(message, cause)

  /**
   * Thrown when writing processed data to the output file fails.
   */
  case class OutputWriteException(message: String, cause: Throwable)
      extends RuntimeException(message, cause)

  /**
   * Thrown when a MIME type is unsupported within the system.
   */
  case class UnsupportedMimeTypeException(
    path: java.nio.file.Path,
    mimeType: String
  ) extends RuntimeException(
        s"Unsupported MIME type '$mimeType' for file: $path"
      )

  /**
   * Thrown when an unknown extension makes it impossible to determine a file's MIME type.
   */
  case class UnknownExtensionException(
    path: java.nio.file.Path,
    extension: String
  ) extends RuntimeException(
        s"Cannot determine MIME type for extension '$extension' in file: $path"
      )

  case class UnsupportedConversionException(
    inputMimeType: String,
    outputMimeType: String
  ) extends RuntimeException(
        s"No bridge found to convert from $inputMimeType to $outputMimeType"
      )

  class ImageSizeException(
    message: String,
    val actualSizeBytes: Long,
    val maxSizeBytes: Long,
    val attempts: Int,
    cause: Throwable = null
  ) extends RuntimeException(message, cause) {

    /**
     * Returns the ratio of actual size to maximum allowed size.
     *
     * @return
     *   A ratio greater than 1.0 indicates by how much the image exceeds the limit
     */
    def overSizeRatio: Double = actualSizeBytes.toDouble / maxSizeBytes

    /**
     * Returns a string describing the size difference in human-readable format.
     *
     * @return
     *   A string like "5.2 MB (over limit by 4.2 MB or 520%)"
     */
    def sizeReport: String = {
      val actualMB   = actualSizeBytes.toDouble / (1024 * 1024)
      val maxMB      = maxSizeBytes.toDouble / (1024 * 1024)
      val overMB     = actualMB - maxMB
      val percentage = (overSizeRatio - 1.0) * 100

      f"${actualMB}%.2f MB (over limit by ${overMB}%.2f MB or ${percentage}%.0f%%)"
    }

    /**
     * Creates a quarantine-friendly message with detailed information for diagnosis.
     *
     * @return
     *   A detailed report suitable for logging in a quarantine system
     */
    def quarantineReport: String =
      s"""Image Size Exception:
         |Message: $getMessage
         |Actual Size: $actualSizeBytes bytes ($sizeReport)
         |Maximum Size: $maxSizeBytes bytes (${maxSizeBytes.toDouble / (1024 * 1024)}%.2f MB)
         |Attempts: $attempts
         |Oversized Ratio: ${overSizeRatio}%.2f
         |""".stripMargin
  }
}
