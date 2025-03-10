# Table Detection Enhancements for SpreadsheetLLM

## 1. Overview of Key Goals

1. **Reduce False-Positive Large Tables**: Improve BFS-based region growth and trimming to keep large but sparse regions from being over-detected.
2. **Unify BFS With Anchor-Based Detection**: Rather than toggling between strategies for large vs. small sheets, combine anchor detection with BFS so that anchor presence influences BFS termination where appropriate.
3. **Enhance Cohesion & Header Detection**: Expand the coverage of cohesion region detection to unify border, formatting, formula relationships, and multi-level headers as "hard boundaries" that BFS does not cross.
4. **Improve Performance & Scalability**: Use concurrency carefully and prune early wherever possible to avoid expensive expansions. Support large spreadsheets without a massive blow-up in memory usage.

## 2. Analysis of Current Issues

Based on analysis of our codebase and comparison with the original C# SheetCompressor implementation, we've identified several issues:

1. **Overly Aggressive BFS Search**:
   - Current BFS implementation in `RegionGrowthDetector.findConnectedRanges` uses a high empty cell tolerance (default via `minGapSize` is 3)
   - Causes tables to expand beyond natural boundaries by crossing large gaps
   - Missing density checks during region growth

2. **Insufficient Boundary Refinement**:
   - Current boundary trimming is basic compared to the original implementation
   - Missing specialized trimming passes (like `SparseBoundariesTrim`, `UpHeaderTrim`, etc.)
   - Need more aggressive empty edge removal

3. **Missing Critical Features From Original Implementation**:
   - No cohesion region detection to identify areas that should stay together
   - No formula correlation analysis to leverage formula relationships
   - Limited use of borders and cell formatting for boundary detection
   - Basic header detection compared to the sophisticated `HeaderReco.cs`
   - Missing specialized filters like `OverlapCohensionFilter`

4. **Configuration and Strategy Issues**:
   - Large sparse tables are always kept if they're big, leading to false positives
   - No clear strategy for different sheet types (unlike the original that switches between `RegionGrowthDetect` and `TableSenseDetect`)

## 3. Phased Improvement Plan

### Phase 1: BFS and Core Algorithm Enhancements

1. **Adjust BFS for Early Pruning**
   - **Early Cutoff Using Anchors**: If any row or column is significantly outside the bounding range of identified anchors, the BFS should stop expanding there. In practice, we combine BFS with anchor checks:
   ```scala
   // Pseudo-code: if config.enableAnchorCheckInBFS
   // and we detect row/col is beyond anchor boundary or row # is not an anchor
   // or within anchorThreshold => prune BFS expansion
   
   // Implementation in findConnectedRanges
   if (config.enableAnchorCheckInBFS && anchorRows.nonEmpty) {
     val minAnchorRow = anchorRows.min - config.anchorThreshold
     val maxAnchorRow = anchorRows.max + config.anchorThreshold
     
     // If current expansion is too far from any anchor row, apply stricter thresholds or stop
     if (currentRow < minAnchorRow || currentRow > maxAnchorRow) {
       // Either apply stricter threshold or stop BFS expansion completely
       if (currentRow < minAnchorRow - 10 || currentRow > maxAnchorRow + 10) {
         queue.clear() // Stop BFS expansion if very far from anchors
       } else {
         // Apply stricter thresholds when moderately outside anchor bounds
         emptyToleranceHorizontal = 0
         emptyToleranceVertical = 0
       }
     }
   }
   ```
   - **Low Threshold by Default**: Lower default threshHor and threshVer to 1. For highly fragmented sheets, allow an override in SpreadsheetLLMConfig.
   - **Density Check During Growth**: Keep track of ratio of empty vs. non-empty cells within the partial bounding box. If the ratio of empty cells grows beyond a threshold early on, prune BFS expansion.

2. **Improve Region Trimming**
   - **Multiple Passes**: Run multiple passes of trimming, but add new heuristics:
   ```scala
   // Improved trimming with multiple specialized passes
   def trimRegions(regions: List[TableRegion], grid: SheetGrid): List[TableRegion] = {
     var result = regions
     
     // Pass 1: Basic empty edge trimming
     result = trimEmptyEdges(result, grid)
     
     // Pass 2: Sparse interior trimming - identify and split regions with large empty sub-areas
     result = trimSparseInterior(result, grid)
     
     // Pass 3: Border-driven trimming - trim edges that don't align with borders
     result = trimByBorders(result, grid)
     
     // Pass 4: Anchor-based trimming - align regions with anchor rows/columns
     result = trimToAnchors(result, grid, anchorRows, anchorCols)
     
     result
   }
   ```
   - **Sparse-Interior Trim**: Identify large sub-areas within the bounding box that have no data. This is especially helpful if the BFS "bridged" around an empty patch.
   - **Border-Driven Trim**: If the region's edges do not contain a border or anchor row/column in more than 80% of that edge, consider trimming the edge row/column.

