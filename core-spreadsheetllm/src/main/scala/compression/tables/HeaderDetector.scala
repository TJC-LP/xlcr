package com.tjclp.xlcr
package compression.tables

import scala.collection.mutable

import org.slf4j.LoggerFactory

import compression.models.{ SheetGrid, TableRegion }

/**
 * HeaderDetector provides methods for detecting and analyzing header rows and columns in a
 * spreadsheet table, based on formatting cues and content analysis.
 */
object HeaderDetector {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Enhanced header detection that implements the full logic from HeaderReco.cs. Identifies
   * multiple header types including up headers, left headers, and multi-level headers.
   */
  def enhancedHeaderDetection(grid: SheetGrid, region: TableRegion): (Set[Int], Set[Int]) = {
    logger.info(s"Detecting headers for region: $region")

    // 1. Detect up headers (column headers)
    val upHeaders = detectUpHeaders(grid, region)

    // 2. Detect left headers (row headers)
    val leftHeaders = detectLeftHeaders(grid, region)

    logger.info(
      s"Detected ${upHeaders.size} row headers (up) and ${leftHeaders.size} column headers (left)"
    )
    (upHeaders, leftHeaders)
  }

  /**
   * Detect up headers (column headers) at the top of a table region
   */
  private def detectUpHeaders(grid: SheetGrid, region: TableRegion): Set[Int] = {
    val headerRows = mutable.Set[Int]()

    // Only check the top 3 rows of the region for headers
    val rowsToCheck = (region.topRow to math.min(region.topRow + 2, region.bottomRow)).toSet

    for (row <- rowsToCheck)
      if (isHeaderRow(grid, row, region.leftCol, region.rightCol)) {
        headerRows += row
      }

    headerRows.toSet
  }

