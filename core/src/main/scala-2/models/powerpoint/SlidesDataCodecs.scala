package com.tjclp.xlcr
package models.powerpoint

import io.circe._
import io.circe.generic.semiauto._

trait SlidesDataCodecs {
  implicit val slidesDataEncoder: Encoder[SlidesData] = deriveEncoder[SlidesData]
  implicit val slidesDataDecoder: Decoder[SlidesData] = deriveDecoder[SlidesData]
}

object SlidesDataCodecs extends SlidesDataCodecs
