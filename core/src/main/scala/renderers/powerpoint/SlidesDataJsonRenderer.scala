package com.tjclp.xlcr
package renderers.powerpoint

import models.FileContent
import models.powerpoint.SlidesData
import renderers.{Renderer, SimpleRenderer}
import types.MimeType
import types.MimeType.ApplicationJson

import io.circe.syntax._

import java.nio.charset.StandardCharsets

/** Renders SlidesData to a JSON string using circe.
  */
class SlidesDataJsonRenderer
    extends SimpleRenderer[SlidesData, ApplicationJson.type] {
  override def render(model: SlidesData): FileContent[ApplicationJson.type] = {
    val jsonString = model.asJson.spaces2
    FileContent[ApplicationJson.type](
      jsonString.getBytes(StandardCharsets.UTF_8),
      ApplicationJson
    )
  }
}
