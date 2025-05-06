package com.tjclp.xlcr
package compression.models

/**
 * Information about a table detected in the grid
 *
 * @param topRow
 *   The top row index of the table
 * @param bottomRow
 *   The bottom row index of the table
 * @param leftCol
 *   The leftmost column index of the table
 * @param rightCol
 *   The rightmost column index of the table
 * @param anchorRows
 *   Set of row indices that are anchors within this table
 * @param anchorCols
 *   Set of column indices that are anchors within this table
 */
case class TableRegion(
  topRow: Int,
  bottomRow: Int,
  leftCol: Int,
  rightCol: Int,
  anchorRows: Set[Int],
  anchorCols: Set[Int]
) {

  /** Calculate width, ensuring non-negative result */
  def width: Int = math.max(0, rightCol - leftCol + 1)

  /** Calculate height, ensuring non-negative result */
  def height: Int = math.max(0, bottomRow - topRow + 1)

  /** Calculate area, ensuring non-negative result */
  def area: Int = width * height

  /** Check if the region is valid (non-negative dimensions) */
  def isValid: Boolean = width > 0 && height > 0

  /** Get all rows in this table region */
  def allRows: Set[Int] = (topRow to bottomRow).toSet

  /** Get all columns in this table region */
  def allCols: Set[Int] = (leftCol to rightCol).toSet

  /** Check if this region contains the given cell coordinates */
  def contains(row: Int, col: Int): Boolean =
    isValid && row >= topRow && row <= bottomRow && col >= leftCol && col <= rightCol

  /** Check if this region fully contains another region */
  def contains(other: TableRegion): Boolean =
    isValid && other.isValid && topRow <= other.topRow && bottomRow >= other.bottomRow &&
      leftCol <= other.leftCol && rightCol >= other.rightCol

  /** Check if this region contains another region with some tolerance for boundary differences */
  def containsWithTolerance(other: TableRegion, tolerance: Int): Boolean =
    isValid && other.isValid &&
      (topRow - tolerance) <= other.topRow && (bottomRow + tolerance) >= other.bottomRow &&
      (leftCol - tolerance) <= other.leftCol && (rightCol + tolerance) >= other.rightCol

  /** Check if this region overlaps with another region */
  def overlaps(other: TableRegion): Boolean =
    isValid && other.isValid &&
      !(rightCol < other.leftCol || leftCol > other.rightCol ||
        bottomRow < other.topRow || topRow > other.bottomRow)

  /**
   * Sophisticated overlap check with support for directional exceptions
   * @param other
   *   Region to check for overlap with
   * @param exceptForward
   *   If true, don't count as overlap if this region fully contains other
   * @param exceptBackward
   *   If true, don't count as overlap if other region fully contains this
   * @return
   *   True if regions overlap under the given constraints
   */
  def overlapsWith(
    other: TableRegion,
    exceptForward: Boolean = false,
    exceptBackward: Boolean = false
  ): Boolean = {
    // Skip if this fully contains other and we're excepting forward containment
    if (exceptForward && this.contains(other)) return false

    // Skip if other fully contains this and we're excepting backward containment
    if (exceptBackward && other.contains(this)) return false

    // Standard overlap check
    overlaps(other)
  }
}
