package com.tjclp

/**
 * This package object holds custom exceptions common to the XLCR application.
 */
package object xlcr {

  /**
   * Thrown when an input file is not found (e.g., missing or invalid path).
   */
  case class InputFileNotFoundException(path: String)
    extends RuntimeException(s"Input file '$path' does not exist")

  /**
   * Thrown when a matching parser cannot be found for the given input and output MIME types.
   */
  case class ParserNotFoundException(inputType: String, outputType: String)
    extends RuntimeException(s"No parser found for input type $inputType and output type $outputType")

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
  case class UnsupportedMimeTypeException(path: java.nio.file.Path, mimeType: String)
    extends RuntimeException(s"Unsupported MIME type '$mimeType' for file: $path")

  /**
   * Thrown when an unknown extension makes it impossible to determine a file's MIME type.
   */
  case class UnknownExtensionException(path: java.nio.file.Path, extension: String)
    extends RuntimeException(s"Cannot determine MIME type for extension '$extension' in file: $path")
}