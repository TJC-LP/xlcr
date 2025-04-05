package com.tjclp.xlcr
package compression.utils

import com.tjclp.xlcr.compression.models.{CellInfo, SheetGrid, TableRegion}
import com.tjclp.xlcr.compression.utils.CellInfoUtils
import com.tjclp.xlcr.models.excel.SheetData
import org.slf4j.LoggerFactory

/**
 * Utility functions for creating and working with SheetGrid objects
 */
object SheetGridUtils {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Create a SheetGrid from a SheetData
   *
   * @param sheetData The sheet data from the core Excel model
   * @return A SheetGrid suitable for anchor extraction
   */
  def fromSheetData(sheetData: SheetData): SheetGrid = {
    // Convert each cell in the SheetData to a CellInfo
    val cellMap = sheetData.cells.map { cellData =>
      val cellInfo = CellInfoUtils.fromCellData(cellData)
      (cellInfo.row, cellInfo.col) -> cellInfo
    }.toMap

    SheetGrid(
      cells = cellMap,
      rowCount = sheetData.rowCount,
      colCount = sheetData.columnCount
    )
  }

  /**
   * Counts the number of non-empty cells in a table region.
   *
   * @param grid   The sheet grid
   * @param region The table region to analyze
   * @return Count of non-empty cells in the region
   */
  def countCellsInRegion(grid: SheetGrid, region: TableRegion): Int = {
    grid.cells.count { case ((row, col), cell) =>
      row >= region.topRow && row <= region.bottomRow &&
        col >= region.leftCol && col <= region.rightCol &&
        !cell.isEffectivelyEmpty
    }
  }

  /**
   * Gets all cells (empty and non-empty) within a region.
   *
   * @param grid   The sheet grid
   * @param region The table region
   * @return List of cells in the region
   */
  def getCellsInRegion(grid: SheetGrid, region: TableRegion): List[CellInfo] = {
    grid.cells.filter { case ((row, col), _) =>
      row >= region.topRow && row <= region.bottomRow &&
        col >= region.leftCol && col <= region.rightCol
    }.values.toList
  }

  /**
   * Calculates the content diversity in a region.
   * A higher diversity score means a more varied content type mix (text, numbers, dates, etc.)
   *
   * @param grid   The sheet grid
   * @param region The table region to analyze
   * @return A diversity score between 0.0 and 1.0
   */
  def calculateContentDiversity(grid: SheetGrid, region: TableRegion): Double = {
    // Get cells in the region
    val cells = getCellsInRegion(grid, region)

    if (cells.isEmpty) return 0.0

    // Count different content types
    val numericCells = cells.count(_.isNumeric)
    val textCells = cells.count(c => !c.isNumeric && !c.isDate && !c.isEffectivelyEmpty)
    val dateCells = cells.count(_.isDate)
    val formattedCells = cells.count(c => c.hasFillColor || c.isBold || c.hasBottomBorder || c.hasTopBorder)

    // Calculate ratios
    val totalSize = cells.size.toDouble
    val numericRatio = if (totalSize > 0) numericCells / totalSize else 0.0
    val textRatio = if (totalSize > 0) textCells / totalSize else 0.0
    val dateRatio = if (totalSize > 0) dateCells / totalSize else 0.0
    val formattedRatio = if (totalSize > 0) formattedCells / totalSize else 0.0

    // Diversity score - sum of min(ratio, 0.3) for each type
    // This rewards having multiple types but caps the contribution of any one type
    math.min(numericRatio, 0.3) + math.min(textRatio, 0.3) +
      math.min(dateRatio, 0.3) + math.min(formattedRatio, 0.3)
  }

  /**
   * Calculates the density of a table region (non-empty cells / total area).
   *
   * @param grid   The sheet grid
   * @param region The table region to analyze
   * @return Density as a ratio between 0.0 and 1.0
   */
  def calculateDensity(grid: SheetGrid, region: TableRegion): Double = {
    val nonEmptyCellCount = countCellsInRegion(grid, region)
    if (region.area > 0) nonEmptyCellCount.toDouble / region.area else 0.0
  }

  /**
   * Checks if a row is empty (contains only empty cells)
   * For table boundary detection, we need to be strict about emptiness
   */
  def isRowEmpty(grid: SheetGrid, left: Int, right: Int, row: Int): Boolean = {
    // Get all cells in this row within the range
    val cells = (left to right).flatMap(col => grid.cells.get((row, col)))

    // If no cells exist in this range, the row is empty
    if (cells.isEmpty) return true

    // For table detection, we only care if cells are truly empty
    // This ensures that tables with "test" content are properly detected
    val nonEmptyCellCount = cells.count(!_.isEmpty)

    // The row is empty if there are no non-empty cells
    nonEmptyCellCount == 0
  }

  /**
   * Checks if a column is empty (contains only empty cells)
   * For table boundary detection, we need to be strict about emptiness
   */
  def isColEmpty(grid: SheetGrid, top: Int, bottom: Int, col: Int): Boolean = {
    // Get all cells in this column within the range
    val cells = (top to bottom).flatMap(row => grid.cells.get((row, col)))

    // If no cells exist in this range, the column is empty
    if (cells.isEmpty) return true

    // For table detection, we only care if cells are truly empty
    // This ensures that tables with "test" content are properly detected
    val nonEmptyCellCount = cells.count(!_.isEmpty)

    // The column is empty if there are no non-empty cells
    nonEmptyCellCount == 0
  }

