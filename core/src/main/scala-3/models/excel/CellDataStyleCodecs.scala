package com.tjclp.xlcr
package models.excel

import io.circe._
import io.circe.derivation.{ Configuration, ConfiguredDecoder, ConfiguredEncoder }

trait CellDataStyleCodecs {
  given Configuration = Configuration.default.withDefaults

  given Encoder[CellDataStyle] = ConfiguredEncoder.derived[CellDataStyle]
  given Decoder[CellDataStyle] = ConfiguredDecoder.derived[CellDataStyle]
}
