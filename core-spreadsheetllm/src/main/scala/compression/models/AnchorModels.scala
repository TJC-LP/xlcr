package com.tjclp.xlcr
package compression.models

import compression.AnchorExtractor.Dimension
import models.excel.CellData

/**
 * Cell information used for anchor analysis.
 *
 * @param row                The 0-based row index
 * @param col                The 0-based column index
 * @param value              The cell content as a string
 * @param isBold             Whether the cell has bold formatting
 * @param isFormula          Whether the cell contains a formula
 * @param isNumeric          Whether the cell contains numeric content
 * @param isDate             Whether the cell contains a date
 * @param isEmpty            Whether the cell is empty
 * @param hasTopBorder       Whether the cell has a top border
 * @param hasBottomBorder    Whether the cell has a bottom border
 * @param hasLeftBorder      Whether the cell has a left border
 * @param hasRightBorder     Whether the cell has a right border 
 * @param hasFillColor       Whether the cell has background fill color
 * @param textLength         The length of the cell text (for ratio calculations)
 * @param alphabetRatio      The ratio of alphabet characters to total length
 * @param numberRatio        The ratio of numeric characters to total length
 * @param spCharRatio        The ratio of special characters to total length
 * @param numberFormatString The Excel number format string if available
 * @param originalRow        The original row index before remapping (for debugging)
 * @param originalCol        The original column index before remapping (for debugging)
 * @param cellData           The original CellData that this cell was derived from
 */
case class CellInfo(
                     row: Int,
                     col: Int,
                     value: String,
                     isBold: Boolean = false,
                     isFormula: Boolean = false,
                     isNumeric: Boolean = false,
                     isDate: Boolean = false,
                     isEmpty: Boolean = false,
                     hasTopBorder: Boolean = false,
                     hasBottomBorder: Boolean = false,
                     hasLeftBorder: Boolean = false,
                     hasRightBorder: Boolean = false,
                     hasFillColor: Boolean = false,
                     textLength: Int = 0,
                     alphabetRatio: Double = 0.0,
                     numberRatio: Double = 0.0,
                     spCharRatio: Double = 0.0,
                     numberFormatString: Option[String] = None,
                     originalRow: Option[Int] = None,
                     originalCol: Option[Int] = None,
                     cellData: Option[CellData] = None
                   )

/**
 * Information about a table detected in the grid
 *
 * @param topRow     The top row index of the table
 * @param bottomRow  The bottom row index of the table
 * @param leftCol    The leftmost column index of the table
 * @param rightCol   The rightmost column index of the table
 * @param anchorRows Set of row indices that are anchors within this table
 * @param anchorCols Set of column indices that are anchors within this table
 */
case class TableRegion(
                        topRow: Int,
                        bottomRow: Int,
                        leftCol: Int,
                        rightCol: Int,
                        anchorRows: Set[Int],
                        anchorCols: Set[Int]
                      ):
  def area: Int = width * height

  def width: Int = rightCol - leftCol + 1

  def height: Int = bottomRow - topRow + 1

  /** Get all rows in this table region */
  def allRows: Set[Int] = (topRow to bottomRow).toSet

  /** Get all columns in this table region */
  def allCols: Set[Int] = (leftCol to rightCol).toSet

/**
 * Represents a spreadsheet grid for anchor extraction.
 */
case class SheetGrid(
                      cells: Map[(Int, Int), CellInfo],
                      rowCount: Int,
                      colCount: Int
                    ):
  /**
   * Get all cells in a specific row or column based on dimension.
   */
  def getCells(dim: Dimension, index: Int): Seq[CellInfo] = dim match
    case Dimension.Row => getRow(index)
    case Dimension.Column => getCol(index)

  /**
   * Get all cells in a specific row.
   */
  def getRow(row: Int): Seq[CellInfo] =
    (0 until colCount).flatMap(col => cells.get((row, col)))

  /**
   * Get all cells in a specific column.
   */
  def getCol(col: Int): Seq[CellInfo] =
    (0 until rowCount).flatMap(row => cells.get((row, col)))

  /**
   * Get dimension count (rowCount or colCount).
   */
  def getDimCount(dim: Dimension): Int = dim match
    case Dimension.Row => rowCount
    case Dimension.Column => colCount

  /**
   * Filter the grid to only include cells in the specified rows and columns.
   */
  def filterToKeep(rowsToKeep: Set[Int], colsToKeep: Set[Int]): SheetGrid =
    val filteredCells = cells.filter { case ((r, c), _) =>
      rowsToKeep.contains(r) && colsToKeep.contains(c)
    }
    SheetGrid(filteredCells, rowCount, colCount)

  /**
   * Remap coordinates to close gaps after pruning.
   * This maintains logical structure while creating a more compact representation.
   */
  def remapCoordinates(): SheetGrid =
    // Create new row and column indices that are continuous
    val sortedRows = cells.keys.map(_._1).toSeq.distinct.sorted
    val sortedCols = cells.keys.map(_._2).toSeq.distinct.sorted

    val rowMap = sortedRows.zipWithIndex.toMap
    val colMap = sortedCols.zipWithIndex.toMap

    // Remap each cell to its new coordinates
    val remappedCells = cells.map { case ((oldRow, oldCol), cellInfo) =>
      val newRow = rowMap(oldRow)
      val newCol = colMap(oldCol)

      // Store original row/col in the cell info for later debugging
      val originalRow = cellInfo.originalRow.getOrElse(oldRow)
      val originalCol = cellInfo.originalCol.getOrElse(oldCol)

      // Important: We need to update both the map key AND the CellInfo's internal coordinates
      (newRow, newCol) -> cellInfo.copy(
        row = newRow,
        col = newCol,
        originalRow = Some(originalRow),
        originalCol = Some(originalCol)
      )
    }

    SheetGrid(remappedCells, sortedRows.size, sortedCols.size)