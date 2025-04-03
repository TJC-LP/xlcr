package com.tjclp.xlcr
package models.powerpoint

import io.circe.*
import io.circe.generic.semiauto.*

final case class Position(
                           x: Double,
                           y: Double,
                           width: Double,
                           height: Double,
                           rotation: Option[Double] = Some(0.0)
                         )

object Position {
  implicit val encoder: Encoder[Position] = deriveEncoder[Position]
  implicit val decoder: Decoder[Position] = deriveDecoder[Position]
}
  