  /**
   * Check if a row appears to be a header row based on formatting and content This implements
   * similar logic to IsHeaderUpSimple in the C# HeaderReco class
   */
  def isHeaderRow(grid: SheetGrid, row: Int, leftCol: Int, rightCol: Int): Boolean = {
    // Get all cells in the row within the column range
    val cells = (leftCol to rightCol).flatMap(col => grid.cells.get((row, col)))

    if (cells.isEmpty) return false

    // Skip rows with only one column (too narrow to be a reliable header)
    if (rightCol - leftCol == 0) return false

    // Filter out cells that are actually empty
    val meaningfulCells = cells.filterNot(_.isEmpty)

    // If too few meaningful cells, not a header
    // This matches the C# check for sumContentExist.SubmatrixSum(header) <= 4
    val meaningfulRatio = if (cells.nonEmpty) meaningfulCells.size.toDouble / cells.size else 0.0
    if (meaningfulCells.size <= 4 && meaningfulRatio <= 0.5) return false

    // Check if cells are too uniform - similar to C# TextDistinctCount check
    // For larger areas, we should have more distinct values
    val distinctValues = meaningfulCells.map(_.value).toSet
    if (
      (cells.size > 4 && distinctValues.size <= 2) ||
      (cells.size > 3 && distinctValues.size < 2)
    ) {
      return false
    }

    // Calculate header score components
    val totalCells = cells.size

    // Calculate content ratios - looking for text-dominated content
    val alphabetDominatedCells = meaningfulCells.count { cell =>
      cell.alphabetRatio >= cell.numberRatio && cell.alphabetRatio > 0
    }

    // Calculate formatting indicators
    val boldCells   = meaningfulCells.count(_.isBold)
    val borderCells = meaningfulCells.count(c => c.hasTopBorder || c.hasBottomBorder)
    val colorCells  = meaningfulCells.count(_.hasFillColor)

    // Similar to C# HeaderRate calculation
    val formatScore = {
      val boldRatio =
        if (meaningfulCells.nonEmpty) boldCells.toDouble / meaningfulCells.size else 0.0
      val borderRatio =
        if (meaningfulCells.nonEmpty) borderCells.toDouble / meaningfulCells.size else 0.0
      val colorRatio =
        if (meaningfulCells.nonEmpty) colorCells.toDouble / meaningfulCells.size else 0.0

      // Weight the formatting signals similar to the C# implementation
      boldRatio * 0.4 + borderRatio * 0.3 + colorRatio * 0.3
    }

    // Calculate content-based score - similar to C# implementation checks
    val contentScore = {
      val alphabetRatio = if (meaningfulCells.nonEmpty)
        alphabetDominatedCells.toDouble / meaningfulCells.size
      else 0.0

      // Check for header keywords (similar to C# TextDistinctCount check)
      val hasHeaderKeywords = meaningfulCells.exists { cell =>
        val text = cell.value.toLowerCase
        text.contains("total") || text.contains("sum") || text.contains("average") ||
        text.contains("year") || text.contains("month") || text.contains("quarter") ||
        text.contains("id") || text.contains("name") || text.contains("date") ||
        text.matches("q[1-4]") || text.matches("fy\\d{4}") // Quarter/fiscal year patterns
      }

      // Weight the content signals
      alphabetRatio * 0.7 + (if (hasHeaderKeywords) 0.3 else 0.0)
    }

    // Check the right side of the row - similar to rightRegionOfHeader in C# code
    val rightSideCol        = math.max(leftCol + 3, (leftCol + rightCol) / 2)
    val rightSideCells      = (rightSideCol to rightCol).flatMap(col => grid.cells.get((row, col)))
    val rightSideMeaningful = rightSideCells.filterNot(_.isEmpty)

    val rightSideFormatted = rightSideMeaningful.count(c =>
      c.isBold || c.hasFillColor ||
        c.hasTopBorder || c.hasBottomBorder
    )
    val rightSideFormatRatio = if (rightSideMeaningful.nonEmpty)
      rightSideFormatted.toDouble / rightSideMeaningful.size
    else 0.0

    // Combine scores - similar to the C# implementation's final checks
    // ContentExistValueDensity > 2 * 0.3 && HeaderRate > 0.4 && HeaderRate(rightRegion) > 0.3
    val contentDensity = meaningfulRatio
    val isHeader = contentDensity > 0.3 &&
      (formatScore > 0.4 || contentScore > 0.4) &&
      rightSideFormatRatio > 0.3

    if (isHeader) {
      logger.debug(
        f"Detected header row at $row (cols $leftCol-$rightCol): format=$formatScore%.2f, content=$contentScore%.2f, density=$contentDensity%.2f"
      )
    }

    isHeader
  }

  /**
   * Detect left headers (row headers) at the left side of a table region
   */
  private def detectLeftHeaders(grid: SheetGrid, region: TableRegion): Set[Int] = {
    val headerCols = mutable.Set[Int]()

    // Only check the leftmost 2 columns for headers
    val colsToCheck = (region.leftCol to math.min(region.leftCol + 1, region.rightCol)).toSet

    for (col <- colsToCheck)
      if (isHeaderColumn(grid, col, region.topRow, region.bottomRow)) {
        headerCols += col
      }

    headerCols.toSet
  }

