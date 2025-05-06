package com.tjclp.xlcr
package models.excel

import io.circe._
import io.circe.derivation.{ Configuration, ConfiguredDecoder, ConfiguredEncoder }

trait CellDataCodecs {
  given Configuration = Configuration.default.withDefaults

  given Encoder[CellData] = ConfiguredEncoder.derived[CellData]
  given Decoder[CellData] = ConfiguredDecoder.derived[CellData]
}
