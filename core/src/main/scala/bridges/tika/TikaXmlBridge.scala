package com.tjclp.xlcr
package bridges.tika

import parsers.tika.TikaXmlParser
import renderers.tika.TikaXmlRenderer
import types.MimeType.ApplicationXml

/**
 * Converts any supported input to XML using Tika. Falls back to plain text wrapped in XML if XML
 * parsing fails.
 *
 * This bridge can handle any input mime type and is registered both for specific mime types and as
 * a wildcard bridge to serve as a fallback when no specific bridge is found.
 */
object TikaXmlBridge extends TikaBridge[ApplicationXml.type] {

  override protected def parser = new TikaXmlParser()

  override protected def renderer = new TikaXmlRenderer()
}