  /**
   * Check if a column appears to be a header column This implements similar logic to
   * IsHeaderLeftSimple in the C# HeaderReco class
   */
  def isHeaderColumn(grid: SheetGrid, col: Int, topRow: Int, bottomRow: Int): Boolean = {
    // Get all cells in the column within the row range
    val cells = (topRow to bottomRow).flatMap(row => grid.cells.get((row, col)))

    if (cells.isEmpty) return false

    // Skip columns with only one row (too short to be a reliable header)
    if (bottomRow - topRow == 0) return false

    // Special case for very short columns - if it's only 2 rows, we need stronger signals
    if (bottomRow - topRow == 1) {
      // Filter out cells that are actually empty
      val meaningfulCells = cells.filterNot(_.isEmpty)

      // Calculate simple header score for short columns
      val boldCells          = meaningfulCells.count(_.isBold)
      val borderCells        = meaningfulCells.count(c => c.hasLeftBorder || c.hasRightBorder)
      val colorCells         = meaningfulCells.count(_.hasFillColor)
      val textDominatedCells = meaningfulCells.count(c => c.alphabetRatio > c.numberRatio)

      val headerScore = if (meaningfulCells.nonEmpty) {
        (boldCells * 0.4 + borderCells * 0.3 + colorCells * 0.3 + textDominatedCells * 0.3) / meaningfulCells.size
      } else 0.0

      // For very short columns, require a higher score threshold
      return headerScore >= 0.5
    }

    // Filter out cells that are actually empty
    val meaningfulCells = cells.filterNot(_.isEmpty)

    // If too few meaningful cells, not a header
    // This matches the C# check for sumContentExist.SubmatrixSum(header) <= 4
    val meaningfulRatio = if (cells.nonEmpty) meaningfulCells.size.toDouble / cells.size else 0.0
    if (meaningfulCells.size <= 4 && meaningfulRatio <= 0.5) return false

    // Check if cells are too uniform - similar to C# TextDistinctCount check
    // For larger areas, we should have more distinct values
    val distinctValues = meaningfulCells.map(_.value).toSet
    if (
      (cells.size > 4 && distinctValues.size <= 2) ||
      (cells.size > 3 && distinctValues.size < 2)
    ) {
      return false
    }

    // Calculate content ratios - looking for text-dominated content
    val alphabetDominatedCells = meaningfulCells.count { cell =>
      cell.alphabetRatio >= cell.numberRatio && cell.alphabetRatio > 0
    }

    // Calculate formatting indicators
    val boldCells   = meaningfulCells.count(_.isBold)
    val borderCells = meaningfulCells.count(c => c.hasLeftBorder || c.hasRightBorder)
    val colorCells  = meaningfulCells.count(_.hasFillColor)

    // Similar to C# HeaderRate calculation
    val formatScore = {
      val boldRatio =
        if (meaningfulCells.nonEmpty) boldCells.toDouble / meaningfulCells.size else 0.0
      val borderRatio =
        if (meaningfulCells.nonEmpty) borderCells.toDouble / meaningfulCells.size else 0.0
      val colorRatio =
        if (meaningfulCells.nonEmpty) colorCells.toDouble / meaningfulCells.size else 0.0

      // Weight the formatting signals similar to the C# implementation
      boldRatio * 0.4 + borderRatio * 0.3 + colorRatio * 0.3
    }

    // Calculate content-based score
    val contentScore = {
      val alphabetRatio = if (meaningfulCells.nonEmpty)
        alphabetDominatedCells.toDouble / meaningfulCells.size
      else 0.0

      // Check for header keywords
      val hasHeaderKeywords = meaningfulCells.exists { cell =>
        val text = cell.value.toLowerCase
        text.contains("total") || text.contains("sum") || text.contains("average") ||
        text.contains("year") || text.contains("month") || text.contains("quarter") ||
        text.contains("id") || text.contains("name") || text.contains("date")
      }

      // Weight the content signals
      alphabetRatio * 0.7 + (if (hasHeaderKeywords) 0.3 else 0.0)
    }

    // Check the upper part of the column - similar to upRegionOfHeader in C# code
    val upperRowIdx     = math.min(bottomRow, topRow + 3)
    val upperCells      = (topRow to upperRowIdx).flatMap(row => grid.cells.get((row, col)))
    val upperMeaningful = upperCells.filterNot(_.isEmpty)

    val upperFormatted = upperMeaningful.count(c =>
      c.isBold || c.hasFillColor ||
        c.hasLeftBorder || c.hasRightBorder
    )
    val upperFormatRatio = if (upperMeaningful.nonEmpty)
      upperFormatted.toDouble / upperMeaningful.size
    else 0.0

    // Combine scores - similar to the C# implementation's final checks
    val contentDensity = meaningfulRatio
    val isHeader = contentDensity > 0.3 &&
      (formatScore > 0.4 || contentScore > 0.4) &&
      upperFormatRatio > 0.3

    if (isHeader) {
      logger.debug(
        f"Detected header column at $col (rows $topRow-$bottomRow): format=$formatScore%.2f, content=$contentScore%.2f, density=$contentDensity%.2f"
      )
    }

    isHeader
  }
}
