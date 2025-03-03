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
  
  /**
   * Cell information used for anchor analysis.
   */
  case class CellInfo(
    row: Int,
    col: Int,
    value: String,
    isBold: Boolean = false,
    isFormula: Boolean = false,
    isNumeric: Boolean = false,
    isDate: Boolean = false,
    isEmpty: Boolean = false
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
     * Filter the grid to only include cells in the specified rows and columns.
     */
    def filterToKeep(rowsToKeep: Set[Int], colsToKeep: Set[Int]): SheetGrid =
      val filteredCells = cells.filter { case ((r, c), _) =>
        rowsToKeep.contains(r) && colsToKeep.contains(c)
      }
      SheetGrid(filteredCells, rowCount, colCount)
    
    /**
     * Remap coordinates to close gaps after pruning.
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
        (newRow, newCol) -> cellInfo.copy(row = newRow, col = newCol)
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
    // Identify anchor rows
    val anchorRows = (0 until grid.rowCount).filter { row =>
      val rowCells = grid.getRow(row)
      isAnchorRow(rowCells, row, grid)
    }.toSet
    
    // Identify anchor columns
    val anchorCols = (0 until grid.colCount).filter { col =>
      val colCells = grid.getCol(col)
      isAnchorColumn(colCells, col, grid)
    }.toSet
    
    logger.info(s"Identified ${anchorRows.size} anchor rows and ${anchorCols.size} anchor columns")
    (anchorRows, anchorCols)

  /**
   * Determines if a row should be considered an anchor based on heterogeneity
   * and structural importance.
   */
  private def isAnchorRow(cells: Seq[CellInfo], rowIdx: Int, grid: SheetGrid): Boolean =
    if cells.isEmpty then
      return false
      
    // Check for format cues that indicate a header or important row
    val hasBoldCells = cells.exists(_.isBold)
    val hasFormulas = cells.exists(_.isFormula)
    
    // Check for type heterogeneity - mix of text, numbers, dates, etc.
    val hasNumbers = cells.exists(_.isNumeric)
    val hasText = cells.exists(c => !c.isNumeric && !c.isDate && !c.isEmpty)
    val hasDates = cells.exists(_.isDate)
    val isHeterogeneous = (hasNumbers && hasText) || (hasNumbers && hasDates) || (hasText && hasDates)
    
    // Check if this row is different from its neighbors
    val isFirstOrLastRow = rowIdx == 0 || rowIdx == grid.rowCount - 1
    val isDifferentFromNeighbors = 
      if rowIdx > 0 && rowIdx < grid.rowCount - 1 then
        val prevRow = grid.getRow(rowIdx - 1)
        val nextRow = grid.getRow(rowIdx + 1)
        
        // Check if data type pattern changes to/from this row
        val prevRowTypes = typePattern(prevRow)
        val currentRowTypes = typePattern(cells)
        val nextRowTypes = typePattern(nextRow)
        
        prevRowTypes != currentRowTypes || currentRowTypes != nextRowTypes
      else
        true // First or last row is automatically different
    
    // A row is an anchor if it has formatting cues or heterogeneous content or is different from neighbors
    hasBoldCells || hasFormulas || isHeterogeneous || isDifferentFromNeighbors || isFirstOrLastRow
  
  /**
   * Determines if a column should be considered an anchor based on heterogeneity
   * and structural importance.
   */
  private def isAnchorColumn(cells: Seq[CellInfo], colIdx: Int, grid: SheetGrid): Boolean =
    if cells.isEmpty then
      return false
      
    // Check for format cues
    val hasBoldCells = cells.exists(_.isBold)
    val hasFormulas = cells.exists(_.isFormula)
    
    // Check for type heterogeneity
    val hasNumbers = cells.exists(_.isNumeric)
    val hasText = cells.exists(c => !c.isNumeric && !c.isDate && !c.isEmpty)
    val hasDates = cells.exists(_.isDate)
    val isHeterogeneous = (hasNumbers && hasText) || (hasNumbers && hasDates) || (hasText && hasDates)
    
    // Check if this column is different from its neighbors
    val isFirstOrLastCol = colIdx == 0 || colIdx == grid.colCount - 1
    val isDifferentFromNeighbors = 
      if colIdx > 0 && colIdx < grid.colCount - 1 then
        val prevCol = grid.getCol(colIdx - 1)
        val nextCol = grid.getCol(colIdx + 1)
        
        // Check if data type pattern changes to/from this column
        val prevColTypes = typePattern(prevCol)
        val currentColTypes = typePattern(cells)
        val nextColTypes = typePattern(nextCol)
        
        prevColTypes != currentColTypes || currentColTypes != nextColTypes
      else
        true // First or last column is automatically different
    
    // A column is an anchor if it has formatting cues or heterogeneous content or is different from neighbors
    hasBoldCells || hasFormulas || isHeterogeneous || isDifferentFromNeighbors || isFirstOrLastCol
  
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
    // Expand anchor rows to include neighbors within threshold
    val expandedRows = anchorRows.flatMap { row =>
      val start = math.max(0, row - threshold)
      val end = math.min(rowCount - 1, row + threshold)
      (start to end)
    }
    
    // Expand anchor columns to include neighbors within threshold
    val expandedCols = anchorCols.flatMap { col =>
      val start = math.max(0, col - threshold)
      val end = math.min(colCount - 1, col + threshold)
      (start to end)
    }
    
    logger.info(s"Expanded to ${expandedRows.size} rows and ${expandedCols.size} columns (threshold=$threshold)")
    (expandedRows, expandedCols)
  
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
      
    logger.info(f"Anchor extraction: ${originalCellCount} cells -> ${retainedCellCount} cells (${compressionRatio}%.2fx compression)")
    
    remappedGrid