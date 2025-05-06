package com.tjclp.xlcr
package parsers.powerpoint

import java.nio.charset.StandardCharsets

import io.circe.parser.decode

import models.FileContent
import models.powerpoint.SlidesData
import parsers.SimpleParser
import types.MimeType.ApplicationJson

/**
 * Parses a JSON string into SlidesData using circe.
 */
class SlidesDataJsonParser
    extends SimpleParser[ApplicationJson.type, SlidesData] {
  override def parse(input: FileContent[ApplicationJson.type]): SlidesData = {
    val jsonStr = new String(input.data, StandardCharsets.UTF_8)
    decode[SlidesData](jsonStr) match {
      case Right(slidesData) => slidesData
      case Left(err) =>
        throw ParserError(
          s"Failed to parse SlidesData from JSON: ${err.getMessage}",
          Some(err)
        )
    }
  }
}
