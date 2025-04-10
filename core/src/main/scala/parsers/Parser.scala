package com.tjclp.xlcr.parsers

import com.tjclp.xlcr.models.FileContent
import com.tjclp.xlcr.models.Model
import com.tjclp.xlcr.types.MimeType
import com.tjclp.xlcr.ParserError

/**
 * A Parser transforms an input file content of type I (MimeType) into a model M.
 *
 * @tparam I input MimeType
 * @tparam M output model type
 */
trait Parser[I <: MimeType, M <: Model] {
  /**
   * Parse input file content into model
   *
   * @param input The file content to parse
   * @return The parsed model
   * @throws ParserError if parsing fails
   */
  @throws[ParserError]
  def parse(input: FileContent[I]): M
}
