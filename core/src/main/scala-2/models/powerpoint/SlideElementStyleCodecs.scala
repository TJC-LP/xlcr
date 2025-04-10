package com.tjclp.xlcr
package models.powerpoint

import io.circe._
import io.circe.generic.semiauto._

trait SlideElementStyleCodecs {
  implicit val slideElementStyleEncoder: Encoder[SlideElementStyle] = deriveEncoder[SlideElementStyle]
  implicit val slideElementStyleDecoder: Decoder[SlideElementStyle] = deriveDecoder[SlideElementStyle]
}

object SlideElementStyleCodecs extends SlideElementStyleCodecs