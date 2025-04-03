package com.tjclp.xlcr
package models.excel

import io.circe._
import io.circe.generic.extras.{Configuration => ExtrasConfiguration}
import io.circe.generic.extras.semiauto._

trait SheetsDataCodecs {
  implicit val configuration: ExtrasConfiguration = ExtrasConfiguration.default.withDefaults

  implicit val encoder: Encoder[SheetsData] = deriveConfiguredEncoder[SheetsData]
  implicit val decoder: Decoder[SheetsData] = deriveConfiguredDecoder[SheetsData]
}