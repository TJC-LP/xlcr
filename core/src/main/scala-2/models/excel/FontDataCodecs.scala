package com.tjclp.xlcr
package models.excel

import io.circe._
import io.circe.generic.extras.{Configuration => ExtrasConfiguration}
import io.circe.generic.extras.semiauto._

trait FontDataCodecs {
  implicit val configuration: ExtrasConfiguration = ExtrasConfiguration.default.withDefaults

  implicit val encoder: Encoder[FontData] = deriveConfiguredEncoder[FontData]
  implicit val decoder: Decoder[FontData] = deriveConfiguredDecoder[FontData]
}