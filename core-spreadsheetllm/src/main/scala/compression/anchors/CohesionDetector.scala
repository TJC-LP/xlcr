package com.tjclp.xlcr
package compression.anchors

import compression.models._
import compression.utils.{CellInfoUtils, SheetGridUtils}

import org.slf4j.LoggerFactory

import scala.collection.mutable

/**
 * Implements cohesion region detection and filtering based on original SheetCompressor spec.
 * Cohesion regions represent areas that should be kept together during table detection.
 */
object CohesionDetector:
  private val logger = LoggerFactory.getLogger(getClass)
  
  /**
   * Detects all cohesion regions in a sheet grid.
   * @param grid The sheet grid to analyze
   * @param config Configuration options
   * @return List of detected cohesion regions
   */
  def detectCohesionRegions(grid: SheetGrid, config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()): List[CohesionRegion] = {
    logger.info("Detecting cohesion regions")
    
    // 1. Detect potential cohesion regions based on formatting cues
    val formattingBasedRegions = detectFormattingCohesionRegions(grid)
    
    // 2. Detect regions with strong content relationships
    val contentBasedRegions = detectContentCohesionRegions(grid)
    
    // 3. Find merged cell regions that indicate cohesion
    val mergedCellRegions = detectMergedCellRegions(grid)
    
    // 4. If formula correlation is enabled, find formula-based cohesion regions
    val formulaRegions = if (config.enableFormulaCorrelation) 
      detectFormulaCohesionRegions(grid)
    else 
      List.empty[CohesionRegion]
    
    // 5. Combine and refine all cohesion regions
    val allCohesionRegions = (formattingBasedRegions ++ contentBasedRegions ++ mergedCellRegions ++ formulaRegions)
      .distinct
      .filterNot(region => region.width < 2 || region.height < 2)
    
    logger.info(s"Detected ${allCohesionRegions.size} cohesion regions")
    allCohesionRegions
  }

  /**
   * Detects cohesion regions based on formatting cues like borders and colors.
   */
  private def detectFormattingCohesionRegions(grid: SheetGrid): List[CohesionRegion] = {
    val regions = mutable.ListBuffer[CohesionRegion]()
    
    // Collect cells with similar formatting
    val borderedGroups = findBorderedGroups(grid)
    val colorGroups = findColorGroups(grid)
    
    // Convert formatting groups to cohesion regions
    regions ++= borderedGroups.map { group =>
      createCohesionRegionFromCells(group, CohesionType.Border)
    }
    
    regions ++= colorGroups.map { group =>
      createCohesionRegionFromCells(group, CohesionType.Format)
    }
    
    regions.toList
  }
  
  /**
   * Find groups of cells that are enclosed by borders.
   */
  private def findBorderedGroups(grid: SheetGrid): List[Set[(Int, Int)]] = {
    val result = mutable.ListBuffer[Set[(Int, Int)]]()
    val visited = mutable.Set[(Int, Int)]()
    
    // Look for cells with borders
    for (((row, col), cell) <- grid.cells if !visited.contains((row, col))) {
      if (cell.hasTopBorder || cell.hasBottomBorder || cell.hasLeftBorder || cell.hasRightBorder) {
        // Use BFS to find connected bordered cells
        val group = mutable.Set[(Int, Int)]()
        val queue = mutable.Queue[(Int, Int)]((row, col))
        
        while (queue.nonEmpty) {
          val (r, c) = queue.dequeue()
          if (!visited.contains((r, c))) {
            visited.add((r, c))
            val currentCell = grid.cells.get((r, c))
            
            if (currentCell.exists(cell => 
              cell.hasTopBorder || cell.hasBottomBorder || 
              cell.hasLeftBorder || cell.hasRightBorder)) {
              
              group.add((r, c))
              
              // Add adjacent cells to the queue
              val directions = Seq((0, 1), (1, 0), (0, -1), (-1, 0))
              for ((dr, dc) <- directions) {
                val newR = r + dr
                val newC = c + dc
                if (grid.isInBounds(newR, newC) && !visited.contains((newR, newC))) {
                  queue.enqueue((newR, newC))
                }
              }
            }
          }
        }
        
        // Only add groups with at least 4 cells
        if (group.size >= 4) {
          result += group.toSet
        }
      }
    }
    
    result.toList
  }
  
  /**
   * Find groups of cells with similar background colors.
   */
  private def findColorGroups(grid: SheetGrid): List[Set[(Int, Int)]] = {
    val result = mutable.ListBuffer[Set[(Int, Int)]]()
    val visited = mutable.Set[(Int, Int)]()
    
    // Look for cells with fill colors
    for (((row, col), cell) <- grid.cells if !visited.contains((row, col))) {
      if (cell.hasFillColor) {
        // Use BFS to find connected cells with the same fill color
        val group = mutable.Set[(Int, Int)]()
        val queue = mutable.Queue[(Int, Int)]((row, col))
        
        while (queue.nonEmpty) {
          val (r, c) = queue.dequeue()
          if (!visited.contains((r, c))) {
            visited.add((r, c))
            val currentCell = grid.cells.get((r, c))
            
            if (currentCell.exists(_.hasFillColor)) {
              group.add((r, c))
              
              // Add adjacent cells to the queue
              val directions = Seq((0, 1), (1, 0), (0, -1), (-1, 0))
              for ((dr, dc) <- directions) {
                val newR = r + dr
                val newC = c + dc
                if (grid.isInBounds(newR, newC) && !visited.contains((newR, newC))) {
                  val adjacentCell = grid.cells.get((newR, newC))
                  if (adjacentCell.exists(_.hasFillColor)) {
                    queue.enqueue((newR, newC))
                  }
                }
              }
            }
          }
        }
        
        // Only add groups with at least 4 cells
        if (group.size >= 4) {
          result += group.toSet
        }
      }
    }
    
    result.toList
  }
  
  /**
   * Detects cohesion regions based on content relationships.
   */
  private def detectContentCohesionRegions(grid: SheetGrid): List[CohesionRegion] = {
    val regions = mutable.ListBuffer[CohesionRegion]()
    
    // For now, a simple implementation that looks for areas with similar content types
    // TODO: Enhance with more sophisticated content relationship detection
    
    regions.toList
  }
  
  /**
   * Detects cohesion regions based on merged cells.
   */
  private def detectMergedCellRegions(grid: SheetGrid): List[CohesionRegion] = {
    val regions = mutable.ListBuffer[CohesionRegion]()
    
    // Find all merged cell areas
    val mergedAreas = findMergedCellAreas(grid)
    
    // Convert large merged areas to cohesion regions
    for (area <- mergedAreas) {
      if (area.width > 2 || area.height > 2) {
        // For larger merged areas, consider the surrounding cells too (1 cell padding)
        val expandedRegion = CohesionRegion(
          math.max(0, area.topRow - 1),
          math.min(grid.rowCount - 1, area.bottomRow + 1),
          math.max(0, area.leftCol - 1),
          math.min(grid.colCount - 1, area.rightCol + 1),
          CohesionType.Merged
        )
        regions += expandedRegion
      }
    }
    
    regions.toList
  }
  
  /**
   * Find merged cell areas in the grid.
   */
  private def findMergedCellAreas(grid: SheetGrid): List[CohesionRegion] = {
    val result = mutable.ListBuffer[CohesionRegion]()
    
    // Simple approach: a cell is part of a merged area if it's the same as adjacent cells
    // This is just a placeholder - the real implementation would use Excel's merged cell info
    
    // For now, return an empty list
    // In practice, we would extract merged ranges from the Excel file
    result.toList
  }
  
  /**
   * Detects cohesion regions based on formula relationships.
   */
  private def detectFormulaCohesionRegions(grid: SheetGrid): List[CohesionRegion] = {
    // Extract formula references
    val formulaRefs = grid.extractFormulaReferences()
    if (formulaRefs.isEmpty) return List.empty
    
    // Build formula graph
    val formulaGraph = FormulaGraph(formulaRefs)
    
    // Find connected components (groups of cells connected by formulas)
    val components = formulaGraph.findConnectedComponents()
    
    // Convert components to cohesion regions
    components.map { cells =>
      if (cells.size > 1) {
        createCohesionRegionFromCells(cells, CohesionType.Formula)
      } else {
        // Skip single-cell components
        null
      }
    }.filter(_ != null)
  }
  
  /**
   * Create a cohesion region from a set of cell coordinates.
   */
  private def createCohesionRegionFromCells(cells: Set[(Int, Int)], cohesionType: CohesionType): CohesionRegion = {
    if (cells.isEmpty) {
      throw new IllegalArgumentException("Cannot create a cohesion region from an empty set of cells")
    }
    
    val rows = cells.map(_._1)
    val cols = cells.map(_._2)
    
    CohesionRegion(
      rows.min,
      rows.max,
      cols.min,
      cols.max,
      cohesionType
    )
  }
  
  /**
   * Filter that removes table candidates overlapping with cohesion regions
   * unless they fully contain or are fully contained by the cohesion region.
   */
  def applyOverlapCohesionFilter(tableRegions: List[TableRegion], cohesionRegions: List[CohesionRegion]): List[TableRegion] = {
    logger.info("Applying cohesion overlap filter")
    
    if (cohesionRegions.isEmpty) {
      return tableRegions
    }
    
    tableRegions.filterNot { tableRegion =>
      cohesionRegions.exists { cohesionRegion =>
        // Check if they overlap but neither fully contains the other
        cohesionRegion.overlaps(tableRegion) && 
        !cohesionRegion.contains(tableRegion) && 
        !tableRegion.contains(cohesionRegion.toTableRegion())
      }
    }
  }
  
  /**
   * Filter for border-based cohesion regions.
   */
  def applyOverlapBorderCohesionFilter(tableRegions: List[TableRegion], cohesionRegions: List[CohesionRegion]): List[TableRegion] = {
    logger.info("Applying border cohesion filter")
    
    // Filter to only include border cohesion regions
    val borderRegions = cohesionRegions.filter(_.cohesionType == CohesionType.Border)
    if (borderRegions.isEmpty) {
      return tableRegions
    }
    
    // Implementation based on the original OverlapBorderCohensionFilter
    tableRegions.filterNot { tableRegion =>
      borderRegions.exists { borderRegion =>
        // Check if they overlap but don't fully contain each other
        borderRegion.overlapsWith(tableRegion, exceptForward = true, exceptBackward = true)
      }
    }
  }
  
  /**
   * Formula correlation filter that enhances table detection using formula relationships.
   */
  def applyFormulaCorrelationFilter(grid: SheetGrid, tableRegions: List[TableRegion]): List[TableRegion] = {
    logger.info("Applying formula correlation filter")
    
    // 1. Extract all formulas and their references from the grid
    val formulaReferences = grid.extractFormulaReferences()
    if (formulaReferences.isEmpty) {
      return tableRegions
    }
    
    // 2. Build a dependency graph of formula relationships
    val formulaGraph = FormulaGraph(formulaReferences)
    
    // 3. Identify strongly connected formula components
    val connectedComponents = formulaGraph.findConnectedComponents()
    
    // 4. Create formula-based table regions
    val formulaRegions = connectedComponents.flatMap { component =>
      if (component.size < 2) None
      else Some(createTableRegionFromCells(component, grid))
    }
    
    // 5. Merge existing table regions with formula regions where appropriate
    val enhancedRegions = mergeWithFormulaRegions(tableRegions, formulaRegions)
    
    logger.info(s"Formula correlation produced ${enhancedRegions.size} enhanced table regions")
    enhancedRegions
  }
  
  /**
   * Create a table region from a set of cell coordinates.
   */
  private def createTableRegionFromCells(cells: Set[(Int, Int)], grid: SheetGrid): TableRegion = {
    val rows = cells.map(_._1)
    val cols = cells.map(_._2)
    
    // Get anchor rows and columns within these cells
    val anchorRows = rows.filter(r => SheetGridUtils.isAnchorRow(grid, r))
    val anchorCols = cols.filter(c => SheetGridUtils.isAnchorColumn(grid, c))
    
    TableRegion(
      rows.min,
      rows.max,
      cols.min,
      cols.max,
      anchorRows,
      anchorCols
    )
  }
  
  /**
   * Merge table regions with formula-based regions when appropriate.
   */
  private def mergeWithFormulaRegions(tableRegions: List[TableRegion], formulaRegions: List[TableRegion]): List[TableRegion] = {
    val result = mutable.ListBuffer[TableRegion]()
    result ++= tableRegions
    
    // Check each formula region for potential merges
    for (formulaRegion <- formulaRegions) {
      val overlappingTables = tableRegions.filter(_.overlaps(formulaRegion))
      
      if (overlappingTables.isEmpty) {
        // No overlap - add formula region as a new table candidate
        result += formulaRegion
      } else {
        // Check if we should merge with overlapping tables
        val shouldMerge = overlappingTables.exists { table =>
          val overlapArea = table.width.min(formulaRegion.width) * table.height.min(formulaRegion.height)
          val overlapRatio = overlapArea.toDouble / Math.min(table.area, formulaRegion.area)
          
          // Merge if there's significant overlap or if formulas strongly connect the regions
          overlapRatio > 0.3
        }
        
        if (shouldMerge) {
          // Create a merged region encompassing all overlapping tables and the formula region
          val allRegions = overlappingTables :+ formulaRegion
          val minRow = allRegions.map(_.topRow).min
          val maxRow = allRegions.map(_.bottomRow).max
          val minCol = allRegions.map(_.leftCol).min
          val maxCol = allRegions.map(_.rightCol).max
          
          val allAnchorRows = allRegions.flatMap(_.anchorRows).toSet
          val allAnchorCols = allRegions.flatMap(_.anchorCols).toSet
          
          val mergedRegion = TableRegion(minRow, maxRow, minCol, maxCol, allAnchorRows, allAnchorCols)
          
          // Remove overlapping tables and add the merged one
          result --= overlappingTables
          result += mergedRegion
        }
      }
    }
    
    result.toList
  }
  
  /**
   * Implementation of SplittedEmptyLinesFilter from original spec.
   * Detects continuous empty rows/cols that can split a box into unrelated regions.
   */
  def applySplitEmptyLinesFilter(grid: SheetGrid, tableRegions: List[TableRegion]): List[TableRegion] = {
    logger.info("Applying split empty lines filter")
    
    tableRegions.filter { region =>
      verifyBoxSplit(grid, region)
    }
  }
  
  /**
   * Verifies a box doesn't have continuous empty lines that split it into two unrelated regions.
   * Implementation based on DetectorFilters.cs:VerifyBoxSplit
   */
  private def verifyBoxSplit(grid: SheetGrid, region: TableRegion): Boolean = {
    // Implementation for empty line detection and verification
    val rowOffset = if region.height > 12 then 2 else 0
    val colOffset = if region.width > 12 then 2 else 0
    
    // Check for empty rows that split the region
    for (i <- region.topRow + 3 + rowOffset until region.bottomRow - 4) {
      // Check for three continuous empty rows
      val emptyRows = (i to i + 2).forall(row => SheetGridUtils.isRowEmpty(grid, region.leftCol, region.rightCol, row))
      
      if (emptyRows) {
        // Check for content both above and below the empty rows
        val hasContentAbove = (region.topRow until i).exists(row => 
          !SheetGridUtils.isRowEmpty(grid, region.leftCol, region.rightCol, row))
        val hasContentBelow = ((i + 3) to region.bottomRow).exists(row => 
          !SheetGridUtils.isRowEmpty(grid, region.leftCol, region.rightCol, row))
        
        if (hasContentAbove && hasContentBelow) {
          return false // Found a split
        }
      }
    }
    
    // Similar check for columns
    for (j <- region.leftCol + 3 + colOffset until region.rightCol - 4) {
      // Check for three continuous empty columns
      val emptyCols = (j to j + 2).forall(col => SheetGridUtils.isColEmpty(grid, region.topRow, region.bottomRow, col))
      
      if (emptyCols) {
        // Check for content both left and right of the empty columns
        val hasContentLeft = (region.leftCol until j).exists(col => 
          !SheetGridUtils.isColEmpty(grid, region.topRow, region.bottomRow, col))
        val hasContentRight = ((j + 3) to region.rightCol).exists(col => 
          !SheetGridUtils.isColEmpty(grid, region.topRow, region.bottomRow, col))
        
        if (hasContentLeft && hasContentRight) {
          return false // Found a split
        }
      }
    }
    
    true // No splits found
  }