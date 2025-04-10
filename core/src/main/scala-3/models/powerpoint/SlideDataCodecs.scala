package com.tjclp.xlcr
package models.powerpoint

import io.circe._
import io.circe.derivation.{Configuration, ConfiguredDecoder, ConfiguredEncoder}

trait SlideDataCodecs {
  given configuration: Configuration = Configuration.default.withDefaults
  given slideDataEncoder: Encoder[SlideData] = ConfiguredEncoder.derived[SlideData]
  given slideDataDecoder: Decoder[SlideData] = ConfiguredDecoder.derived[SlideData]
}

object SlideDataCodecs extends SlideDataCodecs