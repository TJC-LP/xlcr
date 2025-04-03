package com.tjclp.xlcr
package models.excel

import io.circe._
import io.circe.derivation.{Configuration, ConfiguredDecoder, ConfiguredEncoder}

trait SheetsDataCodecs {
  given Configuration = Configuration.default.withDefaults

  given Encoder[SheetsData] = ConfiguredEncoder.derived[SheetsData]
  given Decoder[SheetsData] = ConfiguredDecoder.derived[SheetsData]
}