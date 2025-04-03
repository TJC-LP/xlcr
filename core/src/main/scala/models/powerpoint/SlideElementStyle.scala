package com.tjclp.xlcr
package models.powerpoint

/**
 * SlideElementStyle holds visual properties for a SlideElement,
 * including fill color, stroke color, stroke width, and an optional font.
 */
final case class SlideElementStyle(
                                    fillColor: Option[String] = None, // e.g. "#RRGGBB"
                                    strokeColor: Option[String] = None, // e.g. "#RRGGBB"
                                    strokeWidth: Option[Double] = None,
                                    font: Option[PptFontData] = None
                                  )

object SlideElementStyle extends SlideElementStyleCodecs
