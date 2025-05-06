package com.tjclp.xlcr
package models.powerpoint

import io.circe._
import io.circe.generic.extras.semiauto._
import io.circe.generic.extras.{ Configuration => ExtrasConfiguration }

trait PositionCodecs {
  implicit val configuration: ExtrasConfiguration = ExtrasConfiguration.default.withDefaults

  implicit val encoder: Encoder[Position] = deriveConfiguredEncoder[Position]
  implicit val decoder: Decoder[Position] = deriveConfiguredDecoder[Position]
}
