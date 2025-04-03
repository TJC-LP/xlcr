package com.tjclp.xlcr
package models.excel

import io.circe._
import io.circe.derivation.{Configuration, ConfiguredDecoder, ConfiguredEncoder}
import io.circe.generic.semiauto._

/**
 * Represents cell style information (colors, borders, etc.).
 */
final case class CellDataStyle(
                                backgroundColor: Option[String] = None, // Hex RGB color string
                                foregroundColor: Option[String] = None, // Hex RGB color string
                                pattern: Option[String] = None, // Fill pattern type
                                rotation: Int = 0, // Text rotation
                                indention: Int = 0, // Text indentation
                                borderTop: Option[String] = None, // Border style names
                                borderRight: Option[String] = None,
                                borderBottom: Option[String] = None,
                                borderLeft: Option[String] = None,
                                borderColors: Map[String, String] = Map.empty // Border side -> RGB color
                              )

object CellDataStyle {
  implicit val configuration: Configuration = Configuration.default.withDefaults

  implicit val encoder: Encoder[CellDataStyle] = ConfiguredEncoder.derived[CellDataStyle]
  implicit val decoder: Decoder[CellDataStyle] = ConfiguredDecoder.derived[CellDataStyle]
}
