package com.tjclp.xlcr
package models.excel

import io.circe._
import io.circe.generic.extras.semiauto._
import io.circe.generic.extras.{ Configuration => ExtrasConfiguration }

trait FontDataCodecs {
  implicit val configuration: ExtrasConfiguration = ExtrasConfiguration.default.withDefaults

  implicit val encoder: Encoder[FontData] = deriveConfiguredEncoder[FontData]
  implicit val decoder: Decoder[FontData] = deriveConfiguredDecoder[FontData]
}
