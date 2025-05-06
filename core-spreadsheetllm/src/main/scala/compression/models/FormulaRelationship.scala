package com.tjclp.xlcr
package compression.models

/**
 * Represents a formula dependency relationship
 *
 * @param sourceCell
 *   The cell containing the formula
 * @param targetCells
 *   The cells that the formula references
 */
case class FormulaRelationship(
  sourceCell: (Int, Int),
  targetCells: Set[(Int, Int)]
)
