package com.tjclp.xlcr
package bridges.tika

import models.FileContent
import models.tika.TikaModel
import types.MimeType
import types.MimeType.ApplicationXml

import com.tjclp.xlcr
import org.apache.tika.sax.{ToXMLContentHandler, WriteOutContentHandler}

/**
 * Converts any supported input to XML using Tika.
 * Falls back to plain text wrapped in XML if XML parsing fails.
 */
object TikaXmlBridge extends TikaBridgeTrait[MimeType, ApplicationXml.type] {
  override def parse(fileContent: FileContent[MimeType]): TikaModel[xlcr.types.MimeType.ApplicationXml.type] =
    parseTika(fileContent.data, new WriteOutContentHandler(new ToXMLContentHandler(), -1))

  override def render(model: TikaModel[ApplicationXml.type]): FileContent[ApplicationXml.type] = {
    val bytes = model.text.getBytes("UTF-8")
    FileContent(bytes, ApplicationXml)
  }
}