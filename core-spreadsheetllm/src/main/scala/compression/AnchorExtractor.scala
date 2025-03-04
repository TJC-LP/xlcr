package com.tjclp.xlcr
package compression

import models.excel.{CellData, SheetData}

import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.math.{min, max}

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
   * Main entry point for the anchor extraction process.
   *
   * @param grid            The sheet grid to process
   * @param anchorThreshold How many neighboring rows/columns to keep around anchors
   * @return A new grid with non-anchor cells pruned but original coordinates preserved
   */
  def extract(grid: SheetGrid, anchorThreshold: Int): SheetGrid =
    // Step 1: Identify structural anchors
    val (anchorRows, anchorCols) = identifyAnchors(grid)

    // Step 2: Expand anchors to include neighbors within threshold
    val (rowsToKeep, colsToKeep) = expandAnchors(
      anchorRows, anchorCols, grid.rowCount, grid.colCount, anchorThreshold
    )

    // Step 3: Filter the grid to only keep cells in the anchor rows and columns
    // but preserve original coordinates (no remapping)
    val prunedGrid = grid.filterToKeep(rowsToKeep, colsToKeep)

    // Log compression statistics
    val originalCellCount = grid.rowCount * grid.colCount
    val retainedCellCount = prunedGrid.cells.size
    val compressionRatio = if originalCellCount > 0 then
      originalCellCount.toDouble / retainedCellCount
    else
      1.0

    logger.info(f"Anchor extraction: $originalCellCount cells -> $retainedCellCount cells ($compressionRatio%.2fx compression)")

    // Return the pruned grid directly without coordinate remapping
    prunedGrid

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
   * Detects potential table regions within the sheet using an improved approach
   * based on the SheetCompressor framework. This method supports both the gap-based anchor approach
   * and the more advanced BFS-based region detection.
   *
   * @param grid       The sheet grid to analyze
   * @param anchorRows Set of identified anchor rows
   * @param anchorCols Set of identified anchor columns
   * @param config     Configuration options for detection, including minGapSize
   * @return List of detected table regions
   */
  def detectTableRegions(
                          grid: SheetGrid,
                          anchorRows: Set[Int],
                          anchorCols: Set[Int],
                          config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()
                        ): List[TableRegion] =
    // Pick detection strategy based on sheet size
    if grid.rowCount > 1000 || grid.rowCount * grid.colCount > 30000 then
      // Large sheet - use simpler region growth strategy
      logger.info(s"Using region growth detection for large sheet (${grid.rowCount}x${grid.colCount})")
      regionGrowthDetect(grid, config)
    else
      // Smaller sheet - use more sophisticated table sense detection
      logger.info(s"Using table sense detection for sheet (${grid.rowCount}x${grid.colCount})")
      tableSenseDetect(grid, anchorRows, anchorCols, config)
  
  /**
   * RegionGrowthDetect - a simpler connected component approach for large sheets
   * Focused on finding connected regions efficiently.
   */
  private def regionGrowthDetect(grid: SheetGrid, config: SpreadsheetLLMConfig): List[TableRegion] = {
    logger.info("Starting region growth detection")
    
    // Use BFS to find connected components with different tolerance values
    val connectedRanges = findConnectedRanges(grid, config.minGapSize, config.minGapSize)
    
    // Filter little and sparse boxes
    val filteredRanges = filterLittleBoxes(connectedRanges, grid)
    
    // Refine table boundaries
    val refinedRanges = refineBoundaries(filteredRanges, grid)
    
    // Handle header regions
    val withHeaders = retrieveUpHeaders(refinedRanges, grid, config.minGapSize)
    
    // Sort by location (top-left to bottom-right)
    val sortedRanges = rankBoxesByLocation(withHeaders)
    
    // Remove overlapping tables
    val finalRanges = if config.eliminateOverlaps then
      eliminateOverlaps(sortedRanges)
    else
      sortedRanges
      
    logger.info(s"Region growth detection found ${finalRanges.size} tables")
    finalRanges
  }
  
  /**
   * TableSenseDetect - more sophisticated multi-pass approach for smaller sheets
   * Using gap analysis, boundary lines, and multiple criteria for high quality detection
   */
  private def tableSenseDetect(
    grid: SheetGrid, 
    anchorRows: Set[Int], 
    anchorCols: Set[Int],
    config: SpreadsheetLLMConfig
  ): List[TableRegion] = {
    // Skip if we have too few anchors to form meaningful table boundaries
    if anchorRows.size < 2 || anchorCols.size < 2 then
      return List(TableRegion(
        0, grid.rowCount - 1,
        0, grid.colCount - 1,
        anchorRows, anchorCols
      ))

    // Step 1: Find gaps in anchor rows that might separate tables
    val sortedRows = anchorRows.toSeq.sorted
    val rowGaps = findGaps(sortedRows, grid.rowCount, config.minGapSize)

    // Step 2: Find gaps in anchor columns that might separate tables
    val sortedCols = anchorCols.toSeq.sorted
    // For columns, we'll use a potentially smaller gap size to be more sensitive to column separation
    val columnGapSize = math.max(1, config.minGapSize - 1) 
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
      
    // Step 5: Use connected range detection as an additional approach
    // Try multiple thresholds to catch different table types
    val connectedRanges = mutable.ListBuffer[TableRegion]()
    
    for (threshHor <- 1 to 2; threshVer <- 1 to 2) {
      val foundRanges = findConnectedRanges(grid, threshHor, threshVer)
      connectedRanges ++= foundRanges
    }

    // Step 6: Create candidate table regions from both approaches
    val gapBasedCandidates = for
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
      
    // Combine both sets of candidates
    val allCandidates = gapBasedCandidates ++ connectedRanges
    
    // Remove duplicate regions
    val uniqueCandidates = removeDuplicateRegions(allCandidates.toList)
    
    // Filter regions with general criteria
    val filteredCandidates = uniqueCandidates.filter { region =>
      val cellCount = countCellsInRegion(grid, region)
      val contentDensity = cellCount.toDouble / region.area
      
      // Check minimum size
      val isBigEnough = region.width >= 2 && region.height >= 2
      
      // Calculate the width-to-height ratio to identify very wide or very tall tables
      val widthToHeightRatio = if region.height > 0 then region.width.toDouble / region.height else 0.0

      // Calculate separate row and column densities for capturing sparse tables
      val rowsCovered = if region.height > 0 then 
        anchorRows.count(r => r >= region.topRow && r <= region.bottomRow).toDouble / region.height 
      else 0.0
      
      val colsCovered = if region.width > 0 then 
        anchorCols.count(c => c >= region.leftCol && c <= region.rightCol).toDouble / region.width
      else 0.0

      // Special case: if the region is significantly wider than tall, it's likely a row-oriented table
      val isRowDominantTable = widthToHeightRatio > 3.0 && rowsCovered > 0.4
      // Special case: if the region is significantly taller than wide, it's likely a column-oriented table
      val isColumnDominantTable = widthToHeightRatio < 0.33 && colsCovered > 0.4
      
      // Check for border-defined table
      val hasBorderedCells = hasBorders(grid, region)

      // Verbose logging
      if config.verbose then
        val tableType =
          if isRowDominantTable then "row-dominant"
          else if isColumnDominantTable then "column-dominant"
          else "standard"
        logger.debug(f"Table candidate at (${region.topRow},${region.leftCol}) to (${region.bottomRow},${region.rightCol}): " +
          f"${region.width}x${region.height}, type=$tableType, density=$contentDensity%.2f, " +
          f"row coverage=$rowsCovered%.2f, col coverage=$colsCovered%.2f, ratio=$widthToHeightRatio%.2f, has borders=$hasBorderedCells")

      // Keep the region if it meets our criteria
      isBigEnough && 
      (contentDensity >= 0.1 || isRowDominantTable || isColumnDominantTable || hasBorderedCells)
    }
    
    // Refine remaining tables
    val refinedRegions = filterLittleBoxes(filteredCandidates, grid)
    
    // Sort by location
    val sortedRegions = rankBoxesByLocation(refinedRegions)
    
    // Remove overlapping tables
    val finalRegions = if config.eliminateOverlaps then
      eliminateOverlaps(sortedRegions)
    else
      sortedRegions
    
    logger.info(s"Table sense detection found ${finalRegions.size} tables")
    finalRegions
  }
  
  /**
   * Checks if a region has a significant number of bordered cells
   */
  private def hasBorders(grid: SheetGrid, region: TableRegion): Boolean = {
    val cells = grid.cells.filter { case ((r, c), _) =>
      r >= region.topRow && r <= region.bottomRow && 
      c >= region.leftCol && c <= region.rightCol
    }
    
    val borderedCellCount = cells.count { case (_, cell) =>
      cell.hasTopBorder || cell.hasBottomBorder || cell.hasLeftBorder || cell.hasRightBorder
    }
    
    // Return true if at least 25% of cells have borders
    borderedCellCount.toDouble / cells.size >= 0.25
  }
  
  /**
   * Filter out small or sparse boxes
   */
  private def filterLittleBoxes(regions: List[TableRegion], grid: SheetGrid): List[TableRegion] = {
    regions.filter { region =>
      // Size criteria
      val isTooBig = region.width > 50 || region.height > 50
      val isTooSmall = region.width < 2 || region.height < 2 || region.area < 8
      
      // Density criteria
      val cellCount = countCellsInRegion(grid, region)
      val density = cellCount.toDouble / region.area
      val isTooSparse = density < 0.1
      
      // Keep tables unless they're too small or too sparse (but always keep very big tables)
      !isTooSmall && (!isTooSparse || isTooBig)
    }
  }
  
  /**
   * Remove duplicated regions (highly overlapping)
   */
  private def removeDuplicateRegions(regions: List[TableRegion]): List[TableRegion] = {
    val result = mutable.ListBuffer[TableRegion]()
    
    // Sort by area (largest first)
    val sortedRegions = regions.sortBy(-_.area)
    
    for (region <- sortedRegions) {
      val isDuplicate = result.exists { existing =>
        val overlapArea = calculateOverlapArea(existing, region)
        val overlapRatio = overlapArea.toDouble / min(existing.area, region.area)
        overlapRatio > 0.8 // More than 80% overlap is considered duplicate
      }
      
      if (!isDuplicate) {
        result += region
      }
    }
    
    result.toList
  }
  
  /**
   * Calculate the overlap area between two regions
   */
  private def calculateOverlapArea(r1: TableRegion, r2: TableRegion): Int = {
    val overlapWidth = max(0, min(r1.rightCol, r2.rightCol) - max(r1.leftCol, r2.leftCol) + 1)
    val overlapHeight = max(0, min(r1.bottomRow, r2.bottomRow) - max(r1.topRow, r2.topRow) + 1)
    overlapWidth * overlapHeight
  }
  
  /**
   * Sort regions by location (top-to-bottom, left-to-right)
   */
  private def rankBoxesByLocation(regions: List[TableRegion]): List[TableRegion] = {
    regions.sortBy(r => (r.topRow, r.leftCol))
  }
  
  /**
   * Refine boundaries of regions
   */
  private def refineBoundaries(regions: List[TableRegion], grid: SheetGrid): List[TableRegion] = {
    // Apply multiple passes of trimming
    val regions1 = regions.map(r => trimEmptyTopBottom(r, grid))
    val regions2 = regions1.map(r => trimEmptySides(r, grid))
    regions2
  }
  
  /**
   * Trim empty top and bottom rows
   */
  private def trimEmptyTopBottom(region: TableRegion, grid: SheetGrid): TableRegion = {
    var topRow = region.topRow
    var bottomRow = region.bottomRow
    
    // Trim from top
    while (topRow < bottomRow && isRowEmpty(grid, region.leftCol, region.rightCol, topRow)) {
      topRow += 1
    }
    
    // Trim from bottom
    while (bottomRow > topRow && isRowEmpty(grid, region.leftCol, region.rightCol, bottomRow)) {
      bottomRow -= 1
    }
    
    if (topRow != region.topRow || bottomRow != region.bottomRow) {
      TableRegion(topRow, bottomRow, region.leftCol, region.rightCol, 
                 region.anchorRows, region.anchorCols)
    } else {
      region
    }
  }
  
  /**
   * Trim empty left and right columns
   */
  private def trimEmptySides(region: TableRegion, grid: SheetGrid): TableRegion = {
    var leftCol = region.leftCol
    var rightCol = region.rightCol
    
    // Trim from left
    while (leftCol < rightCol && isColEmpty(grid, region.topRow, region.bottomRow, leftCol)) {
      leftCol += 1
    }
    
    // Trim from right
    while (rightCol > leftCol && isColEmpty(grid, region.topRow, region.bottomRow, rightCol)) {
      rightCol -= 1
    }
    
    if (leftCol != region.leftCol || rightCol != region.rightCol) {
      TableRegion(region.topRow, region.bottomRow, leftCol, rightCol,
                 region.anchorRows, region.anchorCols)
    } else {
      region
    }
  }
  
  /**
   * Try to include header rows above detected tables
   */
  private def retrieveUpHeaders(regions: List[TableRegion], grid: SheetGrid, step: Int): List[TableRegion] = {
    regions.map { region =>
      var newTopRow = region.topRow
      
      // Try to find headers up to 'step' rows above
      for (row <- (region.topRow - step) until region.topRow) {
        if (row >= 0 && isHeaderRow(grid, row, region.leftCol, region.rightCol)) {
          newTopRow = row
          // Found header, no need to check more rows above
          row == region.topRow // Force exit from loop
        }
      }
      
      if (newTopRow != region.topRow) {
        TableRegion(newTopRow, region.bottomRow, region.leftCol, region.rightCol, 
                   region.anchorRows, region.anchorCols)
      } else {
        region
      }
    }
  }
  
  /**
   * Check if a row appears to be a header row based on formatting and content
   */
  private def isHeaderRow(grid: SheetGrid, row: Int, leftCol: Int, rightCol: Int): Boolean = {
    // Get all cells in the row within the column range
    val cells = (leftCol to rightCol).flatMap(col => grid.cells.get((row, col)))
    
    if (cells.isEmpty) return false
    
    // Header characteristics
    val totalCells = cells.size
    val nonEmptyCells = cells.count(!_.isEmpty)
    val boldCells = cells.count(_.isBold)
    val borderCells = cells.count(c => c.hasTopBorder || c.hasBottomBorder)
    val colorCells = cells.count(_.hasFillColor)
    
    // Check if there's at least some content
    if (nonEmptyCells < 1) return false
    
    // Strong header indicators
    val hasMostlyBold = boldCells.toDouble / totalCells >= 0.5
    val hasMostlyBorders = borderCells.toDouble / totalCells >= 0.5
    val hasMostlyColor = colorCells.toDouble / totalCells >= 0.5
    
    // Text-based header detection
    val hasHeaderText = cells.exists(c => 
      !c.isEmpty && c.alphabetRatio > c.numberRatio && c.alphabetRatio > 0.5
    )
    
    // Return true if it shows multiple header characteristics
    hasMostlyBold || hasMostlyBorders || hasMostlyColor || hasHeaderText
  }
  
  /**
   * Eliminate overlapping tables by keeping the larger one
   */
  private def eliminateOverlaps(regions: List[TableRegion]): List[TableRegion] = {
    val result = mutable.ListBuffer[TableRegion]()
    val sortedRegions = regions.sortBy(-_.area) // Sort by decreasing area
    
    for (region <- sortedRegions) {
      val overlapsExisting = result.exists { existing =>
        val overlapArea = calculateOverlapArea(existing, region)
        // Consider significant overlap if more than 25% of the smaller table
        val overlapRatio = overlapArea.toDouble / min(existing.area, region.area)
        overlapRatio > 0.25
      }
      
      if (!overlapsExisting) {
        result += region
      }
    }
    
    result.toList
  }

  /**
   * Finds gaps (sequences of missing indices) in a sorted sequence.
   *
   * @param indices    Sorted sequence of indices
   * @param maxIndex   The maximum possible index (exclusive)
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
   * @param gaps     List of gaps as (start, end) pairs
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
   * @param grid   The sheet grid
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
   * Find connected regions in the sheet using a BFS algorithm that allows for empty cell tolerance.
   * This implements the core of FindConnectedRanges from the SheetCompressor pseudocode.
   *
   * @param grid      The sheet grid to analyze
   * @param threshHor Maximum number of empty cells to tolerate horizontally
   * @param threshVer Maximum number of empty cells to tolerate vertically
   * @param direction Search direction (0 for top-to-bottom, 1 for bottom-to-top)
   * @return List of detected table regions
   */
  private def findConnectedRanges(
    grid: SheetGrid,
    threshHor: Int = 1,
    threshVer: Int = 1,
    direction: Int = 1
  ): List[TableRegion] = {
    val height = grid.rowCount
    val width = grid.colCount
    
    // Track visited cells and tolerance counters
    val visited = Array.ofDim[Boolean](height, width)
    val horizontalCounter = Array.ofDim[Int](height, width) // Empty cell tolerance horizontally
    val verticalCounter = Array.ofDim[Int](height, width)   // Empty cell tolerance vertically
    
    // Initialize counters with threshold values
    for (row <- 0 until height; col <- 0 until width) {
      horizontalCounter(row)(col) = threshHor
      verticalCounter(row)(col) = threshVer
    }
    
    // Define traversal range based on direction
    val rangeRow = if (direction == 0) 0 until height else (height - 1) to 0 by -1
    val rangeCol = 0 until width  // Always left to right
    
    val tableRegions = mutable.ListBuffer[TableRegion]()
    
    // Scan sheet for unvisited non-empty cells
    for (row <- rangeRow; col <- rangeCol) {
      // Skip visited or empty cells
      if (!visited(row)(col)) {
        val cell = grid.cells.get((row, col))
        
        if (cell.exists(!_.isEmpty)) {
          // BFS to find connected region
          val queue = mutable.Queue[(Int, Int)]()
          queue.enqueue((row, col))
          visited(row)(col) = true
          
          var minRow = row
          var maxRow = row
          var minCol = col
          var maxCol = col
          
          while (queue.nonEmpty) {
            val (currentRow, currentCol) = queue.dequeue()
            
            // Skip if tolerance exhausted
            if (horizontalCounter(currentRow)(currentCol) == 0 || 
                verticalCounter(currentRow)(currentCol) == 0) {
              // Skip this cell
            } else {
              // Update bounds of connected region
              minRow = min(minRow, currentRow)
              maxRow = max(maxRow, currentRow)
              minCol = min(minCol, currentCol)
              maxCol = max(maxCol, currentCol)
              
              // Define direction vectors: right, left, down, up
              val directions = Seq((0,1), (0,-1), (1,0), (-1,0))
              
              // Check all four directions
              for (i <- 0 until 4) {
                val nextRow = currentRow + directions(i)._1
                val nextCol = currentCol + directions(i)._2
                
                if (isInBounds(nextRow, nextCol, height, width) && !visited(nextRow)(nextCol)) {
                  visited(nextRow)(nextCol) = true
                  
                  // Check if next cell is empty
                  val nextCell = grid.cells.get((nextRow, nextCol))
                  val isEmpty = nextCell.forall(_.isEmpty)
                  
                  if (isEmpty) {
                    // Reduce counter for empty cells
                    if (directions(i)._2 != 0) { // Horizontal movement
                      horizontalCounter(nextRow)(nextCol) = horizontalCounter(currentRow)(currentCol) - 1
                    }
                    if (directions(i)._1 != 0) { // Vertical movement
                      verticalCounter(nextRow)(nextCol) = verticalCounter(currentRow)(currentCol) - 1
                    }
                  } else {
                    // If border exists, reset tolerance counters
                    val hasBorder = nextCell.exists(c => 
                      c.hasTopBorder || c.hasBottomBorder || c.hasLeftBorder || c.hasRightBorder
                    )
                    
                    if (hasBorder) {
                      if (directions(i)._2 != 0) { // Horizontal
                        horizontalCounter(nextRow)(nextCol) = threshHor
                      }
                      if (directions(i)._1 != 0) { // Vertical
                        verticalCounter(nextRow)(nextCol) = threshVer
                      }
                    }
                  }
                  
                  queue.enqueue((nextRow, nextCol))
                }
              }
            }
          }
          
          // Add detected region if it's large enough
          if (maxRow - minRow > 1) {
            // Convert to 1-based coordinates to match our TableRegion convention
            val tableRegion = TableRegion(
              minRow, maxRow, minCol, maxCol,
              Set.empty, Set.empty // Empty anchor sets
            )
            tableRegions += tableRegion
            
            // Mark all cells in this region as visited to avoid redundant processing
            for (r <- minRow to maxRow; c <- minCol to maxCol) {
              visited(r)(c) = true
            }
          }
        }
      }
    }
    
    // Trim empty edges from regions (3 passes for stability)
    var trimmedRegions = tableRegions.toList
    for (_ <- 0 until 3) {
      trimmedRegions = trimEmptyEdges(trimmedRegions, grid)
    }
    
    trimmedRegions
  }
  
  /**
   * Trims empty edges from table regions.
   */
  private def trimEmptyEdges(regions: List[TableRegion], grid: SheetGrid): List[TableRegion] = {
    regions.map { region =>
      var up = region.topRow
      var down = region.bottomRow
      var left = region.leftCol
      var right = region.rightCol
      
      // Trim top rows until non-empty row found
      var foundNonEmpty = false
      for (i <- up to down if !foundNonEmpty) {
        if (!isRowEmpty(grid, left, right, i)) {
          up = i
          foundNonEmpty = true
        }
      }
      
      // Trim bottom rows until non-empty row found
      foundNonEmpty = false
      for (i <- down to up by -1 if !foundNonEmpty) {
        if (!isRowEmpty(grid, left, right, i)) {
          down = i
          foundNonEmpty = true
        }
      }
      
      // Trim left columns until non-empty column found
      foundNonEmpty = false
      for (j <- left to right if !foundNonEmpty) {
        if (!isColEmpty(grid, up, down, j)) {
          left = j
          foundNonEmpty = true
        }
      }
      
      // Trim right columns until non-empty column found
      foundNonEmpty = false
      for (j <- right to left by -1 if !foundNonEmpty) {
        if (!isColEmpty(grid, up, down, j)) {
          right = j
          foundNonEmpty = true
        }
      }
      
      // Create new trimmed region
      if (left <= right && up <= down) {
        TableRegion(up, down, left, right, Set.empty, Set.empty)
      } else {
        // If trimming made the region invalid, return the original
        region
      }
    }
  }
  
  /** Checks if a row is empty */
  private def isRowEmpty(grid: SheetGrid, left: Int, right: Int, row: Int): Boolean = {
    !(left to right).exists(col => 
      grid.cells.get((row, col)).exists(!_.isEmpty)
    )
  }
  
  /** Checks if a column is empty */
  private def isColEmpty(grid: SheetGrid, top: Int, bottom: Int, col: Int): Boolean = {
    !(top to bottom).exists(row => 
      grid.cells.get((row, col)).exists(!_.isEmpty)
    )
  }
  
  /** Checks if coordinates are in bounds */
  private def isInBounds(row: Int, col: Int, height: Int, width: Int): Boolean = {
    row >= 0 && row < height && col >= 0 && col < width
  }

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
   * 
   * Enhanced with additional border and formatting checks based on the SheetCompressor framework.
   */
  private def isAnchor(cells: Seq[CellInfo], idx: Int, grid: SheetGrid, dim: Dimension): Boolean =
    if cells.isEmpty then
      return false

    // Check for format cues that indicate a header or important row/column
    val hasBoldCells = cells.exists(_.isBold)
    val hasFormulas = cells.exists(_.isFormula)
    
    // Check for border patterns that might indicate structure
    val hasBorders = dim match
      case Dimension.Row => 
        cells.count(c => c.hasTopBorder || c.hasBottomBorder) > cells.size / 3
      case Dimension.Column => 
        cells.count(c => c.hasLeftBorder || c.hasRightBorder) > cells.size / 3
    
    // Check for fill color patterns that might indicate structure
    val hasFillColors = cells.count(_.hasFillColor) > cells.size / 4
    
    // Check for header-like characteristics
    val hasHeaderCharacteristics = dim match
      case Dimension.Row =>
        // Headers typically have more letters than numbers and may contain special characters
        cells.count(c => c.alphabetRatio > c.numberRatio && c.textLength > 0) > cells.size / 3 ||
        cells.count(c => c.spCharRatio > 0 && !c.isEmpty) > 1
      case Dimension.Column => 
        // Column headers often have similar characteristics to row headers
        cells.count(c => c.alphabetRatio > c.numberRatio && c.textLength > 0) > cells.size / 3

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

        // Check if formatting patterns change
        val prevFormatting = formatPattern(prevCells)
        val currentFormatting = formatPattern(cells)
        val nextFormatting = formatPattern(nextCells)

        prevPattern != currentPattern || currentPattern != nextPattern ||
        prevFormatting != currentFormatting || currentFormatting != nextFormatting
      else
        true // First or last row/column is automatically different

    // A row/column is an anchor if it has formatting cues, borders, colors, 
    // header characteristics, heterogeneous content, or is different from neighbors
    hasBoldCells || hasFormulas || hasBorders || hasFillColors || 
    hasHeaderCharacteristics || isHeterogeneous || isDifferentFromNeighbors || isFirstOrLast

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
   * Helper method to determine the formatting pattern in a row or column.
   * This helps detect structure based on borders, colors, and formatting.
   */
  private def formatPattern(cells: Seq[CellInfo]): String =
    cells.map { cell =>
      val borderPart = if cell.hasTopBorder || cell.hasBottomBorder || 
                          cell.hasLeftBorder || cell.hasRightBorder then "B" else "-"
      val colorPart = if cell.hasFillColor then "C" else "-"
      val boldPart = if cell.isBold then "F" else "-"
      s"$borderPart$colorPart$boldPart"
    }.mkString

  /**
   * Expands the set of anchor rows and columns by including neighbors
   * up to the specified threshold.
   *
   * @param anchorRows Set of row indices identified as anchors
   * @param anchorCols Set of column indices identified as anchors
   * @param rowCount   Total number of rows in the sheet
   * @param colCount   Total number of columns in the sheet
   * @param threshold  How many neighboring rows/cols to include around each anchor
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

  // Define dimension enum to abstract row vs column operations
  enum Dimension:
    case Row, Column

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

  object CellInfo:
    /**
     * Convert from CellData to CellInfo
     *
     * @param cellData The CellData from the core Excel model
     * @return CellInfo suitable for anchor extraction
     */
    def fromCellData(cellData: CellData): CellInfo =
      // Extract the row and column from the cell's A1 reference
      // Keep the original 1-based row index that Excel uses (don't subtract 1)
      val rowIndex = cellData.rowIndex
      val colIndex = cellData.columnIndex
      
      // Validate column index is not negative
      val validatedColIndex = math.max(0, colIndex)
      
      if (colIndex < 0) {
        logger.warn(s"Detected negative column index: $colIndex in cell ${cellData.referenceA1}, corrected to 0")
      }

      // Use formatted value if available, otherwise use raw value
      val displayValue = cellData.formattedValue.getOrElse(cellData.value.getOrElse(""))

      // Extract formatting information
      val isBold = cellData.font.exists(_.bold)
      val isFormula = cellData.formula.isDefined
      val isNumeric = cellData.cellType == "NUMERIC"
      val isDate = cellData.dataFormat.exists(format =>
        format.contains("d") && format.contains("m") && format.contains("y") ||
          format == "m/d/yy" || format.contains("date"))
      val isEmpty = displayValue.trim.isEmpty || cellData.cellType == "BLANK"
      
      // Extract border information from cell style (if available)
      val hasTopBorder = cellData.style.exists(style => 
        style.borderTop.exists(_ != "NONE"))
      val hasBottomBorder = cellData.style.exists(style => 
        style.borderBottom.exists(_ != "NONE"))
      val hasLeftBorder = cellData.style.exists(style => 
        style.borderLeft.exists(_ != "NONE"))
      val hasRightBorder = cellData.style.exists(style => 
        style.borderRight.exists(_ != "NONE"))
      
      // Check if the cell has a background color
      val hasFillColor = cellData.style.exists(style => 
        style.backgroundColor.isDefined || style.foregroundColor.isDefined)
      
      // Calculate text feature ratios for header detection
      val text = displayValue
      val textLength = text.length
      val alphabetCount = text.count(_.isLetter)
      val numberCount = text.count(_.isDigit)
      val specialCount = text.count(c => !c.isLetterOrDigit && !c.isWhitespace)
      
      val alphabetRatio = if (textLength > 0) alphabetCount.toDouble / textLength else 0.0
      val numberRatio = if (textLength > 0) numberCount.toDouble / textLength else 0.0
      val spCharRatio = if (textLength > 0) specialCount.toDouble / textLength else 0.0

      CellInfo(
        row = rowIndex,
        col = validatedColIndex, // Use validated column index
        value = displayValue,
        isBold = isBold,
        isFormula = isFormula,
        isNumeric = isNumeric,
        isDate = isDate,
        isEmpty = isEmpty,
        hasTopBorder = hasTopBorder,
        hasBottomBorder = hasBottomBorder,
        hasLeftBorder = hasLeftBorder,
        hasRightBorder = hasRightBorder,
        hasFillColor = hasFillColor,
        textLength = textLength,
        alphabetRatio = alphabetRatio,
        numberRatio = numberRatio,
        spCharRatio = spCharRatio,
        numberFormatString = cellData.dataFormat,
        cellData = Some(cellData)
      )

  object SheetGrid:
    /**
     * Create a SheetGrid from a SheetData
     *
     * @param sheetData The sheet data from the core Excel model
     * @return A SheetGrid suitable for anchor extraction
     */
    def fromSheetData(sheetData: SheetData): SheetGrid =
      // Convert each cell in the SheetData to a CellInfo
      val cellMap = sheetData.cells.map { cellData =>
        val cellInfo = CellInfo.fromCellData(cellData)
        (cellInfo.row, cellInfo.col) -> cellInfo
      }.toMap

      SheetGrid(
        cells = cellMap,
        rowCount = sheetData.rowCount,
        colCount = sheetData.columnCount
      )