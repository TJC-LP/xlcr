package com.tjclp.xlcr
package bridges.tika

import parsers.tika.TikaTextParser
import renderers.tika.TikaTextRenderer
import types.MimeType.TextPlain

/**
 * Converts any supported input to plain text using Tika.
 *
 * This bridge can handle any input mime type and is registered both for specific mime types and as
 * a wildcard bridge to serve as a fallback when no specific bridge is found.
 */
object TikaPlainTextBridge extends TikaBridge[TextPlain.type] {

  override protected def parser = new TikaTextParser()

  override protected def renderer = new TikaTextRenderer()
}