  /** Checks if coordinates are in bounds */
  def isInBounds(row: Int, col: Int, height: Int, width: Int): Boolean = {
    row >= 0 && row < height && col >= 0 && col < width
  }

  /**
   * Counts consecutive empty rows from a starting position.
   *
   * @param grid      The sheet grid
   * @param startRow  The row to start checking from
   * @param direction Direction to check (1 for down, -1 for up)
   * @param left      Leftmost column of the range to check
   * @param right     Rightmost column of the range to check
   * @param maxRows   Maximum number of rows to check
   * @return Number of consecutive empty rows
   */
  def countConsecutiveEmptyRows(grid: SheetGrid, startRow: Int, direction: Int,
                                left: Int, right: Int, maxRows: Int): Int = {
    val maxRowIndex = if (direction > 0) grid.rowCount - 1 else 0
    val range = (1 to maxRows).takeWhile { step =>
      val rowToCheck = startRow + (step * direction)
      rowToCheck >= 0 && rowToCheck <= maxRowIndex && isRowEmpty(grid, left, right, rowToCheck)
    }
    range.size
  }

  /**
   * Counts consecutive empty columns from a starting position.
   *
   * @param grid      The sheet grid
   * @param startCol  The column to start checking from
   * @param direction Direction to check (1 for right, -1 for left)
   * @param top       Top row of the range to check
   * @param bottom    Bottom row of the range to check
   * @param maxCols   Maximum number of columns to check
   * @return Number of consecutive empty columns
   */
  def countConsecutiveEmptyCols(grid: SheetGrid, startCol: Int, direction: Int,
                                top: Int, bottom: Int, maxCols: Int): Int = {
    val maxColIndex = if (direction > 0) grid.colCount - 1 else 0
    val range = (1 to maxCols).takeWhile { step =>
      val colToCheck = startCol + (step * direction)
      colToCheck >= 0 && colToCheck <= maxColIndex && isColEmpty(grid, top, bottom, colToCheck)
    }
    range.size
  }

  /**
   * Checks if a row could be considered an anchor row based on its content and formatting
   * An anchor row has significant content, formatting, or structural characteristics
   */
  def isAnchorRow(grid: SheetGrid, row: Int): Boolean = {
    // Get all cells in the row
    val cells = grid.getRow(row)

    if (cells.isEmpty) return false

    // Check for anchor characteristics
    val nonEmptyCells = cells.count(!_.isEffectivelyEmpty)
    val hasBoldOrColor = cells.exists(c => c.isBold || c.hasFillColor)
    val hasBorders = cells.exists(c => c.hasTopBorder || c.hasBottomBorder)
    val hasHighTextDensity = nonEmptyCells > cells.size * 0.3

    // A row is an anchor if it has at least one of these characteristics
    hasBoldOrColor || hasBorders || hasHighTextDensity
  }

  /**
   * Checks if a column could be considered an anchor column based on its content and formatting
   * An anchor column has significant content, formatting, or structural characteristics
   */
  def isAnchorColumn(grid: SheetGrid, col: Int): Boolean = {
    // Get all cells in the column
    val cells = grid.getCol(col)

    if (cells.isEmpty) return false

    // Check for anchor characteristics
    val nonEmptyCells = cells.count(!_.isEffectivelyEmpty)
    val hasBoldOrColor = cells.exists(c => c.isBold || c.hasFillColor)
    val hasBorders = cells.exists(c => c.hasLeftBorder || c.hasRightBorder)
    val hasHighTextDensity = nonEmptyCells > cells.size * 0.3

    // A column is an anchor if it has at least one of these characteristics
    hasBoldOrColor || hasBorders || hasHighTextDensity
  }

  /**
   * Checks if a region has uniform content (all non-empty cells have the same value)
   * This is useful for identifying test/example tables or grids with repeating values.
   *
   * @param grid   The sheet grid
   * @param region The table region to analyze
   * @return True if all non-empty cells have the same value
   */
  def hasUniformContent(grid: SheetGrid, region: TableRegion): Boolean = {
    // Get all non-empty cells in the region
    val nonEmptyCells = grid.cells.filter { case ((row, col), cell) =>
      row >= region.topRow && row <= region.bottomRow &&
        col >= region.leftCol && col <= region.rightCol &&
        !cell.isEmpty // Note: we check for actually empty, not "effectively empty"
    }

    if (nonEmptyCells.isEmpty) return false

    // If we have just one cell, it's technically uniform
    if (nonEmptyCells.size == 1) return true

    // Check if all cells have the same value
    val values = nonEmptyCells.values.map(_.value).toSet

    // Consider it uniform if there's only one distinct value
    // or if all values are very similar (e.g., "test", "test ", "Test")
    if (values.size == 1) return true

    // Allow for minor variations in case/whitespace
    val normalizedValues = values.map(_.trim.toLowerCase).filter(_.nonEmpty)
    normalizedValues.size == 1
  }
}