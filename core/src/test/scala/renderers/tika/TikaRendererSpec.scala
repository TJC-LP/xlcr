package com.tjclp.xlcr
package renderers.tika

import base.RendererSpec
import models.tika.TikaModel
import types.MimeType.TextPlain

import scala.reflect.ClassTag

class TikaRendererSpec extends RendererSpec {

  "TikaRenderer" should "render TikaModel to bytes" in {
    val renderer = new TikaRenderer[TextPlain.type] {
      val mimeType: TextPlain.type = TextPlain
      val mimeTag: ClassTag[TextPlain.type] =
        implicitly[scala.reflect.ClassTag[TextPlain.type]]
    }
    val model = TikaModel[TextPlain.type]("Some text", Map.empty)
    val result = renderer.render(model)
    result.mimeType shouldBe TextPlain
    new String(result.data) shouldBe "Some text"
  }
}
