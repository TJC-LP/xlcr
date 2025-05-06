package com.tjclp.xlcr
package models.powerpoint

import io.circe._
import io.circe.derivation.{ Configuration, ConfiguredDecoder, ConfiguredEncoder }

trait PositionCodecs {
  given Configuration = Configuration.default.withDefaults

  given Encoder[Position] = ConfiguredEncoder.derived[Position]
  given Decoder[Position] = ConfiguredDecoder.derived[Position]
}
