package com.tjclp.xlcr
package models.excel

import io.circe._
import io.circe.derivation.{ Configuration, ConfiguredDecoder, ConfiguredEncoder }

trait FontDataCodecs {
  given Configuration = Configuration.default.withDefaults

  given Encoder[FontData] = ConfiguredEncoder.derived[FontData]
  given Decoder[FontData] = ConfiguredDecoder.derived[FontData]
}