3. **Configuration Parameters**
   - **Anchor-BFS Toggle**: enableAnchorCheckInBFS: Boolean to unify anchor logic with BFS expansions.
   - **MaxRegionArea vs. MaxTableSize**: Keep maxTableSize, but also allow a separate maxRegionArea for BFS expansions. This helps avoid confusion if a user wants to set them differently.

4. **Content Diversity Analysis**
   ```scala
   def calculateContentDiversity(grid: SheetGrid, region: TableRegion): Double = {
     // Get cells in the region
     val cells = grid.cells.filter { case ((r, c), _) =>
       r >= region.topRow && r <= region.bottomRow &&
       c >= region.leftCol && c <= region.rightCol
     }.values.toList
     
     if (cells.isEmpty) return 0.0
     
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
   }
   ```

### Phase 2: Cohesion & Formula Improvements

1. **CohesionRegionDetector**
   - **Factor in Row/Column Spans**: For merged cells, store row/column spans in SheetGrid so we can quickly unify them into large merged-cell blocks. The BFS (or RegionGrowth) can unify these blocks as a single unit.
   ```scala
   case class CohesionRegion(
     topRow: Int,
     bottomRow: Int,
     leftCol: Int,
     rightCol: Int,
     cohesionStrength: Double,  // How strongly this region should be preserved (0.0-1.0)
     cohesionType: CohesionType // The reason this region should stay together
   ) {
     def width: Int = rightCol - leftCol + 1
     def height: Int = bottomRow - topRow + 1
     def area: Int = width * height
     
     def contains(row: Int, col: Int): Boolean =
       row >= topRow && row <= bottomRow && col >= leftCol && col <= rightCol
       
     def overlaps(other: CohesionRegion): Boolean =
       !(rightCol < other.leftCol || leftCol > other.rightCol || 
         bottomRow < other.topRow || topRow > other.bottomRow)
   }
   ```

   - **Smarter Overlap Handling**: If a portion of a BFS region spans multiple CohesionRegions, do not automatically merge them. Instead, treat each cohesion region's boundaries as a partial "fence." Where BFS tries to cross a fence, reduce tolerance or require that anchors or borders are present.

2. **Header & Anchor Cross-Check**
   - **Anchor as Proxy for Headers**: Once multi-level headers are detected, mark them as special anchor rows (or anchor columns). This ensures BFS or TableSense detection sees them as boundaries.
   ```scala
   def detectMultiLevelHeaders(grid: SheetGrid, region: TableRegion, dim: Dimension): Set[Int] = {
     val result = collection.mutable.Set[Int]()
     
     dim match {
       case Dimension.Row =>
         // Check for header rows at the top of region
         val checkRows = math.min(region.bottomRow - region.topRow + 1, 5)
         for (r <- 0 until checkRows) {
           val rowIndex = region.topRow + r
           val headerScore = isHeaderRow(grid, rowIndex, region.leftCol, region.rightCol)
           
           if (headerScore > 0.5) {
             result.add(rowIndex)
           }
         }
         
       case Dimension.Column =>
         // Similar logic for columns
         // ...
     }
     
     result.toSet
   }
   ```
   - **Refined Header Detection**: Keep the scoring approach but incorporate:
     - Weighted check for "common header synonyms" (e.g., "total", "sum", "average", "year", "month", "quarter")
     - Additional pattern checks for enumerations (like "Q1, Q2, Q3, Q4," or "FY2019, FY2020…") which often occur in header rows.

3. **Formula Correlation**
   - **Partial Range Overlap**: For formulas referencing wide ranges, avoid automatically pulling in empty columns on both sides. Instead, create sub-regions around cells that are actually used in the formula references.
   ```scala
   def smartFormulaRangeAnalysis(formulaRange: ExcelRange, grid: SheetGrid): TableRegion = {
     // Only include non-empty portions of the referenced range
     val nonEmptyCells = for {
       r <- formulaRange.topRow to formulaRange.bottomRow
       c <- formulaRange.leftCol to formulaRange.rightCol
       if grid.cells.get((r, c)).exists(!_.isEmpty)
     } yield (r, c)
     
     if (nonEmptyCells.isEmpty) {
       // Fall back to original range if all cells are empty
       TableRegion(formulaRange.topRow, formulaRange.bottomRow, 
                  formulaRange.leftCol, formulaRange.rightCol, 
                  Set.empty, Set.empty)
     } else {
       // Create region based on non-empty cells
       val rows = nonEmptyCells.map(_._1)
       val cols = nonEmptyCells.map(_._2)
       TableRegion(rows.min, rows.max, cols.min, cols.max, Set.empty, Set.empty)
     }
   }
   ```
   - **Cell-by-Cell vs. Range Averages**: If a formula references A1:D100 but 80% of it is empty, treat only the non-empty interior as relevant for bounding. This prevents bloating the final region around formula references.

