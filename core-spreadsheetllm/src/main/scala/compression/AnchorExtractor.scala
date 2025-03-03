package com.tjclp.xlcr
package compression

import org.slf4j.LoggerFactory

/**
 * AnchorExtractor identifies structural anchors in a spreadsheet and prunes away
 * less-informative cells. This is the first step in the SpreadsheetLLM compression pipeline.
 *
 * Anchors are heterogeneous rows and columns that define table boundaries and structural
 * elements (like headers, footers, or column labels). Cells that are far from any anchor
 * are pruned to reduce the spreadsheet size while preserving its structure.
 */
object AnchorExtractor:
  private val logger = LoggerFactory.getLogger(getClass)

  // Define dimension enum to abstract row vs column operations
  enum Dimension:
    case Row, Column

  /**
   * Cell information used for anchor analysis.
   *
   * @param row The 0-based row index
   * @param col The 0-based column index
   * @param value The cell content as a string
   * @param isBold Whether the cell has bold formatting
   * @param isFormula Whether the cell contains a formula
   * @param isNumeric Whether the cell contains numeric content
   * @param isDate Whether the cell contains a date
   * @param isEmpty Whether the cell is empty
   * @param originalRow The original row index before remapping (for debugging)
   * @param originalCol The original column index before remapping (for debugging)
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
                       originalRow: Option[Int] = None,
                       originalCol: Option[Int] = None
                     )

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

      // For debugging purposes, log the mapping
      logger.debug(s"Row mapping: ${rowMap.take(10)}...")
      logger.debug(s"Column mapping: ${colMap.take(10)}...")

      // Remap each cell to its new coordinates
      val remappedCells = cells.map { case ((oldRow, oldCol), cellInfo) =>
        val newRow = rowMap(oldRow)
        val newCol = colMap(oldCol)

        // For debugging, log significant coordinate changes
        if math.abs(oldRow - newRow) > 1 || math.abs(oldCol - newCol) > 1 then
          logger.debug(s"Remapping cell: ($oldRow,$oldCol) -> ($newRow,$newCol) [${cellInfo.value}]")

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

  /**
   * Identifies which rows and columns are structural anchors in the sheet
   * based on heterogeneity and formatting cues.
   *
   * @param grid The sheet grid to analyze
   * @return Set of row and column indices identified as anchors
   */
  def identifyAnchors(grid: SheetGrid): (Set[Int], Set[Int]) =
    // Identify anchors for rows and columns using the same logic
    val anchorRows = identifyAnchorsForDimension(grid, Dimension.Row)
    val anchorCols = identifyAnchorsForDimension(grid, Dimension.Column)

    logger.info(s"Identified ${anchorRows.size} anchor rows and ${anchorCols.size} anchor columns")
    (anchorRows, anchorCols)

  /**
   * Identifies anchors for a specific dimension (rows or columns).
   */
  private def identifyAnchorsForDimension(grid: SheetGrid, dim: Dimension): Set[Int] =
    val count = grid.getDimCount(dim)
    (0 until count).filter { idx =>
      val cells = grid.getCells(dim, idx)
      isAnchor(cells, idx, grid, dim)
    }.toSet

  /**
   * Determines if a row or column should be considered an anchor based on heterogeneity
   * and structural importance.
   */
  private def isAnchor(cells: Seq[CellInfo], idx: Int, grid: SheetGrid, dim: Dimension): Boolean =
    if cells.isEmpty then
      return false

    // Check for format cues that indicate a header or important row/column
    val hasBoldCells = cells.exists(_.isBold)
    val hasFormulas = cells.exists(_.isFormula)

    // Check for type heterogeneity - mix of text, numbers, dates, etc.
    val hasNumbers = cells.exists(_.isNumeric)
    val hasText = cells.exists(c => !c.isNumeric && !c.isDate && !c.isEmpty)
    val hasDates = cells.exists(_.isDate)
    val isHeterogeneous = (hasNumbers && hasText) || (hasNumbers && hasDates) || (hasText && hasDates)

    // Check if this row/column is different from its neighbors
    val count = grid.getDimCount(dim)
    val isFirstOrLast = idx == 0 || idx == count - 1
    val isDifferentFromNeighbors =
      if idx > 0 && idx < count - 1 then
        val prevCells = grid.getCells(dim, idx - 1)
        val nextCells = grid.getCells(dim, idx + 1)

        // Check if data type pattern changes to/from this row/column
        val prevPattern = typePattern(prevCells)
        val currentPattern = typePattern(cells)
        val nextPattern = typePattern(nextCells)

        prevPattern != currentPattern || currentPattern != nextPattern
      else
        true // First or last row/column is automatically different

    // A row/column is an anchor if it has formatting cues or heterogeneous content or is different from neighbors
    hasBoldCells || hasFormulas || isHeterogeneous || isDifferentFromNeighbors || isFirstOrLast

  /**
   * Helper method to determine the type pattern (sequence of cell types) in a row or column.
   */
  private def typePattern(cells: Seq[CellInfo]): String =
    cells.map { cell =>
      if cell.isEmpty then "E"
      else if cell.isNumeric then "N"
      else if cell.isDate then "D"
      else "T" // Text
    }.mkString

  /**
   * Expands the set of anchor rows and columns by including neighbors
   * up to the specified threshold.
   *
   * @param anchorRows Set of row indices identified as anchors
   * @param anchorCols Set of column indices identified as anchors
   * @param rowCount Total number of rows in the sheet
   * @param colCount Total number of columns in the sheet
   * @param threshold How many neighboring rows/cols to include around each anchor
   * @return Expanded sets of row and column indices to keep
   */
  def expandAnchors(
                     anchorRows: Set[Int],
                     anchorCols: Set[Int],
                     rowCount: Int,
                     colCount: Int,
                     threshold: Int
                   ): (Set[Int], Set[Int]) =
    // Use common expansion logic for both dimensions
    val expandedRows = expandDimension(anchorRows, rowCount, threshold)
    val expandedCols = expandDimension(anchorCols, colCount, threshold)

    logger.info(s"Expanded to ${expandedRows.size} rows and ${expandedCols.size} columns (threshold=$threshold)")
    (expandedRows, expandedCols)

  /**
   * Expands a set of indices by including neighbors within threshold.
   */
  private def expandDimension(anchors: Set[Int], count: Int, threshold: Int): Set[Int] =
    anchors.flatMap { anchor =>
      val start = math.max(0, anchor - threshold)
      val end = math.min(count - 1, anchor + threshold)
      start to end
    }

  /**
   * Main entry point for the anchor extraction process.
   *
   * @param grid The sheet grid to process
   * @param anchorThreshold How many neighboring rows/columns to keep around anchors
   * @return A new grid with non-anchor cells pruned and coordinates remapped
   */
  def extract(grid: SheetGrid, anchorThreshold: Int): SheetGrid =
    // Step 1: Identify structural anchors
    val (anchorRows, anchorCols) = identifyAnchors(grid)

    // Step 2: Expand anchors to include neighbors within threshold
    val (rowsToKeep, colsToKeep) = expandAnchors(
      anchorRows, anchorCols, grid.rowCount, grid.colCount, anchorThreshold
    )

    // Step 3: Filter the grid to only keep cells in the anchor rows and columns
    val prunedGrid = grid.filterToKeep(rowsToKeep, colsToKeep)

    // Step 4: Remap coordinates to close gaps
    val remappedGrid = prunedGrid.remapCoordinates()

    // Log compression statistics
    val originalCellCount = grid.rowCount * grid.colCount
    val retainedCellCount = remappedGrid.cells.size
    val compressionRatio = if originalCellCount > 0 then
      originalCellCount.toDouble / retainedCellCount
    else
      1.0

    logger.info(f"Anchor extraction: $originalCellCount cells -> $retainedCellCount cells ($compressionRatio%.2fx compression)")

    remappedGrid