package com.tjclp.xlcr
package bridges.tika

import parsers.tika.TikaXmlParser
import renderers.tika.TikaXmlRenderer
import types.MimeType.ApplicationXml

/** Converts any supported input to XML using Tika.
  * Falls back to plain text wrapped in XML if XML parsing fails.
  */
object TikaXmlBridge extends TikaBridge[ApplicationXml.type] {

  override protected def parser = new TikaXmlParser()

  override protected def renderer = new TikaXmlRenderer()
}
