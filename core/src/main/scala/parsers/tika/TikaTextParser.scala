// TikaTextParser.scala
package com.tjclp.xlcr
package parsers.tika

import org.xml.sax.ContentHandler

import types.MimeType
import types.MimeType.TextPlain

/**
 * A TikaParser that produces TikaModel[TextPlain.type]. Usage: val parser = new TikaTextParser()
 * val result: TikaModel[TextPlain.type] = parser.parse(fileContent)
 */
class TikaTextParser extends TikaParser[MimeType, TextPlain.type] {

  /** We use a text content handler for textual extraction */
  protected val contentHandler: ContentHandler = TikaContentHandler.text()
}
