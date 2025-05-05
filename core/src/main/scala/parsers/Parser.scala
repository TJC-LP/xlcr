package com.tjclp.xlcr
package parsers

import models.{FileContent, Model}
import types.MimeType

/** A Parser transforms an input file content of type I (MimeType) into a model M.
  *
  * @tparam I input MimeType
  * @tparam M output model type
  */
trait Parser[I <: MimeType, M <: Model] {

  /** Parse input file content into model
    *
    * @param input The file content to parse
    * @param config Optional parser-specific configuration
    * @return The parsed model
    * @throws ParserError if parsing fails
    */
  @throws[ParserError]
  def parse(input: FileContent[I], config: Option[ParserConfig] = None): M
}
