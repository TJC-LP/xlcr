package com.tjclp.xlcr
package compression.tables

import compression.models.{SheetGrid, TableRegion}
import compression.utils.SheetGridUtils

import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.math.min

/**
 * Implementation of the TableSense algorithm for table detection.
 * This is a more sophisticated multi-pass approach for smaller sheets
 * that uses gap analysis, boundary lines, and multiple criteria for high quality detection.
 */
object TableSenseDetector:
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Main entry point for table sense detection
   */
  def detect(
              grid: SheetGrid,
              anchorRows: Set[Int],
              anchorCols: Set[Int],
              config: SpreadsheetLLMConfig
            ): List[TableRegion] = {
    // Skip if we have too few anchors to form meaningful table boundaries
    if anchorRows.size < 2 || anchorCols.size < 2 then
      return List(TableRegion(
        0, grid.rowCount - 1,
        0, grid.colCount - 1,
        anchorRows, anchorCols
      ))

    // Step 1: Find gaps in anchor rows that might separate tables
    val sortedRows = anchorRows.toSeq.sorted
    val rowGaps = TableDetector.findGaps(sortedRows, grid.rowCount, config.minGapSize)

    // Step 2: Find gaps in anchor columns that might separate tables
    val sortedCols = anchorCols.toSeq.sorted
    // For columns, we'll use a potentially smaller gap size to be more sensitive to column separation
    val columnGapSize = math.max(1, config.minGapSize - 1)
    val colGaps = TableDetector.findGaps(sortedCols, grid.colCount, columnGapSize)

    // Debug logging
    if config.verbose && columnGapSize != config.minGapSize then
      logger.info(s"Using enhanced column detection with gap size $columnGapSize (row gap size: ${config.minGapSize})")
    if config.verbose then
      logger.debug(s"Found ${rowGaps.size} row gaps and ${colGaps.size} column gaps")

    // Step 3: Define row segments based on gaps
    val rowSegments = if rowGaps.isEmpty then
      List((0, grid.rowCount - 1))
    else
      TableDetector.segmentsFromGaps(rowGaps, grid.rowCount)

    // Step 4: Define column segments based on gaps
    val colSegments = if colGaps.isEmpty then
      List((0, grid.colCount - 1))
    else
      TableDetector.segmentsFromGaps(colGaps, grid.colCount)

    // Step 5: Use connected range detection as an additional approach
    // Try multiple thresholds to catch different table types
    val connectedRanges = mutable.ListBuffer[TableRegion]()

    for (threshHor <- 1 to 2; threshVer <- 1 to 2) {
      val foundRanges = RegionGrowthDetector.findConnectedRanges(grid, threshHor, threshVer)
      connectedRanges ++= foundRanges
    }

    // Step 6: Create candidate table regions from both approaches
    val gapBasedCandidates = for
      (topRow, bottomRow) <- rowSegments
      (leftCol, rightCol) <- colSegments
    yield
      // Find anchors within this region
      val regionAnchorRows = anchorRows.filter(r => r >= topRow && r <= bottomRow)
      val regionAnchorCols = anchorCols.filter(c => c >= leftCol && c <= rightCol)

      TableRegion(
        topRow, bottomRow,
        leftCol, rightCol,
        regionAnchorRows, regionAnchorCols
      )

    // Combine both sets of candidates
    val allCandidates = gapBasedCandidates ++ connectedRanges

    // Remove duplicate regions
    val uniqueCandidates = removeDuplicateRegions(allCandidates.toList)

    // Filter regions with general criteria
    val filteredCandidates = uniqueCandidates.filter { region =>
      val cellCount = SheetGridUtils.countCellsInRegion(grid, region)
      val contentDensity = cellCount.toDouble / region.area

      // Check minimum size
      val isBigEnough = region.width >= 2 && region.height >= 2

      // Calculate the width-to-height ratio to identify very wide or very tall tables
      val widthToHeightRatio = if region.height > 0 then region.width.toDouble / region.height else 0.0

      // Calculate separate row and column densities for capturing sparse tables
      val rowsCovered = if region.height > 0 then
        anchorRows.count(r => r >= region.topRow && r <= region.bottomRow).toDouble / region.height
      else 0.0

      val colsCovered = if region.width > 0 then
        anchorCols.count(c => c >= region.leftCol && c <= region.rightCol).toDouble / region.width
      else 0.0

      // Special case: if the region is significantly wider than tall, it's likely a row-oriented table
      val isRowDominantTable = widthToHeightRatio > 3.0 && rowsCovered > 0.4
      // Special case: if the region is significantly taller than wide, it's likely a column-oriented table
      val isColumnDominantTable = widthToHeightRatio < 0.33 && colsCovered > 0.4

      // Check for border-defined table
      val hasBorderedCells = hasBorders(grid, region)

      // Verbose logging
      if config.verbose then
        val tableType =
          if isRowDominantTable then "row-dominant"
          else if isColumnDominantTable then "column-dominant"
          else "standard"
        logger.debug(f"Table candidate at (${region.topRow},${region.leftCol}) to (${region.bottomRow},${region.rightCol}): " +
          f"${region.width}x${region.height}, type=$tableType, density=$contentDensity%.2f, " +
          f"row coverage=$rowsCovered%.2f, col coverage=$colsCovered%.2f, ratio=$widthToHeightRatio%.2f, has borders=$hasBorderedCells")

      // Keep the region if it meets our criteria
      isBigEnough &&
        (contentDensity >= 0.1 || isRowDominantTable || isColumnDominantTable || hasBorderedCells)
    }

    // Refine remaining tables
    val refinedRegions = filterLittleBoxes(filteredCandidates, grid)

    // Sort by location
    val sortedRegions = TableDetector.rankBoxesByLocation(refinedRegions)

    // Remove overlapping tables
    val finalRegions = if config.eliminateOverlaps then
      eliminateOverlaps(sortedRegions)
    else
      sortedRegions

    logger.info(s"Table sense detection found ${finalRegions.size} tables")
    finalRegions
  }

  /**
   * Checks if a region has a significant number of bordered cells
   */
  private def hasBorders(grid: SheetGrid, region: TableRegion): Boolean = {
    val cells = grid.cells.filter { case ((r, c), _) =>
      r >= region.topRow && r <= region.bottomRow &&
        c >= region.leftCol && c <= region.rightCol
    }

    val borderedCellCount = cells.count { case (_, cell) =>
      cell.hasTopBorder || cell.hasBottomBorder || cell.hasLeftBorder || cell.hasRightBorder
    }

    // Return true if at least 25% of cells have borders
    cells.nonEmpty && borderedCellCount.toDouble / cells.size >= 0.25
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
   * Remove duplicated regions (highly overlapping)
   */
  private def removeDuplicateRegions(regions: List[TableRegion]): List[TableRegion] = {
    val result = mutable.ListBuffer[TableRegion]()

    // Sort by area (largest first)
    val sortedRegions = regions.sortBy(-_.area)

    for (region <- sortedRegions) {
      val isDuplicate = result.exists { existing =>
        val overlapArea = TableDetector.calculateOverlapArea(existing, region)
        val overlapRatio = overlapArea.toDouble / min(existing.area, region.area)
        overlapRatio > 0.8 // More than 80% overlap is considered duplicate
      }

      if (!isDuplicate) {
        result += region
      }
    }

    result.toList
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