### Phase 3: TableSense & Anchor Integration

1. **Unify BFS + Anchor**
   - **Combined BFS**: Instead of having a pure BFS for large sheets and TableSenseDetector for smaller ones, combine them:
     1. Identify anchor rows/columns.
     2. Start BFS expansions from each anchor row/column, using anchor-based fences for row/col expansions.
     3. If a BFS expansion tries to jump an anchor, apply stricter thresholds.
   ```scala
   def unifiedTableDetection(grid: SheetGrid, anchorRows: Set[Int], anchorCols: Set[Int]): List[TableRegion] = {
     val regions = mutable.ListBuffer[TableRegion]()
     
     // Start expansions from each anchor row/column intersection
     for {
       row <- anchorRows
       col <- anchorCols
       if grid.cells.contains((row, col)) && !grid.cells((row, col)).isEmpty
     } {
       // Run BFS from this anchor point with anchor awareness
       val region = runAnchorAwareBFS(grid, row, col, anchorRows, anchorCols)
       regions += region
     }
     
     // Run additional detection for areas without anchors
     val anchorRegionCells = regions.flatMap(r => 
       for {
         r <- r.topRow to r.bottomRow
         c <- r.leftCol to r.rightCol
       } yield (r, c)
     ).toSet
     
     // Find starting points for cells not covered by anchor-based regions
     val nonCoveredCells = grid.cells.keys.filterNot(anchorRegionCells.contains).toList
     
     // Run constrained BFS from these points
     for (cell <- nonCoveredCells) {
       if (!grid.cells(cell).isEmpty) {
         val region = runConstrainedBFS(grid, cell._1, cell._2, anchorRows, anchorCols)
         if (region.area >= minTableSize) {
           regions += region
         }
       }
     }
     
     // Apply filtering and merging
     mergeAndFilterRegions(regions.toList, grid)
   }
   ```
   - **Save a Second Pass**: This eliminates the need to run BFS, then anchor-based pruning again. We get a single integrated pass that respects anchors from the beginning.

2. **Adaptive Gap Handling**
   - For large sheets, we keep BFS expansions local to anchor zones by default. For small sheets, expand more freely. Provide a smoothing threshold so that if the sheet has no anchors in a large area, BFS stops quickly.

3. **Multi-Table Detection**
   - After BFS completes, we keep sub-segmentation to detect multiple sub-tables inside a BFS region using row/col-level anchor checks or advanced TableSenseDetector.
   ```scala
   def detectSubTables(region: TableRegion, grid: SheetGrid): List[TableRegion] = {
     // Skip small regions - they're likely atomic already
     if (region.width < 20 && region.height < 20) return List(region)
     
     // Look for internal anchor rows that might divide tables
     val internalAnchors = detectInternalAnchorRows(region, grid)
     if (internalAnchors.isEmpty) return List(region)
     
     // Sort anchors by position
     val sortedAnchors = internalAnchors.toList.sorted
     
     // Create sub-regions based on internal anchors
     val subRegions = mutable.ListBuffer[TableRegion]()
     var currentStart = region.topRow
     
     for (anchor <- sortedAnchors) {
       // If gap between current start and anchor is significant
       if (anchor - currentStart > 3) {
         // Create sub-region from currentStart to just before anchor
         subRegions += TableRegion(
           currentStart, anchor - 1,
           region.leftCol, region.rightCol,
           Set(currentStart), // Mark start as anchor
           region.anchorCols
         )
       }
       currentStart = anchor + 1 // Start next region after the anchor
     }
     
     // Handle last segment if any
     if (currentStart <= region.bottomRow) {
       subRegions += TableRegion(
         currentStart, region.bottomRow,
         region.leftCol, region.rightCol,
         Set(currentStart), // Mark start as anchor
         region.anchorCols
       )
     }
     
     // Apply similar logic for horizontal division using anchor columns
     // ...
     
     subRegions.toList
   }
   ```
   - For each BFS region, run a simplified TableSenseDetector to break it into sub-tables if we see internal anchor rows/columns.

