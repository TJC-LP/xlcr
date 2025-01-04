package com.tjclp.xlcr
package bridges.tika

import models.tika.TikaModel
import parsers.tika.TikaTextParser
import renderers.tika.TikaTextRenderer
import types.MimeType
import types.MimeType.TextPlain

/**
 * Converts any supported input to plain text using Tika.
 */
object TikaPlainTextBridge extends TikaBridge[TextPlain.type] {
  override protected def parser = new TikaTextParser()

  override protected def renderer = new TikaTextRenderer()
}