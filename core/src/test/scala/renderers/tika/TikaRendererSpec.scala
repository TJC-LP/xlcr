package com.tjclp.xlcr
package renderers.tika

import models.tika.TikaModel
import renderers.tika.TikaRenderer
import types.MimeType
import base.RendererSpec

class TikaRendererSpec extends RendererSpec {

  "TikaRenderer" should "render TikaModel to bytes" in {
    val renderer = new TikaRenderer[MimeType.TextPlain.type] {
      val mimeType = MimeType.TextPlain
    }
    val model = TikaModel[MimeType.TextPlain.type]("Some text", Map.empty)
    val result = renderer.render(model)
    result.mimeType shouldBe MimeType.TextPlain
    new String(result.data) shouldBe "Some text"
  }
}