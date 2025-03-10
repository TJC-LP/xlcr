package com.tjclp.xlcr
package compression.utils

import compression.models.{CellInfo, SheetGrid, TableRegion}
import compression.utils.CellInfoUtils
import models.excel.SheetData

import org.slf4j.LoggerFactory

/**
 * Utility functions for creating and working with SheetGrid objects
 */
object SheetGridUtils:
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Create a SheetGrid from a SheetData
   *
   * @param sheetData The sheet data from the core Excel model
   * @return A SheetGrid suitable for anchor extraction
   */
  def fromSheetData(sheetData: SheetData): SheetGrid =
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

  /**
   * Counts the number of non-empty cells in a table region.
   *
   * @param grid   The sheet grid
   * @param region The table region to analyze
   * @return Count of non-empty cells in the region
   */
  def countCellsInRegion(grid: SheetGrid, region: TableRegion): Int =
    grid.cells.count { case ((row, col), cell) =>
      row >= region.topRow && row <= region.bottomRow &&
        col >= region.leftCol && col <= region.rightCol &&
        !cell.isEmpty
    }

  /**
   * Gets all cells (empty and non-empty) within a region.
   *
   * @param grid   The sheet grid
   * @param region The table region
   * @return List of cells in the region
   */
  def getCellsInRegion(grid: SheetGrid, region: TableRegion): List[CellInfo] =
    grid.cells.filter { case ((row, col), _) =>
      row >= region.topRow && row <= region.bottomRow &&
        col >= region.leftCol && col <= region.rightCol
    }.values.toList
    
  /**
   * Calculates the content diversity in a region.
   * A higher diversity score means a more varied content type mix (text, numbers, dates, etc.)
   *
   * @param grid   The sheet grid
   * @param region The table region to analyze
   * @return A diversity score between 0.0 and 1.0
   */
  def calculateContentDiversity(grid: SheetGrid, region: TableRegion): Double =
    // Get cells in the region
    val cells = getCellsInRegion(grid, region)
    
    if cells.isEmpty then return 0.0
    
    // Count different content types
    val numericCells = cells.count(_.isNumeric)
    val textCells = cells.count(c => !c.isNumeric && !c.isDate && !c.isEmpty)
    val dateCells = cells.count(_.isDate)
    val formattedCells = cells.count(c => c.hasFillColor || c.isBold || c.hasBottomBorder || c.hasTopBorder)
    
    // Calculate ratios
    val numericRatio = numericCells.toDouble / cells.size
    val textRatio = textCells.toDouble / cells.size
    val dateRatio = dateCells.toDouble / cells.size
    val formattedRatio = formattedCells.toDouble / cells.size
    
    // Diversity score - sum of min(ratio, 0.3) for each type
    // This rewards having multiple types but caps the contribution of any one type
    math.min(numericRatio, 0.3) + math.min(textRatio, 0.3) + 
    math.min(dateRatio, 0.3) + math.min(formattedRatio, 0.3)

  /**
   * Calculates the density of a table region (non-empty cells / total area).
   *
   * @param grid   The sheet grid
   * @param region The table region to analyze
   * @return Density as a ratio between 0.0 and 1.0
   */
  def calculateDensity(grid: SheetGrid, region: TableRegion): Double =
    val nonEmptyCellCount = countCellsInRegion(grid, region)
    if region.area > 0 then nonEmptyCellCount.toDouble / region.area else 0.0

  /** Checks if a row is empty */
  def isRowEmpty(grid: SheetGrid, left: Int, right: Int, row: Int): Boolean = {
    !(left to right).exists(col =>
      grid.cells.get((row, col)).exists(!_.isEmpty)
    )
  }

  /** Checks if a column is empty */
  def isColEmpty(grid: SheetGrid, top: Int, bottom: Int, col: Int): Boolean = {
    !(top to bottom).exists(row =>
      grid.cells.get((row, col)).exists(!_.isEmpty)
    )
  }

  /** Checks if coordinates are in bounds */
  def isInBounds(row: Int, col: Int, height: Int, width: Int): Boolean = {
    row >= 0 && row < height && col >= 0 && col < width
  }
  
  /**
   * Counts consecutive empty rows from a starting position.
   *
   * @param grid     The sheet grid
   * @param startRow The row to start checking from
   * @param direction Direction to check (1 for down, -1 for up)
   * @param left     Leftmost column of the range to check
   * @param right    Rightmost column of the range to check
   * @param maxRows  Maximum number of rows to check
   * @return Number of consecutive empty rows
   */
  def countConsecutiveEmptyRows(grid: SheetGrid, startRow: Int, direction: Int, 
                              left: Int, right: Int, maxRows: Int): Int =
    val maxRowIndex = if direction > 0 then grid.rowCount - 1 else 0
    val range = (1 to maxRows).takeWhile { step =>
      val rowToCheck = startRow + (step * direction)
      rowToCheck >= 0 && rowToCheck <= maxRowIndex && isRowEmpty(grid, left, right, rowToCheck)
    }
    range.size
    
  /**
   * Counts consecutive empty columns from a starting position.
   *
   * @param grid     The sheet grid
   * @param startCol The column to start checking from
   * @param direction Direction to check (1 for right, -1 for left)
   * @param top      Top row of the range to check
   * @param bottom   Bottom row of the range to check
   * @param maxCols  Maximum number of columns to check
   * @return Number of consecutive empty columns
   */
  def countConsecutiveEmptyCols(grid: SheetGrid, startCol: Int, direction: Int, 
                              top: Int, bottom: Int, maxCols: Int): Int =
    val maxColIndex = if direction > 0 then grid.colCount - 1 else 0
    val range = (1 to maxCols).takeWhile { step =>
      val colToCheck = startCol + (step * direction)
      colToCheck >= 0 && colToCheck <= maxColIndex && isColEmpty(grid, top, bottom, colToCheck)
    }
    range.size