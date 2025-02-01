package com.tjclp.xlcr
package models.ppt

import io.circe._
import io.circe.derivation.{Configuration, ConfiguredDecoder, ConfiguredEncoder}

/**
 * SlideElementStyle holds visual properties for a SlideElement,
 * including fill color, stroke color, stroke width, and an optional font.
 */
final case class SlideElementStyle(
  fillColor: Option[String] = None,   // e.g. "#RRGGBB"
  strokeColor: Option[String] = None, // e.g. "#RRGGBB"
  strokeWidth: Option[Double] = None,
  font: Option[PptFontData] = None
)

object SlideElementStyle:
  given Configuration = Configuration.default.withDefaults
  implicit val encoder: Encoder[SlideElementStyle] = ConfiguredEncoder.derived[SlideElementStyle]
  implicit val decoder: Decoder[SlideElementStyle] = ConfiguredDecoder.derived[SlideElementStyle]