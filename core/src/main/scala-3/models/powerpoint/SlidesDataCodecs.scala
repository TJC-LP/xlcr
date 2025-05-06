package com.tjclp.xlcr
package models.powerpoint

import io.circe._
import io.circe.generic.semiauto._

trait SlidesDataCodecs {
  given slidesDataEncoder: Encoder[SlidesData] = deriveEncoder[SlidesData]
  given slidesDataDecoder: Decoder[SlidesData] = deriveDecoder[SlidesData]
}

object SlidesDataCodecs extends SlidesDataCodecs