### Phase 4: Additional Filtering & Overlap Handling

1. **Overlap & Merge**
   - Similar to your plan, merge overlapping table regions if they exceed a certain overlap ratio. But do a two-step approach:
   ```scala
   def mergeOverlappingRegions(regions: List[TableRegion]): List[TableRegion] = {
     // First pass: merge exact matches and complete containment
     val firstPassResult = mergeExactAndContainedRegions(regions)
     
     // Second pass: merge regions with significant overlap (>50%) and similar characteristics
     var result = firstPassResult
     var mergeOccurred = true
     
     while (mergeOccurred) {
       mergeOccurred = false
       val currentRegions = result
       result = mutable.ListBuffer[TableRegion]()
       val processed = mutable.Set[Int]()
       
       for (i <- currentRegions.indices if !processed.contains(i)) {
         var current = currentRegions(i)
         processed += i
         
         // Look for regions to merge with current one
         for (j <- currentRegions.indices if !processed.contains(j)) {
           val other = currentRegions(j)
           
           // Calculate overlap
           val overlapArea = calculateOverlap(current, other)
           val smallerArea = math.min(current.area, other.area)
           val overlapRatio = overlapArea.toDouble / smallerArea
           
           // Check if they share format characteristics or anchors
           val shareAnchors = (current.anchorRows.intersect(other.anchorRows).nonEmpty ||
                             current.anchorCols.intersect(other.anchorCols).nonEmpty)
           val shareFormatting = haveSimilarFormatting(current, other, grid)
           
           // Merge if overlap and other conditions are met
           if (overlapRatio > 0.5 && (shareAnchors || shareFormatting)) {
             current = mergeRegions(current, other)
             processed += j
             mergeOccurred = true
           }
         }
         
         result += current
       }
     }
     
     result.toList
   }
   ```
   - **Exact Merge Pass**: Merge regions that fully contain each other or are identical.
   - **Heuristic Merge Pass**: If two large regions overlap more than ~50% and share consistent formatting or anchor rows, unify them.

2. **Cohesion Overlap**
   - If a table region partially overlaps a strong CohesionRegion, either clamp the table region to that boundary or unify them in a single region. For instance, a pivot table region with strong formatting is rarely partial.

3. **Cohesion Confidence**
   - Add a cohesion confidence weighting factor. For example, a region discovered purely from formulas might have a moderate strength (0.5), while merged cells are 0.9. During overlap resolution, pick the bounding approach that yields the highest net confidence.
   ```scala
   def resolveOverlappingCohesionRegions(tableRegion: TableRegion, 
                                       cohesionRegions: List[CohesionRegion]): TableRegion = {
     // Find overlapping cohesion regions
     val overlapping = cohesionRegions.filter(cr => 
       regionsOverlap(tableRegion, cr) && 
       !tableRegion.contains(cr) && 
       !cr.contains(tableRegion))
     
     if (overlapping.isEmpty) return tableRegion
     
     // Group by cohesion type and find strongest
     val byType = overlapping.groupBy(_.cohesionType)
     
     // For high confidence cohesion (merged cells, borders), clamp the table
     val highConfidenceOverlaps = overlapping.filter(_.cohesionStrength > 0.8)
     
     if (highConfidenceOverlaps.nonEmpty) {
       // Clamp the table region to respect these high confidence boundaries
       val adjustedRegion = clampRegionToCohesionBoundaries(tableRegion, highConfidenceOverlaps)
       return adjustedRegion
     }
     
     // For medium confidence, consider expanding the table to include them
     val mediumConfidenceOverlaps = overlapping.filter(cr => 
       cr.cohesionStrength > 0.5 && cr.cohesionStrength <= 0.8)
     
     if (mediumConfidenceOverlaps.nonEmpty) {
       // Expand table to include these related areas
       return expandRegionToIncludeCohesion(tableRegion, mediumConfidenceOverlaps)
     }
     
     // For low confidence cohesion, keep the table as is
     tableRegion
   }
   ```

### Phase 5: Performance & Stability

1. **Concurrency**
   - Restrict concurrency to per-sheet processing. Within a single sheet, BFS expansions that operate in parallel can cause high memory usage. Instead, run each sheet's detection sequentially or with small concurrency if the sheet is extremely large.

