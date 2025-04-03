package com.tjclp.xlcr
package models.powerpoint

import io.circe._
import io.circe.derivation.{Configuration, ConfiguredDecoder, ConfiguredEncoder}

/**
 * Represents font information for a SlideElement in PowerPoint.
 */
final case class PptFontData(
                              name: String,
                              size: Option[Int] = None,
                              bold: Boolean = false,
                              italic: Boolean = false,
                              underline: Boolean = false,
                              color: Option[String] = None // e.g. "#RRGGBB"
                            )

object PptFontData {
  implicit val configuration: Configuration = Configuration.default.withDefaults

  implicit val encoder: Encoder[PptFontData] = ConfiguredEncoder.derived[PptFontData]
  implicit val decoder: Decoder[PptFontData] = ConfiguredDecoder.derived[PptFontData]
}
  