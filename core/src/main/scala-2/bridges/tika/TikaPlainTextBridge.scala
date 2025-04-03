package com.tjclp.xlcr
package bridges.tika

import models.tika.TikaModel
import parsers.tika.TikaTextParser
import renderers.tika.TikaTextRenderer
import types.MimeType
import types.MimeType.TextPlain

import scala.reflect.ClassTag

/**
 * Converts any supported input to plain text using Tika.
 */
object TikaPlainTextBridge extends TikaBridge[TextPlain.type] {
  override implicit val mTag: ClassTag[TikaModel[TextPlain.type]] = implicitly[ClassTag[TikaModel[TextPlain.type]]]
  override implicit val iTag: ClassTag[MimeType] = implicitly[ClassTag[MimeType]]
  override implicit val oTag: ClassTag[TextPlain.type] = implicitly[ClassTag[TextPlain.type]]
  
  override protected def parser = new TikaTextParser()

  override protected def renderer = new TikaTextRenderer()
}