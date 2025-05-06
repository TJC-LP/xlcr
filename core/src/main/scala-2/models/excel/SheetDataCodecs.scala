package com.tjclp.xlcr
package models.excel

import io.circe._
import io.circe.generic.extras.semiauto._
import io.circe.generic.extras.{ Configuration => ExtrasConfiguration }

trait SheetDataCodecs {
  implicit val configuration: ExtrasConfiguration = ExtrasConfiguration.default.withDefaults

  implicit val sheetDataEncoder: Encoder[SheetData] = deriveConfiguredEncoder[SheetData]
  implicit val sheetDataDecoder: Decoder[SheetData] = deriveConfiguredDecoder[SheetData]
}
