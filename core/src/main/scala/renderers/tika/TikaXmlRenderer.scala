package com.tjclp.xlcr
package renderers.tika

import types.MimeType.ApplicationXml

/**
 * Render a `TikaModel[ApplicationXml.type]` back to raw `application/xml` bytes.
 *
 * – Uses the default implementation in TikaRenderer which serialises `model.text` into UTF-8 bytes.
 */
class TikaXmlRenderer extends TikaRenderer[ApplicationXml.type] {
  override val mimeType: ApplicationXml.type = ApplicationXml

  implicit val mimeTag: scala.reflect.ClassTag[ApplicationXml.type] =
    scala.reflect.ClassTag(ApplicationXml.getClass)
}
