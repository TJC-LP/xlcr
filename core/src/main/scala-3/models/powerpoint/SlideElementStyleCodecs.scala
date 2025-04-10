package com.tjclp.xlcr
package models.powerpoint

import io.circe._
import io.circe.derivation.{Configuration, ConfiguredDecoder, ConfiguredEncoder}

trait SlideElementStyleCodecs {
  given configuration: Configuration = Configuration.default.withDefaults
  given slideElementStyleEncoder: Encoder[SlideElementStyle] = ConfiguredEncoder.derived[SlideElementStyle]
  given slideElementStyleDecoder: Decoder[SlideElementStyle] = ConfiguredDecoder.derived[SlideElementStyle]
}

object SlideElementStyleCodecs extends SlideElementStyleCodecs