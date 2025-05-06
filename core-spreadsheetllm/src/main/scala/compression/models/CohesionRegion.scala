package com.tjclp.xlcr
package compression.models

/**
 * Represents a cohesion region within a sheet - an area where cells should be kept together due to
 * their relationship, formatting, or other structural cues.
 *
 * @param topRow
 *   Row index of the top of the region
 * @param bottomRow
 *   Row index of the bottom of the region
 * @param leftCol
 *   Column index of the left side of the region
 * @param rightCol
 *   Column index of the right side of the region
 * @param cohesionType
 *   The type of cohesion that caused this region to be created
 * @param strength
 *   Relative strength of the cohesion (higher means stronger relationship)
 */
case class CohesionRegion(
  topRow: Int,
  bottomRow: Int,
  leftCol: Int,
  rightCol: Int,
  cohesionType: CohesionType,
  strength: Int = 1
) {

  /** Width of the region */
  def width: Int = rightCol - leftCol + 1

  /** Height of the region */
  def height: Int = bottomRow - topRow + 1

  /** Area of the region in cells */
  def area: Int = width * height

  /** Check if this region contains the given cell coordinates */
  def contains(row: Int, col: Int): Boolean =
    row >= topRow && row <= bottomRow && col >= leftCol && col <= rightCol

  /** Check if this region fully contains another region */
  def contains(other: CohesionRegion): Boolean =
    topRow <= other.topRow && bottomRow >= other.bottomRow &&
      leftCol <= other.leftCol && rightCol >= other.rightCol

  /** Check if this region contains another table region */
  def contains(tableRegion: TableRegion): Boolean =
    topRow <= tableRegion.topRow && bottomRow >= tableRegion.bottomRow &&
      leftCol <= tableRegion.leftCol && rightCol >= tableRegion.rightCol

  /** Check if this region overlaps with another cohesion region */
  def overlaps(other: CohesionRegion): Boolean =
    !(rightCol < other.leftCol || leftCol > other.rightCol ||
      bottomRow < other.topRow || topRow > other.bottomRow)

  /** Check if this region overlaps with a table region */
  def overlaps(tableRegion: TableRegion): Boolean =
    !(rightCol < tableRegion.leftCol || leftCol > tableRegion.rightCol ||
      bottomRow < tableRegion.topRow || topRow > tableRegion.bottomRow)

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
    val thisAsTableRegion =
      TableRegion(topRow, bottomRow, leftCol, rightCol, Set.empty, Set.empty)
    if (exceptBackward && other.contains(thisAsTableRegion)) return false

    // Standard overlap check
    overlaps(other)
  }

  /** Convert this cohesion region to a table region */
  def toTableRegion(
    anchorRows: Set[Int] = Set.empty,
    anchorCols: Set[Int] = Set.empty
  ): TableRegion =
    TableRegion(topRow, bottomRow, leftCol, rightCol, anchorRows, anchorCols)
}
