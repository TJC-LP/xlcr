package com.tjclp.xlcr
package models.powerpoint

import io.circe._
import io.circe.derivation.{Configuration, ConfiguredDecoder, ConfiguredEncoder}

trait PptFontDataCodecs {
  given Configuration = Configuration.default.withDefaults

  given Encoder[PptFontData] = ConfiguredEncoder.derived[PptFontData]
  given Decoder[PptFontData] = ConfiguredDecoder.derived[PptFontData]
}