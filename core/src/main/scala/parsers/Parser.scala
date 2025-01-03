package com.tjclp.xlcr
package parsers

import models.Content
import types.MimeType

import java.nio.file.Path
import scala.util.Try

/**
 * Base trait for all content parsers in the XLCR system.
 * Defines the core contract that all parsers must implement.
 */
trait Parser:
  /**
   * Extract content from the input file and return it in the desired format
   *
   * @param input The path to the input file
   * @param output Optional output path for diffMode analysis
   * @return A Try[Content] containing the extracted content or a failure
   */
  def extractContent(input: Path, output: Option[Path] = None): Try[Content]
  /**
   * Get the primary MIME type this parser produces
   *
   * @return The MimeType that this parser outputs
   */
  def outputType: MimeType
  /**
   * Get all supported input MIME types for this parser
   *
   * @return Set of supported input MimeTypes
   */
  def supportedInputTypes: Set[MimeType]
  /**
   * Get the priority level for this parser
   * Higher numbers indicate higher priority
   *
   * @return The priority level (default is 0)
   */
  def priority: Int = 0
  /**
   * Whether this parser supports diffing operations between an input and existing output
   *
   * @return Boolean indicating if diff mode is supported
   */
  def supportsDiffMode: Boolean = false