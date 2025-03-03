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
   * Information about a table detected in the grid
   *
   * @param topRow The top row index of the table
   * @param bottomRow The bottom row index of the table
   * @param leftCol The leftmost column index of the table
   * @param rightCol The rightmost column index of the table
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
    def width: Int = rightCol - leftCol + 1
    def height: Int = bottomRow - topRow + 1
    def area: Int = width * height
    
    /** Get all rows in this table region */
    def allRows: Set[Int] = (topRow to bottomRow).toSet
    
    /** Get all columns in this table region */
    def allCols: Set[Int] = (leftCol to rightCol).toSet
  
  /**
   * Identifies which rows and columns are structural anchors in the sheet
   * based on heterogeneity and formatting cues. Also detects potential
   * table regions within the sheet.
   *
   * @param grid The sheet grid to analyze
   * @return Set of row and column indices identified as anchors
   */
  def identifyAnchors(grid: SheetGrid): (Set[Int], Set[Int]) =
    // Identify anchors for rows and columns using the same logic
    val anchorRows = identifyAnchorsForDimension(grid, Dimension.Row)
    val anchorCols = identifyAnchorsForDimension(grid, Dimension.Column)
    
    // Detect table regions using the anchor information (more advanced method)
    val tableRegions = detectTableRegions(grid, anchorRows, anchorCols)
    
    // Log information about detected tables
    if tableRegions.nonEmpty then
      logger.info(s"Detected ${tableRegions.size} table regions in the sheet")
      tableRegions.zipWithIndex.foreach { case (table, idx) =>
        logger.info(f"  Table ${idx + 1}: (${table.topRow},${table.leftCol}) to (${table.bottomRow},${table.rightCol}) - ${table.width}x${table.height} cells")
      }
    
    logger.info(s"Identified ${anchorRows.size} anchor rows and ${anchorCols.size} anchor columns")
    (anchorRows, anchorCols)
    
  /**
   * Detects potential table regions within the sheet based on anchor rows and columns.
   * This uses a more sophisticated approach to identify distinct tables by looking for
   * clusters of anchors with empty regions between them.
   *
   * @param grid The sheet grid to analyze
   * @param anchorRows Set of identified anchor rows
   * @param anchorCols Set of identified anchor columns
   * @return List of detected table regions
   */
  /**
   * Detects potential table regions within the sheet based on anchor rows and columns.
   * This method is made public so it can be called from the CompressionPipeline.
   * 
   * @param grid The sheet grid to analyze
   * @param anchorRows Set of identified anchor rows
   * @param anchorCols Set of identified anchor columns
   * @param config Configuration options for detection, including minGapSize
   * @return List of detected table regions
   */
  def detectTableRegions(
    grid: SheetGrid, 
    anchorRows: Set[Int], 
    anchorCols: Set[Int],
    config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()
  ): List[TableRegion] =
    // Skip if we have too few anchors to form meaningful table boundaries
    if anchorRows.size < 2 || anchorCols.size < 2 then
      return List(TableRegion(
        0, grid.rowCount - 1, 
        0, grid.colCount - 1,
        anchorRows, anchorCols
      ))
    
    // Step 1: Find gaps in anchor rows that might separate tables
    val sortedRows = anchorRows.toSeq.sorted
    val rowGaps = findGaps(sortedRows, grid.rowCount, config.minGapSize) // Use configured gap size
    
    // Step 2: Find gaps in anchor columns that might separate tables
    val sortedCols = anchorCols.toSeq.sorted
    // For columns, we'll use a potentially smaller gap size to be more sensitive to column separation
    // This makes table detection more effective for side-by-side tables
    val columnGapSize = math.max(1, config.minGapSize - 1) // At least 1, but typically 1 less than row gap size
    val colGaps = findGaps(sortedCols, grid.colCount, columnGapSize)
    
    // Debug logging
    if config.verbose && columnGapSize != config.minGapSize then
      logger.info(s"Using enhanced column detection with gap size $columnGapSize (row gap size: ${config.minGapSize})")
    if config.verbose then
      logger.debug(s"Found ${rowGaps.size} row gaps and ${colGaps.size} column gaps")
    
    // Step 3: Define row segments based on gaps
    val rowSegments = if rowGaps.isEmpty then 
      List((0, grid.rowCount - 1)) 
    else 
      segmentsFromGaps(rowGaps, grid.rowCount)
    
    // Step 4: Define column segments based on gaps
    val colSegments = if colGaps.isEmpty then 
      List((0, grid.colCount - 1))
    else 
      segmentsFromGaps(colGaps, grid.colCount)
    
    // Step 5: Create candidate table regions from the cross product of row and column segments
    val candidates = for 
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
    
    // Step 6: Filter out regions that don't actually have enough content
    // A valid table should have anchors and content 
    candidates.filter { region =>
      val cellCount = countCellsInRegion(grid, region)
      val contentDensity = cellCount.toDouble / region.area
      
      // Calculate the width-to-height ratio to identify very wide or very tall tables
      val widthToHeightRatio = if region.height > 0 then region.width.toDouble / region.height else 0.0
      
      // Calculate separate row and column densities for capturing sparse tables
      val rowsCovered = region.anchorRows.size.toDouble / region.height
      val colsCovered = region.anchorCols.size.toDouble / region.width
      
      // Special case: if the region is significantly wider than tall, it's likely a row-oriented table
      val isRowDominantTable = widthToHeightRatio > 3.0 && rowsCovered > 0.4
      // Special case: if the region is significantly taller than wide, it's likely a column-oriented table
      val isColumnDominantTable = widthToHeightRatio < 0.33 && colsCovered > 0.4
      
      // Enhanced debug logging for table characteristics
      if config.verbose then
        val tableType = 
          if isRowDominantTable then "row-dominant" 
          else if isColumnDominantTable then "column-dominant"
          else "standard"
        logger.debug(f"Table candidate at (${region.topRow},${region.leftCol}) to (${region.bottomRow},${region.rightCol}): " +
          f"${region.width}x${region.height}, type=$tableType, density=$contentDensity%.2f, " +
          f"row coverage=$rowsCovered%.2f, col coverage=$colsCovered%.2f, ratio=$widthToHeightRatio%.2f")
      
      // Keep the region if it meets any of our criteria:
      // 1. It has a reasonable overall density of filled cells
      // 2. It's a row-dominant or column-dominant table
      // 3. Both have at least one anchor row and column
      (contentDensity >= 0.1 || isRowDominantTable || isColumnDominantTable) && 
      region.anchorRows.nonEmpty && 
      region.anchorCols.nonEmpty
    }.toList
    
  /**
   * Finds gaps (sequences of missing indices) in a sorted sequence.
   *
   * @param indices Sorted sequence of indices
   * @param maxIndex The maximum possible index (exclusive)
   * @param minGapSize The minimum gap size to consider significant
   * @return List of gaps as (start, end) pairs
   */
  private def findGaps(indices: Seq[Int], maxIndex: Int, minGapSize: Int): List[(Int, Int)] =
    if indices.isEmpty then
      return List((0, maxIndex - 1))
      
    val result = scala.collection.mutable.ListBuffer[(Int, Int)]()
    
    // Check for a gap at the beginning
    if indices.head > minGapSize then
      result += ((0, indices.head - 1))
    
    // Check for gaps between indices
    for i <- 0 until indices.size - 1 do
      val current = indices(i)
      val next = indices(i + 1)
      
      if next - current > minGapSize then
        result += ((current + 1, next - 1))
    
    // Check for a gap at the end
    if maxIndex - indices.last > minGapSize then
      result += ((indices.last + 1, maxIndex - 1))
    
    result.toList
    
  /**
   * Converts a list of gaps into a list of segments.
   *
   * @param gaps List of gaps as (start, end) pairs
   * @param maxIndex The maximum possible index (exclusive)
   * @return List of segments as (start, end) pairs
   */
  private def segmentsFromGaps(gaps: List[(Int, Int)], maxIndex: Int): List[(Int, Int)] =
    if gaps.isEmpty then
      return List((0, maxIndex - 1))
      
    val sortedGaps = gaps.sortBy(_._1)
    val result = scala.collection.mutable.ListBuffer[(Int, Int)]()
    
    // Add segment before first gap
    if sortedGaps.head._1 > 0 then
      result += ((0, sortedGaps.head._1 - 1))
    
    // Add segments between gaps
    for i <- 0 until sortedGaps.size - 1 do
      val currentGapEnd = sortedGaps(i)._2
      val nextGapStart = sortedGaps(i + 1)._1
      
      if nextGapStart > currentGapEnd + 1 then
        result += ((currentGapEnd + 1, nextGapStart - 1))
    
    // Add segment after last gap
    if sortedGaps.last._2 < maxIndex - 1 then
      result += ((sortedGaps.last._2 + 1, maxIndex - 1))
    
    result.toList
    
  /**
   * Counts the number of non-empty cells in a table region.
   *
   * @param grid The sheet grid
   * @param region The table region to analyze
   * @return Count of non-empty cells in the region
   */
  private def countCellsInRegion(grid: SheetGrid, region: TableRegion): Int =
    val cells = grid.cells.filter { case ((row, col), _) =>
      row >= region.topRow && row <= region.bottomRow &&
      col >= region.leftCol && col <= region.rightCol
    }
    
    cells.size

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