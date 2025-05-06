package com.tjclp.xlcr
package compression.tables

import scala.math.{ max, min }

import org.slf4j.LoggerFactory

import compression.models.{ SheetGrid, TableRegion }
import compression.utils.SheetGridUtils

/**
 * TableDetector implements algorithms to identify table structures within a spreadsheet. It uses
 * both gap-based analysis and connected component detection.
 */
object TableDetector {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Detects potential table regions within the sheet using an improved approach based on the
   * SheetCompressor framework. This method supports both the gap-based anchor approach and the more
   * advanced BFS-based region detection.
   *
   * @param grid
   *   The sheet grid to analyze
   * @param anchorRows
   *   Set of identified anchor rows
   * @param anchorCols
   *   Set of identified anchor columns
   * @param config
   *   Configuration options for detection, including minGapSize
   * @return
   *   List of detected table regions
   */
  def detectTableRegions(
    grid: SheetGrid,
    anchorRows: Set[Int],
    anchorCols: Set[Int],
    config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()
  ): List[TableRegion] =
    // Pick detection strategy based on sheet size
    if (grid.rowCount > 1000 || grid.rowCount * grid.colCount > 30000) {
      // Large sheet - use simpler region growth strategy
      logger.info(
        s"Using region growth detection for large sheet (${grid.rowCount}x${grid.colCount})"
      )
      RegionGrowthDetector.detect(grid, config)
    } else {
      // Smaller sheet - use more sophisticated table sense detection
      logger.info(s"Using table sense detection for sheet (${grid.rowCount}x${grid.colCount})")
      val regions = TableSenseDetector.detect(grid, anchorRows, anchorCols, config)
      // For diagnostic purposes, log the detected tables
      if (regions.nonEmpty) {
        logger.info(s"Detected ${regions.size} tables using enhanced column detection")
        regions.foreach { region =>
          logger.info(f"Table at (${region.topRow},${region.leftCol}) to (${region
              .bottomRow},${region.rightCol}): ${region.width}x${region.height}")
        }
      }
      regions
    }

  /**
   * Finds gaps (sequences of missing indices) in a sorted sequence.
   *
   * @param indices
   *   Sorted sequence of indices
   * @param maxIndex
   *   The maximum possible index (exclusive)
   * @param minGapSize
   *   The minimum gap size to consider significant
   * @return
   *   List of gaps as (start, end) pairs
   */
  def findGaps(indices: Seq[Int], maxIndex: Int, minGapSize: Int): List[(Int, Int)] = {
    // Handle empty sequence: the entire range is a gap
    if (indices.isEmpty) {
      // Only return a gap if the range itself is larger than the minimum gap size or if minGapSize is 1 (meaning any empty space counts)
      if (maxIndex > 0 && (maxIndex >= minGapSize || minGapSize <= 1)) {
        return List((0, maxIndex - 1))
      } else {
        return List.empty // No gap if the range is too small
      }
    }

    val result = scala.collection.mutable.ListBuffer[(Int, Int)]()

    // Check for a gap at the beginning
    val firstIndex = indices.head
    // A gap exists if the first index is greater than or equal to the minGapSize
    // Example: maxIndex=10, indices=[5, 7], minGapSize=2. Gap is 0..4. firstIndex=5. 5 >= 2. Add (0, 4).
    // Example: maxIndex=10, indices=[1, 7], minGapSize=2. firstIndex=1. 1 < 2. No gap at start.
    if (firstIndex >= minGapSize) {
      result += ((0, firstIndex - 1))
    }

    // Check for gaps between indices
    for (i <- 0 until indices.size - 1) {
      val current = indices(i)
      val next    = indices(i + 1)
      val diff    = next - current

      // A gap exists if the difference between consecutive indices is greater than minGapSize
      // Example: indices=[1, 7], minGapSize=2. next=7, current=1. diff=6. 6 > 2. Add gap (1+1, 7-1) => (2, 6).
      // Example: indices=[1, 3], minGapSize=2. next=3, current=1. diff=2. 2 is not > 2. No gap.
      if (diff > minGapSize) {
        result += ((current + 1, next - 1))
      }
    }

    // Check for a gap at the end
    val lastIndex = indices.last
    // A gap exists if the space between the last index and maxIndex is greater than or equal to minGapSize
    // Example: maxIndex=10, indices=[1, 7], minGapSize=2. lastIndex=7. maxIndex - 1 - lastIndex = 10 - 1 - 7 = 2. 2 >= 2. Add gap (7+1, 10-1) => (8, 9).
    // Example: maxIndex=10, indices=[1, 8], minGapSize=2. lastIndex=8. maxIndex - 1 - lastIndex = 10 - 1 - 8 = 1. 1 < 2. No gap at end.
    if ((maxIndex - 1 - lastIndex) >= minGapSize) {
      result += ((lastIndex + 1, maxIndex - 1))
    }

    result.toList
  }

  /**
   * Converts a list of gaps into a list of segments (regions between gaps).
   *
   * @param gaps
   *   List of gaps as (start, end) pairs
   * @param maxIndex
   *   The maximum possible index (exclusive)
   * @return
   *   List of segments as (start, end) pairs
   */
  def segmentsFromGaps(gaps: List[(Int, Int)], maxIndex: Int): List[(Int, Int)] = {
    if (maxIndex <= 0) return List.empty // Handle invalid maxIndex

    if (gaps.isEmpty) {
      // If no gaps, the entire range is one segment
      return List((0, maxIndex - 1))
    }

    val sortedGaps = gaps.filter(g => g._1 <= g._2).sortBy(_._1)
    if (sortedGaps.isEmpty) {
      return List((0, maxIndex - 1))
    }

    val result = scala.collection.mutable.ListBuffer[(Int, Int)]()

    // Add segment before first gap, if it exists
    val firstGapStart = sortedGaps.head._1
    if (firstGapStart > 0) {
      result += ((0, firstGapStart - 1))
    }

    // Add segments between gaps
    for (i <- 0 until sortedGaps.size - 1) {
      val currentGapEnd = sortedGaps(i)._2
      val nextGapStart  = sortedGaps(i + 1)._1

      // Check if there is space between the current gap end and the next gap start
      if (nextGapStart > currentGapEnd + 1) {
        result += ((currentGapEnd + 1, nextGapStart - 1))
      }
    }

    // Add segment after last gap, if it exists
    val lastGapEnd = sortedGaps.last._2
    if (lastGapEnd < maxIndex - 1) {
      result += ((lastGapEnd + 1, maxIndex - 1))
    }

    result.toList
  }

  /**
   * Calculate the overlap area between two regions
   */
  def calculateOverlapArea(r1: TableRegion, r2: TableRegion): Int = {
    val overlapWidth  = max(0, min(r1.rightCol, r2.rightCol) - max(r1.leftCol, r2.leftCol) + 1)
    val overlapHeight = max(0, min(r1.bottomRow, r2.bottomRow) - max(r1.topRow, r2.topRow) + 1)
    overlapWidth * overlapHeight
  }

  /**
   * Sort regions by location (top-to-bottom, left-to-right)
   */
  def rankBoxesByLocation(regions: List[TableRegion]): List[TableRegion] =
    regions.sortBy(r => (r.topRow, r.leftCol))

  /**
   * Refine boundaries of regions using enhanced trimming provided by RegionGrowthDetector. This
   * delegates to the potentially more sophisticated logic there.
   */
  def refineBoundaries(regions: List[TableRegion], grid: SheetGrid): List[TableRegion] = {
    // Use RegionGrowthDetector's enhanced boundary refinement
    val config = SpreadsheetLLMConfig()
    RegionGrowthDetector.refineBoundaries(regions, grid, config)
  }

  /**
   * Trim empty top and bottom rows (kept for potential direct use or reference).
   */
  private def trimEmptyTopBottom(region: TableRegion, grid: SheetGrid): TableRegion = {
    var topRow    = region.topRow
    var bottomRow = region.bottomRow

    // Trim from top
    while (
      topRow <= bottomRow && SheetGridUtils.isRowEmpty(
        grid,
        region.leftCol,
        region.rightCol,
        topRow
      )
    )
      topRow += 1

    // Trim from bottom - Ensure bottomRow doesn't go below the potentially updated topRow
    while (
      bottomRow >= topRow && SheetGridUtils.isRowEmpty(
        grid,
        region.leftCol,
        region.rightCol,
        bottomRow
      )
    )
      bottomRow -= 1

    // Check if the region became invalid (e.g., topRow > bottomRow)
    if (topRow <= bottomRow) {
      // Only create a new region if boundaries actually changed
      if (topRow != region.topRow || bottomRow != region.bottomRow) {
        // Preserve original anchors, filter them if necessary based on new bounds
        val updatedAnchorRows = region.anchorRows.filter(r => r >= topRow && r <= bottomRow)
        TableRegion(
          topRow,
          bottomRow,
          region.leftCol,
          region.rightCol,
          updatedAnchorRows,
          region.anchorCols
        )
      } else {
        region // No change
      }
    } else {
      // Region became invalid after trimming, return an invalid region or handle appropriately
      TableRegion(
        region.topRow,
        region.topRow - 1,
        region.leftCol,
        region.leftCol - 1,
        Set.empty,
        Set.empty
      ) // Example invalid
    }
  }

  /**
   * Trim empty left and right columns (kept for potential direct use or reference).
   */
  private def trimEmptySides(region: TableRegion, grid: SheetGrid): TableRegion = {
    var leftCol  = region.leftCol
    var rightCol = region.rightCol

    // Check if the region is already invalid before trimming sides
    if (region.topRow > region.bottomRow)
      return region // Skip if already invalid from top/bottom trim

    // Trim from left
    while (
      leftCol <= rightCol && SheetGridUtils.isColEmpty(
        grid,
        region.topRow,
        region.bottomRow,
        leftCol
      )
    )
      leftCol += 1

    // Trim from right - Ensure rightCol doesn't go below the potentially updated leftCol
    while (
      rightCol >= leftCol && SheetGridUtils.isColEmpty(
        grid,
        region.topRow,
        region.bottomRow,
        rightCol
      )
    )
      rightCol -= 1

    // Check if the region became invalid (e.g., leftCol > rightCol)
    if (leftCol <= rightCol) {
      // Only create a new region if boundaries actually changed
      if (leftCol != region.leftCol || rightCol != region.rightCol) {
        // Preserve original anchors, filter them if necessary based on new bounds
        val updatedAnchorCols = region.anchorCols.filter(c => c >= leftCol && c <= rightCol)
        TableRegion(
          region.topRow,
          region.bottomRow,
          leftCol,
          rightCol,
          region.anchorRows,
          updatedAnchorCols
        )
      } else {
        region // No change
      }
    } else {
      // Region became invalid after trimming, return an invalid region or handle appropriately
      TableRegion(
        region.topRow,
        region.topRow - 1,
        region.leftCol,
        region.leftCol - 1,
        Set.empty,
        Set.empty
      ) // Example invalid
    }
  }
}
