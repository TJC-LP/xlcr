package com.tjclp.xlcr
package parsers

import models.{FileContent, Model}
import types.MimeType

/** A simple parser implementation that provides backward compatibility with
  * existing parsers by delegating to a configuration-less parse method.
  *
  * This trait allows existing parsers to be easily adapted to support the
  * new Parser interface with minimal changes.
  *
  * @tparam I Input MimeType
  * @tparam M Output model type
  */
trait SimpleParser[I <: MimeType, M <: Model] extends Parser[I, M] {

  /** Parse implementation that ignores the configuration.
    * Subclasses should implement this method instead of overriding parse.
    *
    * @param input The file content to parse
    * @return The parsed model
    * @throws ParserError if parsing fails
    */
  @throws[ParserError]
  def parse(input: FileContent[I]): M

  /** Implements the Parser.parse method by delegating to parseSimple.
    * This provides compatibility with the new configuration-aware interface.
    *
    * @param input The file content to parse
    * @param config Optional parser-specific configuration (ignored in SimpleParser)
    * @return The parsed model
    * @throws ParserError if parsing fails
    */
  override final def parse(
      input: FileContent[I],
      config: Option[ParserConfig] = None
  ): M = {
    parse(input)
  }
}

/** Companion object providing factory methods for SimpleParser. */
object SimpleParser {

  /** Creates a SimpleParser implementation from a function.
    *
    * @param parseFn The function that implements the parsing logic
    * @tparam I The input MimeType
    * @tparam M The output model type
    * @return A SimpleParser implementation
    */
  def apply[I <: MimeType, M <: Model](
      parseFn: FileContent[I] => M
  ): SimpleParser[I, M] = { (input: FileContent[I]) =>
    parseFn(input)
  }
}
