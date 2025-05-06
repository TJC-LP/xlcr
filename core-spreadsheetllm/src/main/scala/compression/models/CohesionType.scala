package com.tjclp.xlcr
package compression.models

object CohesionType {
  case object Format
      extends CohesionType // Based on formatting cues like borders, colors, etc
  case object Content extends CohesionType // Based on content relationships
  case object Merged  extends CohesionType // Based on merged cells
  case object Formula extends CohesionType // Based on formula relationships
  case object Border  extends CohesionType // Based on border patterns
  case object Pivot   extends CohesionType // Part of a pivot table
  case object ForcedLayout
      extends CohesionType // Based on layout patterns that suggest strong cohesion
}

// Scala 2 compatible CohesionType
sealed trait CohesionType
