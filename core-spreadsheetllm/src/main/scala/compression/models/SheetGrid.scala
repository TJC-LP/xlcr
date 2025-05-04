package com.tjclp.xlcr
package compression.models

import compression.AnchorExtractor.Dimension

import scala.collection.mutable

/** Represents a spreadsheet grid for anchor extraction.
  */
case class SheetGrid(
    cells: Map[(Int, Int), CellInfo],
    rowCount: Int,
    colCount: Int
) {

  /** Get all cells in a specific row or column based on dimension.
    */
  def getCells(dim: Dimension, index: Int): Seq[CellInfo] = {
    dim match {
      case Dimension.Row    => getRow(index)
      case Dimension.Column => getCol(index)
    }
  }

  /** Get all cells in a specific row.
    */
  def getRow(row: Int): Seq[CellInfo] = {
    // Ensure row index is valid
    if (row >= 0 && row < rowCount) {
      (0 until colCount).flatMap(col => cells.get((row, col)))
    } else {
      Seq.empty
    }
  }

  /** Get all cells in a specific column.
    */
  def getCol(col: Int): Seq[CellInfo] = {
    // Ensure col index is valid
    if (col >= 0 && col < colCount) {
      (0 until rowCount).flatMap(row => cells.get((row, col)))
    } else {
      Seq.empty
    }
  }

  /** Get dimension count (rowCount or colCount).
    */
  def getDimCount(dim: Dimension): Int = {
    dim match {
      case Dimension.Row    => rowCount
      case Dimension.Column => colCount
    }
  }

  /** Filter the grid to only include cells in the specified rows and columns.
    * Preserves original row/col counts.
    */
  def filterToKeep(rowsToKeep: Set[Int], colsToKeep: Set[Int]): SheetGrid = {
    val filteredCells = cells.filter { case ((r, c), _) =>
      rowsToKeep.contains(r) && colsToKeep.contains(c)
    }
    // Return new grid with filtered cells but original dimensions
    SheetGrid(filteredCells, rowCount, colCount)
  }

  /** Remap coordinates to close gaps after pruning.
    * This maintains logical structure while creating a more compact representation.
    * Updates the rowCount and colCount of the returned grid.
    */
  def remapCoordinates(): SheetGrid = {
    // Get the unique, sorted row and column indices present in the filtered cells
    val presentRows = cells.keys.map(_._1).toSeq.distinct.sorted
    val presentCols = cells.keys.map(_._2).toSeq.distinct.sorted

    // Create mapping from old index to new compact index
    val rowMap = presentRows.zipWithIndex.toMap
    val colMap = presentCols.zipWithIndex.toMap

    // Remap each cell to its new coordinates
    val remappedCells = cells.map { case ((oldRow, oldCol), cellInfo) =>
      val newRow = rowMap(oldRow)
      val newCol = colMap(oldCol)

      // Store original row/col in the cell info if not already present
      val originalRow = cellInfo.originalRow.orElse(Some(oldRow))
      val originalCol = cellInfo.originalCol.orElse(Some(oldCol))

      // Important: We need to update both the map key AND the CellInfo's internal coordinates
      (newRow, newCol) -> cellInfo.copy(
        row = newRow,
        col = newCol,
        originalRow = originalRow,
        originalCol = originalCol
      )
    }

    // Return new grid with remapped cells and updated dimensions
    SheetGrid(remappedCells, presentRows.size, presentCols.size)
  }

  /** Checks if the given cell coordinates are within the valid bounds of the grid
    */
  def isInBounds(row: Int, col: Int): Boolean =
    row >= 0 && row < rowCount && col >= 0 && col < colCount

  /** Extracts formula references from the grid
    * @return Map from cell coordinates to the cells they reference through formulas
    */
  def extractFormulaReferences(): Map[(Int, Int), Set[(Int, Int)]] = {
    cells
      .filter { case (_, cell) => cell.isFormula }
      .flatMap { case (coords, cell) =>
        cell.cellData
          .flatMap(_.formula)
          .map { formula =>
            coords -> parseFormulaReferences(formula)
          }
      }
  }

  /** Parse an Excel formula to extract all cell references
    * @param formula The Excel formula as a string (e.g. "=SUM(A1:B2)")
    * @return Set of cell coordinates referenced by the formula
    */
  private def parseFormulaReferences(formula: String): Set[(Int, Int)] = {
    // A simple regex-based parser for demonstration
    // A production parser would need to handle complex Excel formula syntax
    val cellRefPattern = """([A-Z]+)(\d+)""".r
    val refs = mutable.Set[(Int, Int)]()

    // Extract single cell references like A1, B2, etc.
    cellRefPattern.findAllMatchIn(formula).foreach { matched =>
      try {
        val col = matched.group(1)
        val rowStr = matched.group(2)
        val colIndex = colNameToIndex(col)
        val rowIndex = rowStr.toInt - 1 // Convert from 1-based to 0-based

        // Check bounds before adding
        if (isInBounds(rowIndex, colIndex)) {
          refs.add((rowIndex, colIndex))
        }
      } catch {
        case e: NumberFormatException =>
        // Log error or ignore invalid reference
        // logger.warn(s"Could not parse row number from reference in formula: $formula", e)
      }
    }

    refs.toSet
  }

  /** Convert an Excel column name to a 0-based index (A->0, B->1, Z->25, AA->26, etc.)
    */
  private def colNameToIndex(colName: String): Int = {
    // Input validation
    if (
      colName == null || colName.isEmpty || !colName
        .forall(c => c >= 'A' && c <= 'Z')
    ) {
      // Handle invalid input, e.g., return -1 or throw exception
      // logger.warn(s"Invalid column name format: $colName")
      return -1 // Or throw new IllegalArgumentException(s"Invalid column name: $colName")
    }

    var result = 0
    var power = 1
    // Iterate from right to left
    for (i <- colName.length - 1 to 0 by -1) {
      val charValue = colName(i) - 'A' + 1
      result += charValue * power
      if (i > 0) { // Avoid overflow on power for the last character
        // Check for potential overflow before multiplying
        if (power > Int.MaxValue / 26) {
          // Handle potential overflow if column names get extremely long (e.g., > "XFD")
          // logger.error(s"Column name $colName is too long, potential overflow")
          return -1 // Or throw exception
        }
        power *= 26
      }
    }
    result - 1 // Convert from 1-based result to 0-based index
  }
}
