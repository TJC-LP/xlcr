package com.tjclp.xlcr
package bridges.tika

import bridges.Bridge
import models.FileContent
import models.tika.Text
import types.MimeType
import types.MimeType.TextPlain

import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.{AutoDetectParser, ParseContext}
import org.apache.tika.sax.BodyContentHandler

import java.io.ByteArrayInputStream
import scala.util.Using

/**
 * TikaBridge demonstrates bridging from e.g. PDF to text/plain using Tika.
 * Could be extended for other input mime types if needed.
 */
trait TikaBridge[O <: MimeType.ApplicationXml.type | MimeType.TextPlain.type] extends Bridge[Text, _, O]

object TikaBridge extends Bridge[
  /* We can define a model or placeholder here. We'll define a trivial "TextModel". */
  Text,
  MimeType,
  MimeType.TextPlain.type
] {

  override def parse(input: FileContent[_]): Text = {
    val parser = new AutoDetectParser()
    val metadata = new Metadata()
    val handler = new BodyContentHandler()
    val context = new ParseContext()

    Using.resource(new ByteArrayInputStream(input.data)) { bais =>
      parser.parse(bais, handler, metadata, context)
    }
    Text(handler.toString)
  }

  override def render(model: Text): FileContent[MimeType.TextPlain.type] = {
    FileContent(model.text.getBytes("UTF-8"), TextPlain)
  }
}
