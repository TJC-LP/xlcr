package com.tjclp.xlcr
package models.excel

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

object CellDataStyle extends CellDataStyleCodecs
