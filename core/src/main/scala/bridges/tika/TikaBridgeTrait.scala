package com.tjclp.xlcr
package bridges.tika

import bridges.Bridge
import models.tika.TikaModel
import types.MimeType

import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.{AutoDetectParser, ParseContext}
import org.xml.sax.ContentHandler

import java.io.ByteArrayInputStream
import scala.util.Using

trait TikaBridgeTrait[I <: MimeType, O <: MimeType] extends Bridge[TikaModel[O], I, O] {
  protected def parseTika(
                           data: Array[Byte],
                           makeHandler: => ContentHandler
                         ): TikaModel[O] = {
    val parser = new AutoDetectParser()
    val metadata = new Metadata()
    val context = new ParseContext()
    val handler = makeHandler
    Using.resource(new ByteArrayInputStream(data)) { stream =>
      parser.parse(stream, handler, metadata, context)
      TikaModel[O](
        text = handler.toString,
        metadata = metadata.names().map(name => name -> metadata.get(name)).toMap
      )
    }
  }
}
