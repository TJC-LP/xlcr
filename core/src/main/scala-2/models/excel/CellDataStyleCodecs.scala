package com.tjclp.xlcr
package models.excel

import io.circe._
import io.circe.generic.extras.{Configuration => ExtrasConfiguration}
import io.circe.generic.extras.semiauto._

trait CellDataStyleCodecs {
  implicit val configuration: ExtrasConfiguration = ExtrasConfiguration.default.withDefaults

  implicit val encoder: Encoder[CellDataStyle] = deriveConfiguredEncoder[CellDataStyle]
  implicit val decoder: Decoder[CellDataStyle] = deriveConfiguredDecoder[CellDataStyle]
}