package com.tjclp.xlcr
package models.powerpoint

final case class Position(
  x: Double,
  y: Double,
  width: Double,
  height: Double,
  rotation: Option[Double] = Some(0.0)
)

object Position extends PositionCodecs
