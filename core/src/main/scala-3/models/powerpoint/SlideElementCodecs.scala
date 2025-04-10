package com.tjclp.xlcr
package models.powerpoint

import io.circe._
import io.circe.derivation.{Configuration, ConfiguredDecoder, ConfiguredEncoder}

trait SlideElementCodecs {
  given configuration: Configuration = Configuration.default.withDefaults
  given slideElementEncoder: Encoder[SlideElement] = ConfiguredEncoder.derived[SlideElement]
  given slideElementDecoder: Decoder[SlideElement] = ConfiguredDecoder.derived[SlideElement]
}

object SlideElementCodecs extends SlideElementCodecs