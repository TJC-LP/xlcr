package com.tjclp.xlcr
package renderers.powerpoint

import java.nio.charset.StandardCharsets

import io.circe.syntax._

import models.FileContent
import models.powerpoint.SlidesData
import renderers.SimpleRenderer
import types.MimeType.ApplicationJson

/**
 * Renders SlidesData to a JSON string using circe.
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
