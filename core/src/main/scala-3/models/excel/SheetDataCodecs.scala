package com.tjclp.xlcr
package models.excel

import io.circe._
import io.circe.derivation.{ Configuration, ConfiguredDecoder, ConfiguredEncoder }

trait SheetDataCodecs {
  given Configuration = Configuration.default.withDefaults

  given Encoder[SheetData] = ConfiguredEncoder.derived[SheetData]
  given Decoder[SheetData] = ConfiguredDecoder.derived[SheetData]
}
