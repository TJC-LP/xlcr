package com.tjclp.xlcr
package compression.tables

import compression.AnchorExtractor
import compression.models.{SheetGrid, TableRegion}
import compression.utils.SheetGridUtils

import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.math.{max, min}

/**
 * Implementation of the RegionGrowth algorithm for table detection.
 * This is a simpler connected component approach for large sheets,
 * focused on finding connected regions efficiently.
 */
object RegionGrowthDetector:
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Main entry point for region growth detection
   */
  def detect(grid: SheetGrid, config: SpreadsheetLLMConfig): List[TableRegion] = {
    logger.info("Starting region growth detection")

    // Extract anchor rows and columns for BFS if anchor checking is enabled
    val anchorRows = if (config.enableAnchorCheckInBFS && !config.disableAnchorExtraction) 
      AnchorExtractor.extractAnchorRows(grid)
    else 
      Set.empty[Int]
      
    val anchorCols = if (config.enableAnchorCheckInBFS && !config.disableAnchorExtraction)
      AnchorExtractor.extractAnchorColumns(grid)
    else
      Set.empty[Int]

    if (config.verbose && config.enableAnchorCheckInBFS) {
      logger.debug(s"Using ${anchorRows.size} anchor rows and ${anchorCols.size} anchor columns for BFS boundary detection")
    }
      
    // Use BFS to find connected components with configured tolerance values
    // Use the new lower empty cell tolerance parameters
    val connectedRanges = findConnectedRanges(
      grid,
      threshHor = config.emptyToleranceHorizontal,
      threshVer = config.emptyToleranceVertical,
      config = config,
      anchorRows = anchorRows,
      anchorCols = anchorCols
    )

    // Filter little and sparse boxes with enhanced criteria
    val filteredRanges = filterLittleBoxes(connectedRanges, grid, config)

    // Refine table boundaries
    val refinedRanges = refineBoundaries(filteredRanges, grid, config)

    // Handle header regions
    val withHeaders = retrieveUpHeaders(refinedRanges, grid, config.minGapSize)

    // Sort by location (top-left to bottom-right)
    val sortedRanges = TableDetector.rankBoxesByLocation(withHeaders)

    // Remove overlapping tables
    val finalRanges = if config.eliminateOverlaps then
      eliminateOverlaps(sortedRanges)
    else
      sortedRanges

    logger.info(s"Region growth detection found ${finalRanges.size} tables")
    finalRanges
  }
  
  /**
   * Enhanced boundary refinement with multiple specialized passes
   */
  private def refineBoundaries(regions: List[TableRegion], grid: SheetGrid, config: SpreadsheetLLMConfig): List[TableRegion] = {
    var result = regions
    
    // Pass 1: Basic empty edge trimming (multiple passes for stability)
    for (_ <- 0 until 3) {
      result = trimEmptyEdges(result, grid)
    }
    
    // Pass 2: Sparse interior trimming - this would split regions with large empty areas
    // Placeholder for future implementation
    
    // For now, use TableDetector's refineBoundaries for compatibility
    TableDetector.refineBoundaries(result, grid)
  }

  /**
   * Filter out small or sparse regions, applying stricter criteria for larger regions
   * and considering content diversity.
   *
   * @param regions List of candidate table regions
   * @param grid The sheet grid
   * @param config Configuration with filtering parameters
   * @return Filtered list of table regions
   */
  private def filterLittleBoxes(regions: List[TableRegion], grid: SheetGrid, config: SpreadsheetLLMConfig): List[TableRegion] = {
    regions.filter { region =>
      // Size criteria
      val isTooBig = region.width > 50 || region.height > 50
      val isTooSmall = region.width < 2 || region.height < 2 || region.area < 8

      // Density criteria with different thresholds based on size
      val density = SheetGridUtils.calculateDensity(grid, region)
      
      // Apply stricter density threshold for larger tables
      val minimumDensity = if (isTooBig) 
        math.max(0.25, config.minTableDensity * 1.5) // 50% higher threshold for large tables
      else 
        config.minTableDensity
        
      val isTooSparse = density < minimumDensity
      
      // Additional content type check - ensure large regions have diverse content
      val contentDiversity = if (isTooBig) 
        SheetGridUtils.calculateContentDiversity(grid, region) 
      else 
        0.3 // Default value for small regions (always passes the diversity check)
        
      val hasDiverseContent = contentDiversity >= 0.3 // At least 30% content type diversity
      
      // Very large tables must pass both density and diversity checks
      val passesLargeTableChecks = if (region.area > config.maxTableSize)
        !isTooSparse && hasDiverseContent
      else 
        !isTooSparse || (isTooBig && hasDiverseContent)
      
      // Log filtering decision for debugging in verbose mode
      if (config.verbose && (isTooSmall || !passesLargeTableChecks)) {
        logger.debug(s"Filtering out table region: ${region.topRow}-${region.bottomRow}, ${region.leftCol}-${region.rightCol}")
        logger.debug(s"  - Size: ${region.width}x${region.height}, Area: ${region.area}")
        logger.debug(s"  - Density: $density (minimum required: $minimumDensity)")
        if (isTooBig) logger.debug(s"  - Content diversity: $contentDiversity")
      }
      
      // Keep tables unless they're too small or fail the large table checks
      !isTooSmall && passesLargeTableChecks
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
        if (row >= 0 && HeaderDetector.isHeaderRow(grid, row, region.leftCol, region.rightCol)) {
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
   * Eliminate overlapping tables by keeping the larger one
   */
  private def eliminateOverlaps(regions: List[TableRegion]): List[TableRegion] = {
    val result = mutable.ListBuffer[TableRegion]()
    val sortedRegions = regions.sortBy(-_.area) // Sort by decreasing area

    for (region <- sortedRegions) {
      val overlapsExisting = result.exists { existing =>
        val overlapArea = TableDetector.calculateOverlapArea(existing, region)
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
   * Find connected regions in the sheet using a BFS algorithm that allows for empty cell tolerance.
   * This enhanced implementation adds early pruning, density checks during growth, and
   * anchor-based boundary detection to prevent false positive large tables.
   *
   * @param grid      The sheet grid to analyze
   * @param threshHor Maximum number of empty cells to tolerate horizontally
   * @param threshVer Maximum number of empty cells to tolerate vertically
   * @param direction Search direction (0 for top-to-bottom, 1 for bottom-to-top)
   * @param config    Configuration with parameters for BFS behavior
   * @param anchorRows Optional set of anchor rows to use for boundary detection
   * @param anchorCols Optional set of anchor columns to use for boundary detection
   * @return List of detected table regions
   */
  def findConnectedRanges(
                           grid: SheetGrid,
                           threshHor: Int = 1,
                           threshVer: Int = 1,
                           direction: Int = 1,
                           config: SpreadsheetLLMConfig = SpreadsheetLLMConfig(),
                           anchorRows: Set[Int] = Set.empty,
                           anchorCols: Set[Int] = Set.empty
                         ): List[TableRegion] = {
    val height = grid.rowCount
    val width = grid.colCount

    // Track visited cells and tolerance counters
    val visited = Array.ofDim[Boolean](height, width)
    val horizontalCounter = Array.ofDim[Int](height, width) // Empty cell tolerance horizontally
    val verticalCounter = Array.ofDim[Int](height, width) // Empty cell tolerance vertically

    // Initialize counters with threshold values
    for (row <- 0 until height; col <- 0 until width) {
      horizontalCounter(row)(col) = threshHor
      verticalCounter(row)(col) = threshVer
    }

    // Define traversal range based on direction
    val rangeRow = if (direction == 0) 0 until height else (height - 1) to 0 by -1
    val rangeCol = 0 until width // Always left to right

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
          
          // Track non-empty cells for density calculation
          val nonEmptyCellsInRegion = mutable.Set[(Int, Int)]()
          if (cell.exists(!_.isEmpty)) {
            nonEmptyCellsInRegion.add((row, col))
          }

          // Track anchor bounds for early pruning
          val hasAnchors = config.enableAnchorCheckInBFS && 
                          (anchorRows.nonEmpty || anchorCols.nonEmpty)
          
          // Calculate anchor boundary constraints if enabled
          val anchorRowConstraints = if (hasAnchors && anchorRows.nonEmpty) {
            val minAnchorRow = anchorRows.min - config.anchorThreshold
            val maxAnchorRow = anchorRows.max + config.anchorThreshold
            Some((minAnchorRow, maxAnchorRow))
          } else None
          
          val anchorColConstraints = if (hasAnchors && anchorCols.nonEmpty) {
            val minAnchorCol = anchorCols.min - config.anchorThreshold
            val maxAnchorCol = anchorCols.max + config.anchorThreshold
            Some((minAnchorCol, maxAnchorCol))
          } else None
          
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
              
              // Calculate current region size and check if it's getting too large
              val currentWidth = maxCol - minCol + 1
              val currentHeight = maxRow - minRow + 1
              val currentArea = currentWidth * currentHeight
              
              // Calculate current density for early pruning
              val currentDensity = if (currentArea > 0) 
                nonEmptyCellsInRegion.size.toDouble / currentArea 
              else 
                0.0
              
              // Check if we should stop region growth due to size or density
              val isTooLarge = currentArea > config.maxTableSize
              val isDensityTooLow = currentDensity < config.minTableDensity / 2 && currentArea > 50
              
              // Stop BFS expansion if the region is too large or too sparse
              if (isTooLarge || isDensityTooLow) {
                if (config.verbose) {
                  if (isTooLarge) {
                    logger.debug(s"Stopping BFS growth due to large region size: $currentArea > ${config.maxTableSize}")
                  }
                  if (isDensityTooLow) {
                    logger.debug(s"Stopping BFS growth due to low density: $currentDensity (min: ${config.minTableDensity / 2})")
                  }
                }
                queue.clear() // Stop BFS expansion
              } else {
                // Apply anchor-based constraints if enabled
                var currentHorizontalTolerance = horizontalCounter(currentRow)(currentCol)
                var currentVerticalTolerance = verticalCounter(currentRow)(currentCol)
                
                // Check if current cell is far from anchor rows
                if (anchorRowConstraints.isDefined) {
                  val (minAnchorRow, maxAnchorRow) = anchorRowConstraints.get
                  if (currentRow < minAnchorRow || currentRow > maxAnchorRow) {
                    // Apply stricter thresholds when outside anchor row boundaries
                    val distanceFromBounds = min(
                      math.abs(currentRow - minAnchorRow), 
                      math.abs(currentRow - maxAnchorRow)
                    )
                    
                    // The further from anchor boundaries, the stricter we become
                    if (distanceFromBounds > 10) {
                      // Far outside anchor bounds - stop expansion
                      if (config.verbose) {
                        logger.debug(s"Stopping BFS at ($currentRow, $currentCol): far from anchor row bounds $minAnchorRow-$maxAnchorRow")
                      }
                      queue.clear()
                    } else if (distanceFromBounds > 5) {
                      // Moderately outside - reduce tolerance
                      currentVerticalTolerance = 0
                    } else {
                      // Slightly outside - reduce tolerance by half
                      currentVerticalTolerance = max(0, currentVerticalTolerance / 2)
                    }
                  }
                }
                
                // Same logic for anchor columns
                if (anchorColConstraints.isDefined && queue.nonEmpty) {
                  val (minAnchorCol, maxAnchorCol) = anchorColConstraints.get
                  if (currentCol < minAnchorCol || currentCol > maxAnchorCol) {
                    val distanceFromBounds = min(
                      math.abs(currentCol - minAnchorCol), 
                      math.abs(currentCol - maxAnchorCol)
                    )
                    
                    if (distanceFromBounds > 10) {
                      if (config.verbose) {
                        logger.debug(s"Stopping BFS at ($currentRow, $currentCol): far from anchor column bounds $minAnchorCol-$maxAnchorCol")
                      }
                      queue.clear()
                    } else if (distanceFromBounds > 5) {
                      currentHorizontalTolerance = 0
                    } else {
                      currentHorizontalTolerance = max(0, currentHorizontalTolerance / 2)
                    }
                  }
                }
                
                // If we haven't stopped BFS expansion, continue processing
                if (queue.nonEmpty) {
                  // Early check for large empty blocks
                  val hasLargeEmptyBlock = checkForLargeEmptyBlocks(grid, currentRow, currentCol, minRow, maxRow, minCol, maxCol)
                  if (hasLargeEmptyBlock) {
                    if (config.verbose) {
                      logger.debug(s"Stopping BFS at ($currentRow, $currentCol): large empty block detected")
                    }
                    queue.clear()
                  } else {
                    // Define direction vectors: right, left, down, up
                    val directions = Seq((0, 1), (0, -1), (1, 0), (-1, 0))
    
                    // Check all four directions
                    for (i <- 0 until 4) {
                      val nextRow = currentRow + directions(i)._1
                      val nextCol = currentCol + directions(i)._2
    
                      if (SheetGridUtils.isInBounds(nextRow, nextCol, height, width) && !visited(nextRow)(nextCol)) {
                        visited(nextRow)(nextCol) = true
    
                        // Check if next cell is empty
                        val nextCell = grid.cells.get((nextRow, nextCol))
                        val isEmpty = nextCell.forall(_.isEmpty)
    
                        if (isEmpty) {
                          // Reduce counter for empty cells using the adjusted tolerances
                          if (directions(i)._2 != 0) { // Horizontal movement
                            horizontalCounter(nextRow)(nextCol) = currentHorizontalTolerance - 1
                          }
                          if (directions(i)._1 != 0) { // Vertical movement
                            verticalCounter(nextRow)(nextCol) = currentVerticalTolerance - 1
                          }
                        } else {
                          // Add to non-empty cells set
                          nonEmptyCellsInRegion.add((nextRow, nextCol))
                          
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
                          } else {
                            // For non-border cells, use the adjusted tolerances
                            if (directions(i)._2 != 0) { // Horizontal
                              horizontalCounter(nextRow)(nextCol) = currentHorizontalTolerance
                            }
                            if (directions(i)._1 != 0) { // Vertical
                              verticalCounter(nextRow)(nextCol) = currentVerticalTolerance
                            }
                          }
                        }
    
                        queue.enqueue((nextRow, nextCol))
                      }
                    }
                  }
                }
              }
            }
          }

          // Add detected region if it's large enough and dense enough
          if (maxRow - minRow > 1) {
            val regionWidth = maxCol - minCol + 1
            val regionHeight = maxRow - minRow + 1
            val regionArea = regionWidth * regionHeight
            
            // Calculate actual density
            val density = nonEmptyCellsInRegion.size.toDouble / regionArea
            
            // Only add region if it meets density criteria
            if (density >= config.minTableDensity / 2) { // Use relaxed criteria here, filterLittleBoxes will apply stricter ones
              // Create region with empty anchor sets
              val tableRegion = TableRegion(
                minRow, maxRow, minCol, maxCol,
                Set.empty, Set.empty
              )
              
              if (config.verbose) {
                logger.debug(s"Found candidate region: ${tableRegion.topRow}-${tableRegion.bottomRow}, ${tableRegion.leftCol}-${tableRegion.rightCol}")
                logger.debug(s"  - Size: ${tableRegion.width}x${tableRegion.height}, Area: ${tableRegion.area}")
                logger.debug(s"  - Density: $density")
              }
              
              tableRegions += tableRegion
              
              // Mark all cells in this region as visited to avoid redundant processing
              for (r <- minRow to maxRow; c <- minCol to maxCol) {
                visited(r)(c) = true
              }
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
   * Check if there are large empty blocks ahead that should terminate BFS expansion.
   * This helps prevent BFS from bridging across large empty areas.
   */
  private def checkForLargeEmptyBlocks(
    grid: SheetGrid, 
    row: Int, 
    col: Int,
    minRow: Int,
    maxRow: Int,
    minCol: Int,
    maxCol: Int
  ): Boolean = {
    // Check for large empty blocks in all directions
    val width = maxCol - minCol + 1
    val height = maxRow - minRow + 1
    
    // Only check for large empty blocks in larger regions
    if (width < 10 && height < 10) return false
    
    // Check for empty block to the right
    val emptyColsRight = SheetGridUtils.countConsecutiveEmptyCols(
      grid, col, 1, math.max(minRow, row - 5), math.min(maxRow, row + 5), 10
    )
    
    // Check for empty block to the left
    val emptyColsLeft = SheetGridUtils.countConsecutiveEmptyCols(
      grid, col, -1, math.max(minRow, row - 5), math.min(maxRow, row + 5), 10
    )
    
    // Check for empty block below
    val emptyRowsBelow = SheetGridUtils.countConsecutiveEmptyRows(
      grid, row, 1, math.max(minCol, col - 5), math.min(maxCol, col + 5), 10
    )
    
    // Check for empty block above
    val emptyRowsAbove = SheetGridUtils.countConsecutiveEmptyRows(
      grid, row, -1, math.max(minCol, col - 5), math.min(maxCol, col + 5), 10
    )
    
    // Stop BFS if we find large empty blocks (8+ consecutive empty rows/cols)
    emptyColsRight >= 8 || emptyColsLeft >= 8 || emptyRowsBelow >= 8 || emptyRowsAbove >= 8
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
        if (!SheetGridUtils.isRowEmpty(grid, left, right, i)) {
          up = i
          foundNonEmpty = true
        }
      }

      // Trim bottom rows until non-empty row found
      foundNonEmpty = false
      for (i <- down to up by -1 if !foundNonEmpty) {
        if (!SheetGridUtils.isRowEmpty(grid, left, right, i)) {
          down = i
          foundNonEmpty = true
        }
      }

      // Trim left columns until non-empty column found
      foundNonEmpty = false
      for (j <- left to right if !foundNonEmpty) {
        if (!SheetGridUtils.isColEmpty(grid, up, down, j)) {
          left = j
          foundNonEmpty = true
        }
      }

      // Trim right columns until non-empty column found
      foundNonEmpty = false
      for (j <- right to left by -1 if !foundNonEmpty) {
        if (!SheetGridUtils.isColEmpty(grid, up, down, j)) {
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