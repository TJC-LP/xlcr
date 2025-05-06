package com.tjclp.xlcr
package models.powerpoint

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

object PptFontData extends PptFontDataCodecs
