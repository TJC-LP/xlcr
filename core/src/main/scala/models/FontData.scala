package com.tjclp.xlcr
package models

import io.circe.*
import io.circe.generic.semiauto.*

/**
 * Represents font information extracted from a cell.
 */
final case class FontData(
                           name: String,
                           size: Option[Int],
                           bold: Boolean = false,
                           italic: Boolean = false,
                           underline: Option[Byte] = None,
                           strikeout: Boolean = false,
                           colorIndex: Option[Int] = None,
                           rgbColor: Option[String] = None // Hex RGB color string
                         )

object FontData:
  implicit val encoder: Encoder[FontData] = deriveEncoder[FontData]
  implicit val decoder: Decoder[FontData] = deriveDecoder[FontData]