2. **Early Pruning**
   - Use early checks for "empty row blocks." If a BFS sees 20 consecutive empty rows in a large sheet, cut off expansion to the next region.
   ```scala
   def shouldTerminateBFS(grid: SheetGrid, currentRow: Int, currentCol: Int, 
                        direction: (Int, Int), config: SpreadsheetLLMConfig): Boolean = {
     // Check for large empty areas ahead
     val (rowDir, colDir) = direction
     
     if (rowDir != 0) {
       // Check for consecutive empty rows
       val emptyRows = (1 to 20).takeWhile { step =>
         val checkRow = currentRow + (step * rowDir)
         if (checkRow < 0 || checkRow >= grid.rowCount) 
           false
         else
           isRowEmpty(grid, checkRow, currentCol - 5, currentCol + 5)
       }
       
       if (emptyRows.size >= 10) {
         // Found 10+ consecutive empty rows
         return true
       }
     }
     
     // Similar check for columns
     if (colDir != 0) {
       // ...
     }
     
     false
   }
   ```

3. **Caching & Re-Use**
   - Cache intermediate results like border analysis, empty row detection, formula reference sets. Re-using them in BFS, header detection, and cohesion detection is often cheaper than recalculating.
   ```scala
   class TableDetectionContext(grid: SheetGrid) {
     // Cache frequently used information
     lazy val emptyRows: Set[Int] = detectEmptyRows(grid)
     lazy val emptyCols: Set[Int] = detectEmptyCols(grid)
     lazy val borderRows: Set[Int] = detectRowsWithBorders(grid)
     lazy val borderCols: Set[Int] = detectColsWithBorders(grid)
     lazy val formulaReferences: Map[(Int, Int), Set[(Int, Int)]] = extractFormulaReferences(grid)
     lazy val headerRowScores: Map[Int, Double] = calculateHeaderRowScores(grid)
     lazy val headerColScores: Map[Int, Double] = calculateHeaderColumnScores(grid)
     
     // Methods to compute the cached values
     // ...
   }
   ```

### Phase 6: Testing, Iteration, and Maintenance

1. **Unit Tests**
   - For BFS expansions, verify that large patches of empties truly get pruned.
   - For anchors, confirm that rows or columns flagged as anchors block BFS expansions.
   - For advanced header detection, test multi-level headers with numeric row headers, text row headers, date columns, etc.

2. **Integration & Regression Tests**
   - Create a suite of real-world spreadsheets (some from the original C# solution) to ensure that the new approach doesn't degrade on edge cases like "stacked pivot tables," "multi-level headers," or "semi-structured forms."

3. **Iterate**
   - After seeing real performance and compression outcomes, calibrate thresholds (e.g., maxTableSize, minTableDensity, anchor BFS thresholds, formula correlation strength) to find an optimal tradeoff between coverage and false positives.

## 4. Revised Timeline & Milestones

| Phase | Task | Priority | Est. Time |
|-------|------|----------|-----------|
| 1 | BFS + RegionGrowth Enhancements (Anchor synergy, density) | High | 3 days |
| 1 | Trimming Logic & Config Updates | High | 2 days |
| 2 | CohesionRegionDetector (border, format, merges, formula) | High | 4 days |
| 2 | OverlapCohesionFilter & Merging Overlapping CohesionRegions | High | 2 days |
| 3 | Unify BFS + Anchor + TableSense, sub-table partitioning | Medium | 3 days |
| 4 | Overlap + Merge pass with cohesion weighting | Medium | 2 days |
| 5 | Performance Tuning (concurrency, early pruning) | Medium | 3 days |
| 6 | Testing & Final Adjustments | High | 4 days |
| **Total** | | | **23 days** |

## 5. Summary of Improvements

Compared to the original implementation, this revised approach:
1. **Deeply Integrates Anchors With BFS**: Eliminates the "either BFS or anchor" approach by using anchors as partial fences (or expansions seeds) within BFS.
2. **Cohesion Regions are Respected**: Rather than only merging them after the fact, BFS sees them as unbreakable boundaries. This prevents BFS from bridging over strongly cohesive areas.
3. **Multi-Pass Sub-Table Splitting**: Instead of letting BFS produce giant bounding boxes, run a TableSense partition pass within each BFS bounding box. This step ensures large bounding boxes get subdivided if the region internally contains multiple "sub-tables."
4. **Better Performance**: Introduces early pruning for large empties and concurrency primarily at the per-sheet level, lowering risk of memory blow-ups and complexity.
5. **Expanded Heuristics**: Encourages more robust formula correlation, improved header detection, and an explicit measure of "cohesion confidence" to unify or clamp overlapping table regions.

By unifying BFS expansions with anchor checks, advanced header detection, and robust cohesion detection (merged cells, formula references, formatting), we can produce far fewer false positives while capturing the same structural complexity that the original SheetCompressor solution aimed for.