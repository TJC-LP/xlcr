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
    val cells = grid.cells.filter { case ((row, col), _) =>
      row >= region.topRow && row <= region.bottomRow &&
        col >= region.leftCol && col <= region.rightCol
    }
    cells.size

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