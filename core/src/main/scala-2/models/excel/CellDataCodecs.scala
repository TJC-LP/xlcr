package com.tjclp.xlcr
package models.excel

import io.circe._
import io.circe.generic.extras.semiauto._
import io.circe.generic.extras.{ Configuration => ExtrasConfiguration }

trait CellDataCodecs {
  implicit val configuration: ExtrasConfiguration = ExtrasConfiguration.default.withDefaults

  implicit val encoder: Encoder[CellData] = deriveConfiguredEncoder[CellData]
  implicit val decoder: Decoder[CellData] = deriveConfiguredDecoder[CellData]
}
