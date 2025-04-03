// TikaXmlParser.scala
package com.tjclp.xlcr
package parsers.tika

import types.MimeType
import types.MimeType.ApplicationXml

import org.xml.sax.ContentHandler

/**
 * A TikaParser that produces TikaModel[ApplicationXml.type].
 * Usage:
 * val parser = new TikaXmlParser()
 * val result: TikaModel[ApplicationXml.type] = parser.parse(fileContent)
 */
class TikaXmlParser extends TikaParser[MimeType, ApplicationXml.type] {

  /** We use an XML content handler for XML extraction */
  protected val contentHandler: ContentHandler = TikaContentHandler.xml()
}
