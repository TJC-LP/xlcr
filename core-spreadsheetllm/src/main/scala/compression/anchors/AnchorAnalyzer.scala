package com.tjclp.xlcr
package compression
package anchors

import org.slf4j.LoggerFactory

import compression.AnchorExtractor.Dimension
import compression.models.{ CellInfo, SheetGrid }
import compression.utils.CellInfoUtils

/**
 * AnchorAnalyzer identifies structural anchors in a spreadsheet based on heterogeneity and
 * structural importance of rows and columns.
 */
object AnchorAnalyzer {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Identifies which rows and columns are structural anchors in the sheet based on heterogeneity
   * and formatting cues.
   *
   * @param grid
   *   The sheet grid to analyze
   * @return
   *   Set of row and column indices identified as anchors
   */
  def identifyAnchors(grid: SheetGrid): (Set[Int], Set[Int]) = {
    // Identify anchors for rows and columns using the same logic
    val anchorRows = identifyAnchorsForDimension(grid, Dimension.Row)
    val anchorCols = identifyAnchorsForDimension(grid, Dimension.Column)

    logger.info(s"Identified ${anchorRows.size} anchor rows and ${anchorCols.size} anchor columns")
    (anchorRows, anchorCols)
  }

  /**
   * Identifies anchors for a specific dimension (rows or columns).
   */
  private def identifyAnchorsForDimension(grid: SheetGrid, dim: Dimension): Set[Int] = {
    val count = grid.getDimCount(dim)
    (0 until count).filter { idx =>
      val cells = grid.getCells(dim, idx)
      isAnchor(cells, idx, grid, dim)
    }.toSet
  }

  /**
   * Determines if a row or column should be considered an anchor based on heterogeneity and
   * structural importance.
   *
   * Enhanced with additional border and formatting checks based on the SheetCompressor framework.
   */
  private def isAnchor(cells: Seq[CellInfo], idx: Int, grid: SheetGrid, dim: Dimension): Boolean = {
    if (cells.isEmpty) {
      return false
    }

    // Check for format cues that indicate a header or important row/column
    val hasBoldCells = cells.exists(_.isBold)
    val hasFormulas  = cells.exists(_.isFormula)

    // Check for border patterns that might indicate structure
    val borderCheckThreshold = if (cells.nonEmpty) cells.size / 3.0 else 0.0
    val hasBorders = dim match {
      case Dimension.Row =>
        cells.count(c => c.hasTopBorder || c.hasBottomBorder) > borderCheckThreshold
      case Dimension.Column =>
        cells.count(c => c.hasLeftBorder || c.hasRightBorder) > borderCheckThreshold
    }

    // Check for fill color patterns that might indicate structure
    val colorCheckThreshold = if (cells.nonEmpty) cells.size / 4.0 else 0.0
    val hasFillColors       = cells.count(_.hasFillColor) > colorCheckThreshold

    // Filter out filler content and truly empty cells - only consider meaningful content
    val meaningfulCells = cells.filterNot(_.isEffectivelyEmpty)

    // If all cells are filler or empty, this is not an anchor
    if (meaningfulCells.isEmpty) {
      return false
    }

    // Check for header-like characteristics
    val headerCheckThreshold = if (meaningfulCells.nonEmpty) meaningfulCells.size / 3.0 else 0.0
    val hasHeaderCharacteristics = dim match {
      case Dimension.Row =>
        // Headers typically have more letters than numbers and may contain special characters
        (meaningfulCells.count(c =>
          c.alphabetRatio > c.numberRatio && c.textLength > 0
        ) > headerCheckThreshold) ||
        (meaningfulCells.count(c => c.spCharRatio > 0) > 1)
      case Dimension.Column =>
        // Column headers often have similar characteristics to row headers
        meaningfulCells.count(c =>
          c.alphabetRatio > c.numberRatio && c.textLength > 0
        ) > headerCheckThreshold
    }

    // Check for type heterogeneity - mix of text, numbers, dates, etc.
    val hasNumbers = meaningfulCells.exists(_.isNumeric)
    val hasText    = meaningfulCells.exists(c => !c.isNumeric && !c.isDate)
    val hasDates   = meaningfulCells.exists(_.isDate)
    val isHeterogeneous =
      (hasNumbers && hasText) || (hasNumbers && hasDates) || (hasText && hasDates)

    // Check if this row/column is different from its neighbors
    val count         = grid.getDimCount(dim)
    val isFirstOrLast = idx == 0 || idx == count - 1
    val isDifferentFromNeighbors =
      if (idx > 0 && idx < count - 1) {
        val prevCells = grid.getCells(dim, idx - 1)
        val nextCells = grid.getCells(dim, idx + 1)

        // Check if data type pattern changes to/from this row/column
        val prevPattern    = CellInfoUtils.typePattern(prevCells)
        val currentPattern = CellInfoUtils.typePattern(cells)
        val nextPattern    = CellInfoUtils.typePattern(nextCells)

        // Check if formatting patterns change
        val prevFormatting    = CellInfoUtils.formatPattern(prevCells)
        val currentFormatting = CellInfoUtils.formatPattern(cells)
        val nextFormatting    = CellInfoUtils.formatPattern(nextCells)

        prevPattern != currentPattern || currentPattern != nextPattern ||
        prevFormatting != currentFormatting || currentFormatting != nextFormatting
      } else {
        true // First or last row/column is automatically different
      }

    // A row/column is an anchor if it has formatting cues, borders, colors,
    // header characteristics, heterogeneous content, or is different from neighbors
    val result = hasBoldCells || hasFormulas || hasBorders || hasFillColors ||
      hasHeaderCharacteristics || isHeterogeneous || isDifferentFromNeighbors || isFirstOrLast

    // Optional: Add debug logging here if needed
    // if (result) logger.debug(s"Anchor detected at dim=$dim, idx=$idx")

    result
  }

  /**
   * Expands the set of anchor rows and columns by including neighbors up to the specified
   * threshold.
   *
   * @param anchorRows
   *   Set of row indices identified as anchors
   * @param anchorCols
   *   Set of column indices identified as anchors
   * @param rowCount
   *   Total number of rows in the sheet
   * @param colCount
   *   Total number of columns in the sheet
   * @param threshold
   *   How many neighboring rows/cols to include around each anchor
   * @return
   *   Expanded sets of row and column indices to keep
   */
  def expandAnchors(
    anchorRows: Set[Int],
    anchorCols: Set[Int],
    rowCount: Int,
    colCount: Int,
    threshold: Int
  ): (Set[Int], Set[Int]) = {
    // Use common expansion logic for both dimensions
    val expandedRows = expandDimension(anchorRows, rowCount, threshold)
    val expandedCols = expandDimension(anchorCols, colCount, threshold)

    logger.info(
      s"Expanded to ${expandedRows.size} rows and ${expandedCols.size} columns (threshold=$threshold)"
    )
    (expandedRows, expandedCols)
  }

  /**
   * Expands a set of indices by including neighbors within threshold.
   */
  private def expandDimension(anchors: Set[Int], count: Int, threshold: Int): Set[Int] = {
    // Ensure threshold is non-negative
    val validThreshold = math.max(0, threshold)

    anchors.flatMap { anchor =>
      val start = math.max(0, anchor - validThreshold)
      val end   = math.min(count - 1, anchor + validThreshold)
      if (start <= end) start to end else Seq.empty
    }
  }
}
