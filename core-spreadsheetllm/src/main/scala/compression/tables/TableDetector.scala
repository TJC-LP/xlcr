package com.tjclp.xlcr
package compression.tables

import compression.models.{SheetGrid, TableRegion}
import compression.utils.SheetGridUtils

import org.slf4j.LoggerFactory

import scala.math.{max, min}

/**
 * TableDetector implements algorithms to identify table structures within a spreadsheet.
 * It uses both gap-based analysis and connected component detection.
 */
object TableDetector:
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Detects potential table regions within the sheet using an improved approach
   * based on the SheetCompressor framework. This method supports both the gap-based anchor approach
   * and the more advanced BFS-based region detection.
   *
   * @param grid       The sheet grid to analyze
   * @param anchorRows Set of identified anchor rows
   * @param anchorCols Set of identified anchor columns
   * @param config     Configuration options for detection, including minGapSize
   * @return List of detected table regions
   */
  def detectTableRegions(
                          grid: SheetGrid,
                          anchorRows: Set[Int],
                          anchorCols: Set[Int],
                          config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()
                        ): List[TableRegion] =
    // Pick detection strategy based on sheet size
    if grid.rowCount > 1000 || grid.rowCount * grid.colCount > 30000 then
      // Large sheet - use simpler region growth strategy
      logger.info(s"Using region growth detection for large sheet (${grid.rowCount}x${grid.colCount})")
      RegionGrowthDetector.detect(grid, config)
    else
      // Smaller sheet - use more sophisticated table sense detection
      logger.info(s"Using table sense detection for sheet (${grid.rowCount}x${grid.colCount})")
      val regions = TableSenseDetector.detect(grid, anchorRows, anchorCols, config)
      // For diagnostic purposes, log the detected tables
      if regions.nonEmpty then
        logger.info(s"Detected ${regions.size} tables using enhanced column detection")
        regions.foreach { region =>
          logger.info(f"Table at (${region.topRow},${region.leftCol}) to (${region.bottomRow},${region.rightCol}): ${region.width}x${region.height}")
        }
      regions

  /**
   * Finds gaps (sequences of missing indices) in a sorted sequence.
   *
   * @param indices    Sorted sequence of indices
   * @param maxIndex   The maximum possible index (exclusive)
   * @param minGapSize The minimum gap size to consider significant
   * @return List of gaps as (start, end) pairs
   */
  def findGaps(indices: Seq[Int], maxIndex: Int, minGapSize: Int): List[(Int, Int)] =
    if indices.isEmpty then
      return List((0, maxIndex - 1))

    val result = scala.collection.mutable.ListBuffer[(Int, Int)]()

    // Check for a gap at the beginning - for leading gaps, be more lenient
    val leadingGapThreshold = math.max(1, minGapSize - 1)
    if indices.head > leadingGapThreshold then
      result += ((0, indices.head - 1))

    // Check for gaps between indices
    for i <- 0 until indices.size - 1 do
      val current = indices(i)
      val next = indices(i + 1)

      if next - current > minGapSize then
        result += ((current + 1, next - 1))

    // Check for a gap at the end - for trailing gaps, be more lenient
    val trailingGapThreshold = math.max(1, minGapSize - 1)
    if maxIndex - indices.last > trailingGapThreshold then
      result += ((indices.last + 1, maxIndex - 1))

    result.toList

  /**
   * Converts a list of gaps into a list of segments.
   *
   * @param gaps     List of gaps as (start, end) pairs
   * @param maxIndex The maximum possible index (exclusive)
   * @return List of segments as (start, end) pairs
   */
  def segmentsFromGaps(gaps: List[(Int, Int)], maxIndex: Int): List[(Int, Int)] =
    if gaps.isEmpty then
      return List((0, maxIndex - 1))

    val sortedGaps = gaps.sortBy(_._1)
    val result = scala.collection.mutable.ListBuffer[(Int, Int)]()

    // Add segment before first gap
    if sortedGaps.head._1 > 0 then
      result += ((0, sortedGaps.head._1 - 1))

    // Add segments between gaps
    for i <- 0 until sortedGaps.size - 1 do
      val currentGapEnd = sortedGaps(i)._2
      val nextGapStart = sortedGaps(i + 1)._1

      if nextGapStart > currentGapEnd + 1 then
        result += ((currentGapEnd + 1, nextGapStart - 1))

    // Add segment after last gap
    if sortedGaps.last._2 < maxIndex - 1 then
      result += ((sortedGaps.last._2 + 1, maxIndex - 1))

    result.toList

  /**
   * Calculate the overlap area between two regions
   */
  def calculateOverlapArea(r1: TableRegion, r2: TableRegion): Int = {
    val overlapWidth = max(0, min(r1.rightCol, r2.rightCol) - max(r1.leftCol, r2.leftCol) + 1)
    val overlapHeight = max(0, min(r1.bottomRow, r2.bottomRow) - max(r1.topRow, r2.topRow) + 1)
    overlapWidth * overlapHeight
  }

  /**
   * Sort regions by location (top-to-bottom, left-to-right)
   */
  def rankBoxesByLocation(regions: List[TableRegion]): List[TableRegion] = {
    regions.sortBy(r => (r.topRow, r.leftCol))
  }

  /**
   * Refine boundaries of regions using enhanced trimming
   */
  def refineBoundaries(regions: List[TableRegion], grid: SheetGrid): List[TableRegion] = {
    // Use RegionGrowthDetector's enhanced boundary refinement
    val config = SpreadsheetLLMConfig()
    RegionGrowthDetector.refineBoundaries(regions, grid, config)
  }

  /**
   * Trim empty top and bottom rows
   */
  private def trimEmptyTopBottom(region: TableRegion, grid: SheetGrid): TableRegion = {
    var topRow = region.topRow
    var bottomRow = region.bottomRow

    // Trim from top
    while (topRow < bottomRow && SheetGridUtils.isRowEmpty(grid, region.leftCol, region.rightCol, topRow)) {
      topRow += 1
    }

    // Trim from bottom
    while (bottomRow > topRow && SheetGridUtils.isRowEmpty(grid, region.leftCol, region.rightCol, bottomRow)) {
      bottomRow -= 1
    }

    if (topRow != region.topRow || bottomRow != region.bottomRow) {
      TableRegion(topRow, bottomRow, region.leftCol, region.rightCol,
        region.anchorRows, region.anchorCols)
    } else {
      region
    }
  }

  /**
   * Trim empty left and right columns
   */
  private def trimEmptySides(region: TableRegion, grid: SheetGrid): TableRegion = {
    var leftCol = region.leftCol
    var rightCol = region.rightCol

    // Trim from left
    while (leftCol < rightCol && SheetGridUtils.isColEmpty(grid, region.topRow, region.bottomRow, leftCol)) {
      leftCol += 1
    }

    // Trim from right
    while (rightCol > leftCol && SheetGridUtils.isColEmpty(grid, region.topRow, region.bottomRow, rightCol)) {
      rightCol -= 1
    }

    if (leftCol != region.leftCol || rightCol != region.rightCol) {
      TableRegion(region.topRow, region.bottomRow, leftCol, rightCol,
        region.anchorRows, region.anchorCols)
    } else {
      region
    }
  }