package com.tjclp.xlcr
package bridges

import models.FileContent
import parsers.{ Parser, ParserConfig }
import types.MimeType

/**
 * Scala 2 version of SimpleBridge. Extends Bridge with the model type being the input FileContent.
 */
trait SimpleBridge[I <: MimeType, O <: MimeType]
    extends Bridge[FileContent[I], I, O]
    with BaseSimpleBridge[I, O] {
  // Default implementation for the input parser - just pass through the PDF
  override def inputParser: Parser[I, FileContent[I]] =
    (input: FileContent[I], _: Option[ParserConfig]) => input
}
