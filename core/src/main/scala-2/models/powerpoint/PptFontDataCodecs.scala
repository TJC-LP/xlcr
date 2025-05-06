package com.tjclp.xlcr
package models.powerpoint

import io.circe._
import io.circe.generic.extras.semiauto._
import io.circe.generic.extras.{ Configuration => ExtrasConfiguration }

trait PptFontDataCodecs {
  implicit val configuration: ExtrasConfiguration = ExtrasConfiguration.default.withDefaults

  implicit val encoder: Encoder[PptFontData] = deriveConfiguredEncoder[PptFontData]
  implicit val decoder: Decoder[PptFontData] = deriveConfiguredDecoder[PptFontData]
}
