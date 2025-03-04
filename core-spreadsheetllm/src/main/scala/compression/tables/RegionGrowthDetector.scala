package com.tjclp.xlcr
package compression.tables

import compression.models.{SheetGrid, TableRegion}
import compression.utils.SheetGridUtils

import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.math.{max, min}

/**
 * Implementation of the RegionGrowth algorithm for table detection.
 * This is a simpler connected component approach for large sheets,
 * focused on finding connected regions efficiently.
 */
object RegionGrowthDetector:
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Main entry point for region growth detection
   */
  def detect(grid: SheetGrid, config: SpreadsheetLLMConfig): List[TableRegion] = {
    logger.info("Starting region growth detection")

    // Use BFS to find connected components with different tolerance values
    val connectedRanges = findConnectedRanges(grid, config.minGapSize, config.minGapSize)

    // Filter little and sparse boxes
    val filteredRanges = filterLittleBoxes(connectedRanges, grid)

    // Refine table boundaries
    val refinedRanges = TableDetector.refineBoundaries(filteredRanges, grid)

    // Handle header regions
    val withHeaders = retrieveUpHeaders(refinedRanges, grid, config.minGapSize)

    // Sort by location (top-left to bottom-right)
    val sortedRanges = TableDetector.rankBoxesByLocation(withHeaders)

    // Remove overlapping tables
    val finalRanges = if config.eliminateOverlaps then
      eliminateOverlaps(sortedRanges)
    else
      sortedRanges

    logger.info(s"Region growth detection found ${finalRanges.size} tables")
    finalRanges
  }

  /**
   * Filter out small or sparse boxes
   */
  private def filterLittleBoxes(regions: List[TableRegion], grid: SheetGrid): List[TableRegion] = {
    regions.filter { region =>
      // Size criteria
      val isTooBig = region.width > 50 || region.height > 50
      val isTooSmall = region.width < 2 || region.height < 2 || region.area < 8

      // Density criteria
      val cellCount = SheetGridUtils.countCellsInRegion(grid, region)
      val density = cellCount.toDouble / region.area
      val isTooSparse = density < 0.1

      // Keep tables unless they're too small or too sparse (but always keep very big tables)
      !isTooSmall && (!isTooSparse || isTooBig)
    }
  }

  /**
   * Try to include header rows above detected tables
   */
  private def retrieveUpHeaders(regions: List[TableRegion], grid: SheetGrid, step: Int): List[TableRegion] = {
    regions.map { region =>
      var newTopRow = region.topRow

      // Try to find headers up to 'step' rows above
      for (row <- (region.topRow - step) until region.topRow) {
        if (row >= 0 && HeaderDetector.isHeaderRow(grid, row, region.leftCol, region.rightCol)) {
          newTopRow = row
          // Found header, no need to check more rows above
          row == region.topRow // Force exit from loop
        }
      }

      if (newTopRow != region.topRow) {
        TableRegion(newTopRow, region.bottomRow, region.leftCol, region.rightCol,
          region.anchorRows, region.anchorCols)
      } else {
        region
      }
    }
  }

  /**
   * Eliminate overlapping tables by keeping the larger one
   */
  private def eliminateOverlaps(regions: List[TableRegion]): List[TableRegion] = {
    val result = mutable.ListBuffer[TableRegion]()
    val sortedRegions = regions.sortBy(-_.area) // Sort by decreasing area

    for (region <- sortedRegions) {
      val overlapsExisting = result.exists { existing =>
        val overlapArea = TableDetector.calculateOverlapArea(existing, region)
        // Consider significant overlap if more than 25% of the smaller table
        val overlapRatio = overlapArea.toDouble / min(existing.area, region.area)
        overlapRatio > 0.25
      }

      if (!overlapsExisting) {
        result += region
      }
    }

    result.toList
  }

  /**
   * Find connected regions in the sheet using a BFS algorithm that allows for empty cell tolerance.
   * This implements the core of FindConnectedRanges from the SheetCompressor pseudocode.
   *
   * @param grid      The sheet grid to analyze
   * @param threshHor Maximum number of empty cells to tolerate horizontally
   * @param threshVer Maximum number of empty cells to tolerate vertically
   * @param direction Search direction (0 for top-to-bottom, 1 for bottom-to-top)
   * @return List of detected table regions
   */
  def findConnectedRanges(
                           grid: SheetGrid,
                           threshHor: Int = 1,
                           threshVer: Int = 1,
                           direction: Int = 1
                         ): List[TableRegion] = {
    val height = grid.rowCount
    val width = grid.colCount

    // Track visited cells and tolerance counters
    val visited = Array.ofDim[Boolean](height, width)
    val horizontalCounter = Array.ofDim[Int](height, width) // Empty cell tolerance horizontally
    val verticalCounter = Array.ofDim[Int](height, width) // Empty cell tolerance vertically

    // Initialize counters with threshold values
    for (row <- 0 until height; col <- 0 until width) {
      horizontalCounter(row)(col) = threshHor
      verticalCounter(row)(col) = threshVer
    }

    // Define traversal range based on direction
    val rangeRow = if (direction == 0) 0 until height else (height - 1) to 0 by -1
    val rangeCol = 0 until width // Always left to right

    val tableRegions = mutable.ListBuffer[TableRegion]()

    // Scan sheet for unvisited non-empty cells
    for (row <- rangeRow; col <- rangeCol) {
      // Skip visited or empty cells
      if (!visited(row)(col)) {
        val cell = grid.cells.get((row, col))

        if (cell.exists(!_.isEmpty)) {
          // BFS to find connected region
          val queue = mutable.Queue[(Int, Int)]()
          queue.enqueue((row, col))
          visited(row)(col) = true

          var minRow = row
          var maxRow = row
          var minCol = col
          var maxCol = col

          while (queue.nonEmpty) {
            val (currentRow, currentCol) = queue.dequeue()

            // Skip if tolerance exhausted
            if (horizontalCounter(currentRow)(currentCol) == 0 ||
              verticalCounter(currentRow)(currentCol) == 0) {
              // Skip this cell
            } else {
              // Update bounds of connected region
              minRow = min(minRow, currentRow)
              maxRow = max(maxRow, currentRow)
              minCol = min(minCol, currentCol)
              maxCol = max(maxCol, currentCol)

              // Define direction vectors: right, left, down, up
              val directions = Seq((0, 1), (0, -1), (1, 0), (-1, 0))

              // Check all four directions
              for (i <- 0 until 4) {
                val nextRow = currentRow + directions(i)._1
                val nextCol = currentCol + directions(i)._2

                if (SheetGridUtils.isInBounds(nextRow, nextCol, height, width) && !visited(nextRow)(nextCol)) {
                  visited(nextRow)(nextCol) = true

                  // Check if next cell is empty
                  val nextCell = grid.cells.get((nextRow, nextCol))
                  val isEmpty = nextCell.forall(_.isEmpty)

                  if (isEmpty) {
                    // Reduce counter for empty cells
                    if (directions(i)._2 != 0) { // Horizontal movement
                      horizontalCounter(nextRow)(nextCol) = horizontalCounter(currentRow)(currentCol) - 1
                    }
                    if (directions(i)._1 != 0) { // Vertical movement
                      verticalCounter(nextRow)(nextCol) = verticalCounter(currentRow)(currentCol) - 1
                    }
                  } else {
                    // If border exists, reset tolerance counters
                    val hasBorder = nextCell.exists(c =>
                      c.hasTopBorder || c.hasBottomBorder || c.hasLeftBorder || c.hasRightBorder
                    )

                    if (hasBorder) {
                      if (directions(i)._2 != 0) { // Horizontal
                        horizontalCounter(nextRow)(nextCol) = threshHor
                      }
                      if (directions(i)._1 != 0) { // Vertical
                        verticalCounter(nextRow)(nextCol) = threshVer
                      }
                    }
                  }

                  queue.enqueue((nextRow, nextCol))
                }
              }
            }
          }

          // Add detected region if it's large enough
          if (maxRow - minRow > 1) {
            // Convert to 1-based coordinates to match our TableRegion convention
            val tableRegion = TableRegion(
              minRow, maxRow, minCol, maxCol,
              Set.empty, Set.empty // Empty anchor sets
            )
            tableRegions += tableRegion

            // Mark all cells in this region as visited to avoid redundant processing
            for (r <- minRow to maxRow; c <- minCol to maxCol) {
              visited(r)(c) = true
            }
          }
        }
      }
    }

    // Trim empty edges from regions (3 passes for stability)
    var trimmedRegions = tableRegions.toList
    for (_ <- 0 until 3) {
      trimmedRegions = trimEmptyEdges(trimmedRegions, grid)
    }

    trimmedRegions
  }

  /**
   * Trims empty edges from table regions.
   */
  private def trimEmptyEdges(regions: List[TableRegion], grid: SheetGrid): List[TableRegion] = {
    regions.map { region =>
      var up = region.topRow
      var down = region.bottomRow
      var left = region.leftCol
      var right = region.rightCol

      // Trim top rows until non-empty row found
      var foundNonEmpty = false
      for (i <- up to down if !foundNonEmpty) {
        if (!SheetGridUtils.isRowEmpty(grid, left, right, i)) {
          up = i
          foundNonEmpty = true
        }
      }

      // Trim bottom rows until non-empty row found
      foundNonEmpty = false
      for (i <- down to up by -1 if !foundNonEmpty) {
        if (!SheetGridUtils.isRowEmpty(grid, left, right, i)) {
          down = i
          foundNonEmpty = true
        }
      }

      // Trim left columns until non-empty column found
      foundNonEmpty = false
      for (j <- left to right if !foundNonEmpty) {
        if (!SheetGridUtils.isColEmpty(grid, up, down, j)) {
          left = j
          foundNonEmpty = true
        }
      }

      // Trim right columns until non-empty column found
      foundNonEmpty = false
      for (j <- right to left by -1 if !foundNonEmpty) {
        if (!SheetGridUtils.isColEmpty(grid, up, down, j)) {
          right = j
          foundNonEmpty = true
        }
      }

      // Create new trimmed region
      if (left <= right && up <= down) {
        TableRegion(up, down, left, right, Set.empty, Set.empty)
      } else {
        // If trimming made the region invalid, return the original
        region
      }
    }
  }