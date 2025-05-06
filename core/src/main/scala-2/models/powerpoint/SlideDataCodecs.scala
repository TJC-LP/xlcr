package com.tjclp.xlcr
package models.powerpoint

import io.circe._
import io.circe.generic.semiauto._

trait SlideDataCodecs {
  implicit val slideDataEncoder: Encoder[SlideData] = deriveEncoder[SlideData]
  implicit val slideDataDecoder: Decoder[SlideData] = deriveDecoder[SlideData]
}

object SlideDataCodecs extends SlideDataCodecs
