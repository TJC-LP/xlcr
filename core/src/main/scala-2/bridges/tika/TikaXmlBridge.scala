package com.tjclp.xlcr
package bridges.tika

import models.tika.TikaModel
import parsers.tika.TikaXmlParser
import renderers.tika.TikaXmlRenderer
import types.MimeType
import types.MimeType.ApplicationXml

import scala.reflect.ClassTag

/**
 * Converts any supported input to XML using Tika.
 * Falls back to plain text wrapped in XML if XML parsing fails.
 */
object TikaXmlBridge extends TikaBridge[ApplicationXml.type] {
  implicit val mTag: ClassTag[TikaModel[ApplicationXml.type]] = implicitly[ClassTag[TikaModel[ApplicationXml.type]]]
  implicit val iTag: ClassTag[MimeType] = implicitly[ClassTag[MimeType]]
  implicit val oTag: ClassTag[ApplicationXml.type] = implicitly[ClassTag[ApplicationXml.type]]
  
  override protected def parser = new TikaXmlParser()

  override protected def renderer = new TikaXmlRenderer()
}