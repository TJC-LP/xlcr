package com.tjclp.xlcr
package models.powerpoint

import io.circe._
import io.circe.generic.semiauto._

trait SlideElementCodecs {
  implicit val slideElementEncoder: Encoder[SlideElement] = deriveEncoder[SlideElement]
  implicit val slideElementDecoder: Decoder[SlideElement] = deriveDecoder[SlideElement]
}

object SlideElementCodecs extends SlideElementCodecs
