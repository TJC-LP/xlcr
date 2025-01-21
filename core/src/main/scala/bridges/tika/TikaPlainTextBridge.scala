package com.tjclp.xlcr
package bridges.tika

import models.FileContent
import models.tika.TikaModel
import types.MimeType
import types.MimeType.TextPlain

import org.apache.tika.sax.BodyContentHandler

/**
 * Converts any supported input to plain text using Tika.
 */
object TikaPlainTextBridge extends TikaBridgeTrait[MimeType, TextPlain.type] {
  override def parseInput(input: FileContent[MimeType]): TikaModel[TextPlain.type] =
    parseTika(input.data, new BodyContentHandler())

  override def render(model: TikaModel[TextPlain.type]): FileContent[TextPlain.type] = {
    val bytes = model.text.getBytes("UTF-8")
    FileContent(bytes, TextPlain)
  }
}