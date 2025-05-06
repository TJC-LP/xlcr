package com.tjclp.xlcr
package renderers.tika

import types.MimeType.TextPlain

/**
 * Renders a TikaModel[TextPlain.type] back to text/plain.
 */
class TikaTextRenderer extends TikaRenderer[TextPlain.type] {
  override val mimeType: TextPlain.type = TextPlain
  implicit val mimeTag: scala.reflect.ClassTag[TextPlain.type] =
    scala.reflect.ClassTag(TextPlain.getClass)
}
