# Detailed Description of SheetCompressor Framework Image

The image illustrates the SheetCompressor framework, showing a step-by-step process of how spreadsheet data is compressed through three techniques.

## Overview
The image is divided into three main sections from left to right, representing the three sequential compression techniques:
1. Structural-anchor-based Extraction
2. Inverted-index Translation
3. Data-format-aware Aggregation

## Left Section: Structural-anchor-based Extraction
This section shows three stages (a, b, c) of the first compression technique:

- Image (a): The original spreadsheet with many rows and columns. It appears as a dense grid with lots of data cells. At the bottom is a small table showing sample data with companies (MindMeld, AquaQuest) and numerical values.

- Image (b): The same spreadsheet with highlighted "candidate boundary" areas marked in yellow. Green text labels explain this step: "Propose candidate boundary as structural anchors"

- Image (c): A significantly reduced spreadsheet after removing distant rows/columns. Text explains: "Remove rows/cols that are k row/cols away from anchors". The resulting sheet is much smaller (24×8) with proper coordinate rearrangement (noted at top).

Below image (c) is a text example of the encoded data format with actual values:
```
|A4, El Dorado|B4, QuantumMind|C4, 1,172,295|D4, 20-Aug|
|F4, |F4, QuantumMind|G4, |H4, |\n
|A5, Lemuria|B5, DreamSculpt|C5, |D5, 20-Aug|
|E5, |F5, Atlantis|G5, 1,797,915|H5, 9.13%|\n
...
```

## Middle Section: Inverted-index Translation
This shows the output of the second compression technique:

- A visualization of data structured in a different format, with values grouped together
- Sample categories like "Sub Region", "Atlantis", "QuantumMind" shown as boxes with cell references
- JSON-like encoding format shown below:
```
{
  "Sub Region": A1,
  "Atlantis": A2,A7,F5,A10,...
  "QuantumMind": B2:B4,F4,
  "20-Aug": D2:D18,D21:D23,
  "1064955": C2,
  "19700822": G16,
  "9.13%": H5,
  "100.00%": H16, ...
}
```

## Right Section: Data-format-aware Aggregation
This shows the final compression technique:

- Similar visualization with boxes but now with data types instead of specific values
- Values have been replaced with format types (e.g., "yy-mm", "IntNum", "Percentage")
- Final JSON-like encoding format:
```
{
  "Sub Region": A1,F1,
  "QuantumMind": B2:B4,F4,
  "yy-mm": D2:D18,D21:D23,
  "IntNum": C2:C4,C6:C12,...
  "Percentage": H5:H6,H8:H9,...
  ...
}
```

## Bottom Labels
Clear labels identify each of the three main techniques:
1. Structural-anchor-based Extraction
2. Inverted-index Translation
3. Data-format-aware Aggregation

The overall visual demonstrates how a large spreadsheet with 576 rows and 23 columns (requiring 61,240 tokens in vanilla encoding) is progressively compressed to just 708 tokens while preserving its essential structure and meaning.

# Anchor Pseudocode

```
function DetectTables(inputSheet, config):
    // Initialize core data structures
    sheet = PreprocessSheet(inputSheet)
    candidateBoxes = []
    
    // Decide detection strategy based on sheet size
    if sheet.Height > 1000 or sheet.Height * sheet.Width > 30000:
        candidateBoxes = RegionGrowthDetect(sheet, config)
    else:
        candidateBoxes = TableSenseDetect(sheet, config)
    
    // Post-process and return results
    if config.eliminateOverlaps:
        candidateBoxes = EliminateOverlaps(candidateBoxes)
    candidateBoxes = RankBoxesByLocation(candidateBoxes)
    return candidateBoxes

function PreprocessSheet(inputSheet):
    // Extract features from sheet
    sheet = new SheetMap()
    sheet.Height = inputSheet.Height
    sheet.Width = inputSheet.Width
    
    // Extract cell features: borders, content, formats, formulas
    ExtractCellFeatures(inputSheet, out sheet.featureMap, out sheet.contentStrs, out sheet.formulaStrs)
    
    // Prepare pivot table information
    sheet.pivotBoxes = ExtractPivotTables(inputSheet)
    
    // Prepare merged cell information
    sheet.mergeBoxes = ConvertMergedRanges(inputSheet.MergedRegions)
    
    // Process formulas to identify reference ranges
    sheet.formulaRanges = PreProcessFormulas(sheet.formulaStrs)
    
    // Calculate value maps for different cell properties
    sheet.valueMapContent = ComputeValueMap(cell => (cell.HasFormula ? 2 : 0) + (cell.MarkText ? 2 : 0))
    sheet.valueMapContentExist = ComputeValueMap(cell => cell.HasFormula || cell.MarkText ? 2 : 0)
    sheet.valueMapBorder = ComputeValueMap(cell => {
        borderCount = Convert.ToInt32(cell.HasBottomBorder) + Convert.ToInt32(cell.HasTopBorder) + 
                      Convert.ToInt32(cell.HasLeftBorder) + Convert.ToInt32(cell.HasRightBorder)
        return (borderCount >= 3 ? borderCount - 1 : borderCount) * 2
    })
    sheet.valueMapBorderCol = ComputeValueMap(cell => cell.HasLeftBorder || cell.HasRightBorder ? 2 : 0)
    sheet.valueMapBorderRow = ComputeValueMap(cell => cell.HasBottomBorder || cell.HasTopBorder ? 2 : 0)
    sheet.valueMapColor = ComputeValueMap(cell => cell.HasFillColor ? 2 : 0)
    sheet.valueMapAll = ComputeValueMap((i, j) => Min(
        sheet.valueMapContent[i, j] + sheet.valueMapBorder[i, j] + sheet.valueMapColor[i, j], 16))
    
    // Compute prefix sums for efficient submatrix queries
    sheet.sumContent = CalculatePrefixSums(sheet.valueMapContent)
    sheet.sumContentExist = CalculatePrefixSums(sheet.valueMapContentExist)
    sheet.sumBorder = CalculatePrefixSums(sheet.valueMapBorder)
    sheet.sumBorderCol = CalculatePrefixSums(sheet.valueMapBorderCol)
    sheet.sumBorderRow = CalculatePrefixSums(sheet.valueMapBorderRow)
    sheet.sumColor = CalculatePrefixSums(sheet.valueMapColor)
    sheet.sumAll = CalculatePrefixSums(sheet.valueMapAll)
    
    return sheet

function RegionGrowthDetect(sheet, config):
    // Simple connected component detection
    boxes = FindConnectedRanges(sheet.contentStrs, sheet.valueMapBorder, threshHor=1, threshVer=1)
    
    // Filter the little and sparse boxes
    boxes = LittleBoxesFilter(boxes, sheet)
    
    // Filter the boxes overlapping pivot tables
    boxes = OverlapPivotFilter(boxes, sheet)
    
    // Refine the upper boundary of boxes, especially for headers
    boxes = RefineHeaderAreas(boxes, sheet)
    
    // Refine all boundaries
    boxes = RefineBoundaries(boxes, sheet)
    
    // Handle headers
    boxes = RetrieveUpHeader(boxes, sheet, step=1)
    boxes = RetrieveUpHeader(boxes, sheet, step=2)
    
    // Handle left headers
    boxes = RetrieveLeftHeader(boxes, sheet)
    boxes = RetrieveLeft(boxes, sheet, step=1)
    boxes = RetrieveLeft(boxes, sheet, step=2)
    
    return boxes

function TableSenseDetect(sheet, config):
    // 1. Preparation phase
    sheet.ProposeBoundaryLines()  // Find potential table boundaries using row/col diffs
    sheet.CohesionDetection()     // Find regions that should stay together (borders, merged, colors)
    blockRegions = sheet.GenerateBlockRegions()  // Split sheet into disconnected blocks
    
    // 2. Generate candidate boxes using multiple methods
    regionGrowthBoxes = []
    // Try multiple thresholds for region growth to catch different table types
    for threshHor = 1 to 7:
        threshVerLimit = (threshHor < 3) ? 3 : 2
        for threshVer = 1 to threshVerLimit:
            // Try with border info
            boxes = FindConnectedRanges(sheet.contentStrs, sheet.valueMapBorder, threshHor, threshVer, direction=0)
            foreach box in boxes:
                if GeneralFilter(box, sheet):
                    regionGrowthBoxes.Add(box)
            
            // If not a large sheet, also try without border info
            if not (sheet.Height * sheet.Width > 10000):
                boxes = FindConnectedRanges(sheet.contentStrs, null, threshHor, threshVer, direction=0)
                foreach box in boxes:
                    if GeneralFilter(box, sheet):
                        regionGrowthBoxes.Add(box)
    
    // 3. Process each block region separately
    sheetBoxes = []
    foreach blockRegion in blockRegions:
        // Generate candidates from boundary intersections
        boxes = GenerateRawCandidateBoxes(blockRegion, sheet)
        
        // Add overlapping region growth boxes
        foreach box in regionGrowthBoxes:
            if Overlaps(blockRegion, box):
                boxes.Add(box)
        
        // Remove duplicates
        boxes = RemoveDuplicates(boxes)
        
        // Refine candidates for this block
        boxes = RefineBlockCandidates(boxes, sheet)
        
        sheetBoxes.AddAll(boxes)
    
    // 4. Apply sheet-wide refinement and return
    candidateBoxes = RemoveDuplicates(sheetBoxes)
    candidateBoxes = RankBoxesByLocation(candidateBoxes)
    candidateBoxes = RefineCandidatesGlobally(candidateBoxes, sheet)
    
    return candidateBoxes

function FindConnectedRanges(cells, borderInfo, threshHor, threshVer, direction=1):
    // Initialize tracking data structures
    height = cells.GetLength(0)
    width = cells.GetLength(1)
    visited = new bool[height, width]
    horizontalCounter = new int[height, width] // For empty cell tolerance in horizontal direction
    verticalCounter = new int[height, width]   // For empty cell tolerance in vertical direction
    
    // Fill counters with threshold values
    for row = 0 to height-1:
        for col = 0 to width-1:
            horizontalCounter[row, col] = threshHor
            verticalCounter[row, col] = threshVer
    
    // Define range for traversal (can be top-to-bottom or bottom-to-top)
    rangeRow = direction == 0 ? [0...height-1] : [height-1...0]
    rangeCol = [0...width-1]  // Always left to right
    
    tableRegions = []
    
    // Scan sheet for unvisited non-empty cells
    for row in rangeRow:
        for col in rangeCol:
            if visited[row, col] or IsEmpty(cells[row, col]):
                continue
            
            // Breadth-first search to find connected region
            queue = new Queue()
            queue.Enqueue((row, col))
            visited[row, col] = true
            
            minRow = maxRow = row
            minCol = maxCol = col
            
            while queue is not empty:
                currentRow, currentCol = queue.Dequeue()
                
                // Skip if tolerance exhausted
                if horizontalCounter[currentRow, currentCol] == 0 or 
                   verticalCounter[currentRow, currentCol] == 0:
                    continue
                
                // Update bounds of connected region
                minRow = Min(minRow, currentRow)
                maxRow = Max(maxRow, currentRow)
                minCol = Min(minCol, currentCol)
                maxCol = Max(maxCol, currentCol)
                
                // Define direction vectors: right, left, down, up
                directions = [(0,1), (0,-1), (1,0), (-1,0)]
                
                // Check four neighboring directions
                for i = 0 to 3:
                    nextRow = currentRow + directions[i].row
                    nextCol = currentCol + directions[i].col
                    
                    if IsInBounds(nextRow, nextCol) and not visited[nextRow, nextCol]:
                        visited[nextRow, nextCol] = true
                        
                        // Check if the next cell is empty
                        if IsEmpty(cells[nextRow, nextCol]):
                            // Reduce counter for empty cells
                            if directions[i].col != 0:  // Horizontal movement
                                horizontalCounter[nextRow, nextCol] = horizontalCounter[currentRow, currentCol] - 1
                            if directions[i].row != 0:  // Vertical movement
                                verticalCounter[nextRow, nextCol] = verticalCounter[currentRow, currentCol] - 1
                        else:
                            // If border exists, reset tolerance counters
                            if borderInfo != null and HasBorder(borderInfo, nextRow, nextCol):
                                if directions[i].col != 0:
                                    horizontalCounter[nextRow, nextCol] = threshHor
                                if directions[i].row != 0:
                                    verticalCounter[nextRow, nextCol] = threshVer
                        
                        queue.Enqueue((nextRow, nextCol))
            
            // Add detected region if it's large enough and mark cells as visited
            if maxRow - minRow > 1:
                tableRegion = new TableRegion(minRow+1, maxRow+1, minCol+1, maxCol+1)  // Convert to 1-based
                tableRegions.Add(tableRegion)
                
                // Mark all cells in this region as visited to avoid redundant processing
                for r = minRow to maxRow:
                    for c = minCol to maxCol:
                        visited[r, c] = true
    
    // Trim empty edges from regions (3 passes for stability)
    for i = 0 to 2:
        tableRegions = TrimEmptyEdges(tableRegions, cells)
    
    return tableRegions

function TrimEmptyEdges(regions, cells):
    trimmedRegions = []
    
    foreach region in regions:
        up = region.top
        down = region.bottom
        left = region.left
        right = region.right
        
        // Trim top rows until non-empty row found
        for i = up to down:
            if not IsRowEmpty(cells, left, right, i):
                up = i
                break
                
        // Trim bottom rows until non-empty row found
        for i = down downto up:
            if not IsRowEmpty(cells, left, right, i):
                down = i
                break
                
        // Trim left columns until non-empty column found
        for j = left to right:
            if not IsColEmpty(cells, up, down, j):
                left = j
                break
                
        // Trim right columns until non-empty column found
        for j = right downto left:
            if not IsColEmpty(cells, up, down, j):
                right = j
                break
        
        // Add trimmed region if it's still valid
        if left <= right and up <= down:
            trimmedRegions.Add(new TableRegion(up, down, left, right))
    
    return trimmedRegions

function GenerateRawCandidateBoxes(blockRegion, sheet):
    candidates = []
    
    // Extract boundary lines relevant to this region
    rowBoundaryLinesBlock = []
    colBoundaryLinesBlock = []
    
    // Filter row boundary lines
    foreach row in sheet.rowBoundaryLines:
        if row < blockRegion.top - 2 or row > blockRegion.bottom - 1:
            continue
            
        // Check for inhomogeneity between rows
        for index = blockRegion.left - 3 to blockRegion.right - 1:
            boxUp = new Boundary(row + 1, row + 1, index, index + 3)
            boxDown = new Boundary(row + 2, row + 2, index, index + 3)
            
            // Check if one row has content and other doesn't (inhomogeneity)
            if (sheet.sumContentExist.SubmatrixSum(boxUp) > 0 and sheet.sumContentExist.SubmatrixSum(boxDown) == 0) or
               (sheet.sumContentExist.SubmatrixSum(boxDown) > 0 and sheet.sumContentExist.SubmatrixSum(boxUp) == 0):
                rowBoundaryLinesBlock.Add(row)
                break
    
    // Filter column boundary lines
    foreach col in sheet.colBoundaryLines:
        if col < blockRegion.left - 2 or col > blockRegion.right - 1:
            continue
            
        // Check for inhomogeneity between columns
        for index = blockRegion.top - 3 to blockRegion.bottom - 1:
            boxLeft = new Boundary(index, index + 3, col + 1, col + 1)
            boxRight = new Boundary(index, index + 3, col + 2, col + 2)
            
            // Check for inhomogeneity
            if (sheet.sumContentExist.SubmatrixSum(boxLeft) > 0 and sheet.sumContentExist.SubmatrixSum(boxRight) == 0) or
               (sheet.sumContentExist.SubmatrixSum(boxRight) > 0 and sheet.sumContentExist.SubmatrixSum(boxLeft) == 0):
                colBoundaryLinesBlock.Add(col)
                break
    
    // Remove duplicates
    rowBoundaryLinesBlock = RemoveDuplicates(rowBoundaryLinesBlock)
    colBoundaryLinesBlock = RemoveDuplicates(colBoundaryLinesBlock)
    
    // For very large sheets, limit the number of boundary lines to prevent combinatorial explosion
    if rowBoundaryLinesBlock.Count > 300:
        rowBoundaryLinesBlock = rowBoundaryLinesBlock.Take(300)
        isComplex = true
    
    if colBoundaryLinesBlock.Count > 150:
        colBoundaryLinesBlock = colBoundaryLinesBlock.Take(150)
        isComplex = true
    
    // Generate boxes from all combinations of boundary lines
    foreach left in colBoundaryLinesBlock:
        if left < blockRegion.left - 2:
            continue
            
        foreach right in colBoundaryLinesBlock:
            if left >= right or right > blockRegion.right - 1:
                continue
                
            // Dictionary to track invalid down values for optimization
            invalidDownValues = new Dictionary<int, bool>()
            
            foreach up in rowBoundaryLinesBlock:
                if up < blockRegion.top - 2:
                    continue
                    
                // Check if outside of up bound is empty or sparse
                boxUpOut = new Boundary(up + 1, up + 1, left + 2, right + 1)
                if sheet.sumContentExist.SubmatrixSum(boxUpOut) >= 6:
                    continue
                    
                // Check if outside of left and right are empty
                boxLeftOut = new Boundary(up + 2, up + 4, left + 1, left + 1)
                if sheet.sumContentExist.SubmatrixSum(boxLeftOut) >= 6:
                    continue
                    
                boxRightOut = new Boundary(up + 2, up + 4, right + 2, right + 2)
                if sheet.sumContentExist.SubmatrixSum(boxRightOut) >= 6:
                    continue
                
                // Check if inside up has content
                boxUpIn = new Boundary(up + 2, up + 2, left + 2, right + 1)
                if sheet.sumContentExist.SubmatrixSum(boxUpIn) == 0:
                    continue
                
                foreach down in rowBoundaryLinesBlock:
                    if up >= down or down > blockRegion.bottom - 1 or invalidDownValues.ContainsKey(down):
                        continue
                        
                    // Check if outside of down bound is empty or sparse
                    boxDownOut = new Boundary(down + 2, down + 2, left + 2, right + 1)
                    if sheet.sumContentExist.SubmatrixSum(boxDownOut) >= 6:
                        invalidDownValues[down] = true
                        continue
                    
                    // Check if inside down has content
                    boxDownIn = new Boundary(down + 1, down + 1, left + 2, right + 1)
                    if sheet.sumContentExist.SubmatrixSum(boxDownIn) == 0:
                        invalidDownValues[down] = true
                        continue
                    
                    // Create candidate box (adjusted for indexing)
                    box = new Boundary(up + 2, down + 1, left + 2, right + 1)
                    
                    // For complex large regions, ensure sufficient density
                    if isComplex and sheet.sumAll.SubmatrixSum(box) / AreaSize(box) < 2 * 0.3:
                        continue
                    
                    // Verify box meets general criteria
                    if GeneralFilter(box, sheet):
                        candidates.Add(box)
    
    return candidates

function GeneralFilter(box, sheet):
    // Basic size check
    if box.bottom - box.top < 1 or box.right - box.left < 1:
        return false
    
    // Border out edge sparse check
    if not VerifyBoxBorderValueInOutSimple(box, sheet):
        return false
    else if not VerifyBoxBorderValueOutSparse(box, sheet):
        return false
    // Check for null lines
    else if not VerifyBoxBorderValueNotNull(box, sheet):
        return false
    // Check for continuity (no empty rows/cols splitting the box)
    else if not VerifyBoxSplit(box, sheet):
        return false
    
    return true

function VerifyBoxBorderValueNotNull(box, sheet):
    // Check if any border has no content or formatting
    boxUp = new Boundary(box.top, box.top, box.left, box.right)
    boxDown = new Boundary(box.bottom, box.bottom, box.left, box.right)
    boxLeft = new Boundary(box.top, box.bottom, box.left, box.left)
    boxRight = new Boundary(box.top, box.bottom, box.right, box.right)
    
    // Each edge must have at least some content or formatting
    if sheet.sumContent.SubmatrixSum(boxUp) + sheet.sumColor.SubmatrixSum(boxUp) == 0 or
       sheet.sumContent.SubmatrixSum(boxDown) + sheet.sumColor.SubmatrixSum(boxDown) == 0 or
       sheet.sumContent.SubmatrixSum(boxLeft) + sheet.sumColor.SubmatrixSum(boxLeft) == 0 or
       sheet.sumContent.SubmatrixSum(boxRight) + sheet.sumColor.SubmatrixSum(boxRight) == 0:
        return false
    
    return true

function VerifyBoxBorderValueInOutSimple(box, sheet):
    // Check if outside borders are sparse and inside borders have content
    
    // Check outside top
    boxUp = new Boundary(box.top - 1, box.top - 1, box.left, box.right)
    sumUp = sheet.sumContentExist.SubmatrixSum(boxUp)
    if sumUp >= 6:
        return false
    
    // Check outside bottom
    boxDown = new Boundary(box.bottom + 1, box.bottom + 1, box.left, box.right)
    sumDown = sheet.sumContentExist.SubmatrixSum(boxDown)
    if sumDown >= 6:
        return false
    
    // Check outside left
    boxLeft = new Boundary(box.top, box.bottom, box.left - 1, box.left - 1)
    sumLeft = sheet.sumContentExist.SubmatrixSum(boxLeft)
    if sumLeft >= 6:
        return false
    
    // Check outside right
    boxRight = new Boundary(box.top, box.bottom, box.right + 1, box.right + 1)
    sumRight = sheet.sumContentExist.SubmatrixSum(boxRight)
    if sumRight >= 6:
        return false
    
    // Check inside borders have content
    boxUp = new Boundary(box.top, box.top, box.left, box.right)
    boxDown = new Boundary(box.bottom, box.bottom, box.left, box.right)
    boxLeft = new Boundary(box.top, box.bottom, box.left, box.left)
    boxRight = new Boundary(box.top, box.bottom, box.right, box.right)
    
    if sheet.sumContent.SubmatrixSum(boxUp) + sheet.sumColor.SubmatrixSum(boxUp) == 0 or
       sheet.sumContent.SubmatrixSum(boxDown) + sheet.sumColor.SubmatrixSum(boxDown) == 0 or
       sheet.sumContent.SubmatrixSum(boxLeft) + sheet.sumColor.SubmatrixSum(boxLeft) == 0 or
       sheet.sumContent.SubmatrixSum(boxRight) + sheet.sumColor.SubmatrixSum(boxRight) == 0:
        return false
    
    return true

function VerifyBoxBorderValueOutSparse(box, sheet):
    // Check if outside borders are sparse enough
    boxUp = new Boundary(box.top - 1, box.top - 1, box.left, box.right)
    boxDown = new Boundary(box.bottom + 1, box.bottom + 1, box.left, box.right)
    boxLeft = new Boundary(box.top, box.bottom, box.left - 1, box.left - 1)
    boxRight = new Boundary(box.top, box.bottom, box.right + 1, box.right + 1)
    
    sumUp = sheet.sumContentExist.SubmatrixSum(boxUp)
    sumDown = sheet.sumContentExist.SubmatrixSum(boxDown)
    sumLeft = sheet.sumContentExist.SubmatrixSum(boxLeft)
    sumRight = sheet.sumContentExist.SubmatrixSum(boxRight)
    
    // Check for sufficient sparsity based on box size
    if sumUp >= 6 or sumDown >= 6 or sumLeft >= 6 or sumRight >= 6:
        return false
    
    // More stringent checks for small boxes
    if box.bottom - box.top <= 2:
        if sumLeft >= 2 or sumRight >= 2:
            return false
    }
    
    if box.right - box.left <= 1:
        if sumUp >= 2 or sumDown >= 2:
            return false
    }
    
    if box.bottom - box.top <= 4:
        if sumLeft >= 4 or sumRight >= 4:
            return false
    }
    
    if box.right - box.left <= 4:
        if sumUp >= 4 or sumDown >= 4:
            return false
    }
    
    return true

function VerifyBoxSplit(box, sheet):
    // Find continuous empty rows/cols that might split the box into distinct regions
    up = box.top
    down = box.bottom
    left = box.left
    right = box.right
    
    // Check for splitting rows - accounting for header offset
    upOffset = box.bottom - box.top > 12 ? 2 : 0
    
    // Search for empty rows in middle (not near header)
    for i = up + 3 + upOffset to down - 4:
        // Check for 3 consecutive empty rows
        edgeBox3 = new Boundary(i, i + 2, left, right)
        edgeBox1 = new Boundary(i + 1, i + 1, left, right)
        
        if sheet.sumContent.SubmatrixSum(edgeBox1) < 3:
            // Case 1: Complete empty row with no format
            if sheet.sumContent.SubmatrixSum(edgeBox1) + sheet.sumColor.SubmatrixSum(edgeBox1) == 0 and 
               sheet.sumContentExist.SubmatrixSum(edgeBox3) == 0:
                
                // Find nearest non-empty rows above and below
                k = i + 3
                while k < down:
                    edgeBoxDown = new Boundary(k, k, left, right)
                    if sheet.sumContent.SubmatrixSum(edgeBoxDown) > 5: 
                        break
                    k++
                
                k = i - 1
                while k > up:
                    edgeBoxUp = new Boundary(k, k, left, right)
                    if sheet.sumContent.SubmatrixSum(edgeBoxUp) > 5:
                        break
                    k--
                
                // If both sides have significant content, they're likely separate tables
                if sheet.sumContentExist.SubmatrixSum(edgeBoxUp) > 5 and 
                   sheet.sumContentExist.SubmatrixSum(edgeBoxDown) > 5:
                    return false
            
            // Case 2: Sparse row with limited formatting - check homogeneity
            else if sheet.sumColor.SubmatrixSum(edgeBox1) + sheet.sumBorderCol.SubmatrixSum(edgeBox1) < 5 and 
                     not Overlaps(edgeBox1, sheet.conhensionRegions, exceptForward=true):
                
                // Check homogeneity of four corner regions
                BoxUpLeft = new Boundary(up, i + 1, left, left + 2)
                BoxUpRight = new Boundary(up, i + 1, right - 2, right)
                BoxDownLeft = new Boundary(i + 1, down, left, left + 2)
                BoxDownRight = new Boundary(i + 1, down, right - 2, right)
                
                // Calculate content density for each corner
                densityUpLeft = (sheet.sumContent.SubmatrixSum(BoxUpLeft) + 
                                sheet.sumColor.SubmatrixSum(BoxUpLeft) + 
                                sheet.sumBorderCol.SubmatrixSum(BoxUpLeft)) / AreaSize(BoxUpLeft)
                
                densityUpRight = (sheet.sumContent.SubmatrixSum(BoxUpRight) + 
                                 sheet.sumColor.SubmatrixSum(BoxUpRight) + 
                                 sheet.sumBorderCol.SubmatrixSum(BoxUpRight)) / AreaSize(BoxUpRight)
                
                densityDownLeft = (sheet.sumContent.SubmatrixSum(BoxDownLeft) + 
                                  sheet.sumColor.SubmatrixSum(BoxDownLeft) + 
                                  sheet.sumBorderCol.SubmatrixSum(BoxDownLeft)) / AreaSize(BoxDownLeft)
                
                densityDownRight = (sheet.sumContent.SubmatrixSum(BoxDownRight) + 
                                   sheet.sumColor.SubmatrixSum(BoxDownRight) + 
                                   sheet.sumBorderCol.SubmatrixSum(BoxDownRight)) / AreaSize(BoxDownRight)
                
                // If one part is empty and the other has content, they're likely separate
                if (densityUpLeft == 0 and densityDownLeft > 2 * 0.2) or
                   (densityUpRight == 0 and densityDownRight > 2 * 0.2) or
                   (densityDownLeft == 0 and densityUpLeft > 2 * 0.2) or
                   (densityDownRight == 0 and densityUpRight > 2 * 0.2):
                    return false
            }
        }
    }
    
    // Check for splitting columns
    leftOffset = box.right - box.left > 12 ? 2 : 0
    
    // Search for empty columns in middle
    for i = left + 3 + leftOffset to right - 4:
        edgeBox3 = new Boundary(up, down, i, i + 2)
        edgeBox1 = new Boundary(up, down, i + 1, i + 1)
        
        if sheet.sumContent.SubmatrixSum(edgeBox1) < 3:
            // Case 1: Complete empty column
            if sheet.sumContent.SubmatrixSum(edgeBox1) + sheet.sumColor.SubmatrixSum(edgeBox1) == 0 and 
               sheet.sumContentExist.SubmatrixSum(edgeBox3) == 0:
                
                // Find nearest non-empty columns left and right
                k = i + 3
                while k < right:
                    edgeBoxRight = new Boundary(up, down, k, k)
                    if sheet.sumContent.SubmatrixSum(edgeBoxRight) > 5:
                        break
                    k++
                
                k = i - 1
                while k > left:
                    edgeBoxLeft = new Boundary(up, down, k, k)
                    if sheet.sumContent.SubmatrixSum(edgeBoxLeft) > 5:
                        break
                    k--
                
                // If significant gap between content columns
                if edgeBoxRight.right - edgeBoxLeft.right >= 3:
                    return false
            
            // Case 2: Sparse column with limited formatting
            else if sheet.sumColor.SubmatrixSum(edgeBox1) + sheet.sumBorderRow.SubmatrixSum(edgeBox1) < 5 and 
                     not Overlaps(edgeBox1, sheet.conhensionRegions, exceptForward=true):
                
                // Check homogeneity of four corner regions
                BoxUpLeft = new Boundary(up, up + 2, left, i + 1)
                BoxUpRight = new Boundary(up, up + 2, i + 1, right)
                BoxDownLeft = new Boundary(down - 2, down, left, i + 1)
                BoxDownRight = new Boundary(down - 2, down, i + 1, right)
                
                // Calculate content density for each corner
                densityUpLeft = (sheet.sumContent.SubmatrixSum(BoxUpLeft) + 
                                sheet.sumColor.SubmatrixSum(BoxUpLeft) + 
                                sheet.sumBorderRow.SubmatrixSum(BoxUpLeft)) / AreaSize(BoxUpLeft)
                
                densityUpRight = (sheet.sumContent.SubmatrixSum(BoxUpRight) + 
                                 sheet.sumColor.SubmatrixSum(BoxUpRight) + 
                                 sheet.sumBorderRow.SubmatrixSum(BoxUpRight)) / AreaSize(BoxUpRight)
                
                densityDownLeft = (sheet.sumContent.SubmatrixSum(BoxDownLeft) + 
                                  sheet.sumColor.SubmatrixSum(BoxDownLeft) + 
                                  sheet.sumBorderRow.SubmatrixSum(BoxDownLeft)) / AreaSize(BoxDownLeft)
                
                densityDownRight = (sheet.sumContent.SubmatrixSum(BoxDownRight) + 
                                   sheet.sumColor.SubmatrixSum(BoxDownRight) + 
                                   sheet.sumBorderRow.SubmatrixSum(BoxDownRight)) / AreaSize(BoxDownRight)
                
                // If one part is empty and the other has content, they're likely separate
                if (densityUpLeft == 0 and densityUpRight / AreaSize(BoxUpRight) > 2 * 0.2) or
                   (densityUpRight == 0 and densityUpLeft / AreaSize(BoxUpLeft) > 2 * 0.2) or
                   (densityDownLeft == 0 and densityDownRight / AreaSize(BoxDownRight) > 2 * 0.2) or
                   (densityDownRight == 0 and densityDownLeft / AreaSize(BoxDownLeft) > 2 * 0.2):
                    return false
            }
        }
    }
    
    return true

function RefineBlockCandidates(boxes, sheet):
    // Multiple passes of refinement for block-level candidates
    
    // 1. Header identification and refinement
    boxes = UpHeaderTrim(boxes, sheet)
    
    // 2. Filter based on overlaps with special regions
    boxes = OverlapCohesionFilter(boxes, sheet)
    boxes = OverlapBorderCohesionFilter(boxes, sheet)
    
    // 3. Filter pivot overlaps
    boxes = OverlapPivotFilter(boxes, sheet)
    
    // 4. Filter small and sparse boxes
    boxes = LittleBoxesFilter(boxes, sheet)
    
    // 5. Header-based filtering
    boxes = OverlapUpHeaderFilter(boxes, sheet)
    
    // 6. Boundary trimming
    boxes = SurroundingBoundariesTrim(boxes, sheet)
    
    // 7. More header-based filtering
    boxes = OverlapUpHeaderFilter(boxes, sheet)
    
    // 8. Split filtering - remove boxes with empty lines that split them
    boxes = SplittedEmptyLinesFilter(boxes, sheet)
    
    return boxes

function RefineCandidatesGlobally(boxes, sheet):
    // Multi-pass global refinement pipeline
    
    // 1. Add border-defined regions as candidates
    boxes = BorderCohensionsAddition(boxes, sheet)
    
    // 2. Filter small/sparse boxes
    boxes = LittleBoxesFilter(boxes, sheet)
    
    // 3. Header processing
    boxes = RetrieveDistantUpHeader(boxes, sheet)
    boxes = VerticalRelationalMerge(boxes, sheet)
    
    // 4. Resolve overlapping conflicts
    boxes = SuppressionSoftFilter(boxes, sheet)
    boxes = HeaderPriorityFilter(boxes, sheet)
    
    // 5. Combine formula-related tables
    boxes = FormulaCorrelationFilter(boxes, sheet)
    
    // 6. Resolve containment relationships
    boxes = PairAlikeContainsFilter(boxes, sheet)
    boxes = PairContainsFilter(boxes, sheet)
    
    // 7. Handle complex structures
    boxes = CombineContainsHeaderFilter(boxes, sheet)
    boxes = CombineContainsFillAreaFilterSoft(boxes, sheet)
    boxes = CombineContainsFillLineFilterSoft(boxes, sheet)
    boxes = ContainsLittleFilter(boxes, sheet)
    
    // 8. Resolve more containment
    boxes = PairAlikeContainsFilter(boxes, sheet)
    boxes = PairContainsFilter(boxes, sheet)
    
    // 9. Handle nested combinations and conflicts
    boxes = NestingCombinationFilter(boxes, sheet)
    boxes = OverlapHeaderFilter(boxes, sheet)
    
    // 10. Remove duplicate boxes
    boxes = RemoveDuplicates(boxes)
    
    // 11. Border-based filtering
    boxes = ForcedBorderFilter(boxes, sheet)
    boxes = AdjoinHeaderFilter(boxes, sheet)
    
    // 12. Final filtering of small boxes
    boxes = LittleBoxesFilter(boxes, sheet)
    
    // 13. Handle more containment cases
    boxes = PairContainsFilterHard(boxes, sheet)
    boxes = CombineContainsFilterHard(boxes, sheet)
    
    // 14. Add region growth results if missed
    boxes = AddRegionGrowth(boxes, sheet)
    boxes = AddCompactRegionGrowth(boxes, sheet)
    
    // 15. Merged cell handling
    boxes = MergeFilter(boxes, sheet)
    
    // 16. Final boundary and header refinement
    boxes = RetrieveLeftHeader(boxes, sheet)
    boxes = LeftHeaderTrim(boxes, sheet)
    boxes = BottomTrim(boxes, sheet)
    boxes = RetrieveUpHeader(boxes, sheet, step=1)
    boxes = RetrieveUpHeader(boxes, sheet, step=2)
    boxes = UpTrimSimple(boxes, sheet)
    
    // 17. Final small box filter
    boxes = LittleBoxesFilter(boxes, sheet)
    
    return boxes

function EliminateOverlaps(boxes):
    // Remove or merge overlapping tables
    removedBoxes = new HashSet<Boundary>()
    
    for i = 0 to boxes.Count - 1:
        box1 = boxes[i]
        if removedBoxes.Contains(box1):
            continue
        
        removedBoxes1 = new HashSet<Boundary>()
        markRemoval = false
        
        for j = i + 1 to boxes.Count - 1:
            box2 = boxes[j]
            if removedBoxes.Contains(box2):
                continue
                
            if Overlaps(box1, box2):
                // Keep larger box, remove smaller one
                if AreaSize(box1) >= AreaSize(box2):
                    removedBoxes1.Add(box2)
                else:
                    markRemoval = true
                    break
            }
        }
        
        if markRemoval:
            removedBoxes.Add(box1)
        else:
            foreach box in removedBoxes1:
                removedBoxes.Add(box)
    }
    
    // Remove all boxes marked for removal
    return boxes.Where(box => !removedBoxes.Contains(box)).ToList()

function OverlapPivotFilter(boxes, sheet):
    // Handle pivot table overlaps
    removedBoxes = new HashSet<Boundary>()
    
    // Remove boxes that overlap pivot tables
    foreach box in boxes:
        if Overlaps(box, sheet.pivotBoxes):
            removedBoxes.Add(box)
    
    // Remove the marked boxes
    boxes = RemoveBoxes(boxes, removedBoxes)
    
    // Add all pivot tables as candidates
    foreach pivotBox in sheet.pivotBoxes:
        boxes.Add(pivotBox)
    
    return boxes

function LittleBoxesFilter(boxes, sheet):
    // Filter out small and sparse tables
    removedBoxes = new HashSet<Boundary>()
    
    foreach box in boxes:
        // Filter thin boxes
        if box.bottom - box.top < 1 or box.right - box.left < 1:
            removedBoxes.Add(box)
            continue
        
        // Filter very small boxes with sparse content
        else if (box.bottom - box.top < 2 or box.right - box.left < 2) and AreaSize(box) < 8:
            if sheet.ContentExistValueDensity(box) < 2 * 0.7:
                removedBoxes.Add(box)
                continue
        
        // Filter small boxes with sparse content
        else if (box.bottom - box.top < 2 or box.right - box.left < 2) and AreaSize(box) < 24:
            if sheet.ContentExistValueDensity(box) < 2 * 0.6:
                removedBoxes.Add(box)
                continue
        
        // Filter boxes with sparse content
        else if box.bottom - box.top < 2 or box.right - box.left < 2:
            if sheet.ContentExistValueDensity(box) < 2 * 0.55:
                removedBoxes.Add(box)
                continue
        
        // Filter wider boxes with sparse content
        else if box.bottom - box.top < 3 or box.right - box.left < 3:
            if sheet.ContentExistValueDensity(box) < 2 * 0.35:
                removedBoxes.Add(box)
                continue
        
        // Filter very small boxes by area
        if AreaSize(box) < 7:
            removedBoxes.Add(box)
            continue
        
        // Filter small boxes with sparse content
        else if (box.bottom - box.top < 5 and box.right - box.left < 3) or 
                (box.bottom - box.top < 3 and box.right - box.left < 5):
            if sheet.ContentExistValueDensity(box) < 2 * 0.55:
                removedBoxes.Add(box)
                continue
        
        // Filter small square boxes with sparse content
        else if box.bottom - box.top < 5 and box.right - box.left < 5:
            if sheet.ContentExistValueDensity(box) < 2 * 0.4:
                removedBoxes.Add(box)
                continue
        
        // Filter medium-sized boxes with sparse content
        else if box.bottom - box.top < 8 and box.right - box.left < 8:
            if sheet.ContentExistValueDensity(box) < 2 * 0.35:
                removedBoxes.Add(box)
                continue
        
        // Filter larger boxes with sparse content
        else if box.bottom - box.top < 14 and box.right - box.left < 14:
            if sheet.ContentExistValueDensity(box) < 2 * 0.25:
                removedBoxes.Add(box)
                continue
        
        // Filter boxes with continuous empty rows/columns
        if box.bottom - box.top == 2:
            boxWindow = new Boundary(box.top + 1, box.bottom - 1, box.left, box.right)
            if sheet.sumContentExist.SubmatrixSum(boxWindow) <= 5 and 
               sheet.ContentExistValueDensity(box) < 2 * 0.45:
                removedBoxes.Add(box)
                continue
        
        if box.right - box.left == 2:
            boxWindow = new Boundary(box.top, box.bottom, box.left + 1, box.right - 1)
            if sheet.sumContentExist.SubmatrixSum(boxWindow) <= 5 and 
               sheet.ContentExistValueDensity(box) < 2 * 0.45:
                removedBoxes.Add(box)
                continue
        
        // Check for empty rows in taller boxes
        if box.bottom - box.top > 3 and box.bottom - box.top <= 4:
            for index = box.top + 1 to box.bottom - 1:
                boxWindow = new Boundary(index, index + 1, box.left, box.right)
                if sheet.sumContentExist.SubmatrixSum(boxWindow) <= 3 and 
                   sheet.ContentExistValueDensity(box) < 2 * 0.4:
                    removedBoxes.Add(box)
                    break
        
        // Check for empty columns in wider boxes
        if box.right - box.left > 2 and box.right - box.left <= 4:
            for index = box.left + 2 to box.right - 1:
                boxWindow = new Boundary(box.top, box.bottom, index, index + 1)
                if sheet.sumContentExist.SubmatrixSum(boxWindow) <= 3 and 
                   sheet.ContentExistValueDensity(box) < 2 * 0.4:
                    removedBoxes.Add(box)
                    break
        
        // Check for empty columns in even wider boxes
        if box.right - box.left > 4 and box.right - box.left <= 7:
            for index = box.left + 2 to box.right - 2:
                boxWindow = new Boundary(box.top, box.bottom, index, index + 1)
                if sheet.sumContentExist.SubmatrixSum(boxWindow) <= 3 and 
                   sheet.ContentExistValueDensity(box) < 2 * 0.5:
                    removedBoxes.Add(box)
                    break
        
        // Check for empty rows in taller boxes
        if box.bottom - box.top > 4 and box.bottom - box.top <= 7:
            for index = box.top + 1 to box.bottom - 2:
                boxWindow = new Boundary(index, index + 1, box.left, box.right)
                if sheet.sumContentExist.SubmatrixSum(boxWindow) <= 3 and 
                   sheet.ContentExistValueDensity(box) < 2 * 0.5:
                    removedBoxes.Add(box)
                    break
        
    }
    
    return RemoveBoxes(boxes, removedBoxes)

function UpHeaderTrim(boxes, sheet):
    // Find and refine upper boundaries for headers
    
    // Find first non-sparse row or header row as upper bound
    boxes = FindUpBoundaryNotSparse(boxes, sheet)
    
    // Find true header if it exists
    boxes = FindUpBoundaryIsHeader(boxes, sheet)
    
    // Find true header with sparse upside row
    boxes = FindUpBoundaryIsClearHeader(boxes, sheet)
    
    // Find first header with compact contents of varying density
    boxes = FindUpBoundaryIsCompactHeader(boxes, sheet, 0.6, 0.8)
    boxes = FindUpBoundaryIsCompactHeader(boxes, sheet, 0.4, 0.7)
    boxes = FindUpBoundaryIsCompactHeader(boxes, sheet, 0.2, 0.5)
    
    return boxes

function FindUpBoundaryNotSparse(boxes, sheet):
    // Find the first non-sparse row as upper boundary
    removedBoxes = new HashSet<Boundary>()
    appendBoxes = new HashSet<Boundary>()
    
    foreach box in boxes:
        newBox = new Boundary(box.top, box.bottom, box.left, box.right)
        
        // Find the first non-sparse row for upper bound
        upperBoundRowCandidate = new Boundary(box.top, box.top, box.left, box.right)
        upperBoundRowCandidateDown = new Boundary(box.top + 1, box.top + 1, box.left, box.right)
        
        while upperBoundRowCandidate.top < newBox.bottom:
            // Check if current row is too sparse
            if sheet.ContentExistValueDensity(upperBoundRowCandidate) < 2 * 0.2 or
               (box.right - box.left + 1 >= 5 and sheet.TextDistinctCount(upperBoundRowCandidate) <= 1):
                upperBoundRowCandidate = new Boundary(upperBoundRowCandidate.top + 1, 
                                                     upperBoundRowCandidate.top + 1, 
                                                     newBox.left, 
                                                     newBox.right)
                upperBoundRowCandidateDown = new Boundary(upperBoundRowCandidateDown.top + 1,
                                                         upperBoundRowCandidateDown.top + 1,
                                                         newBox.left,
                                                         newBox.right)
            
            // Skip sparse rows that aren't headers in wide tables
            else if not IsHeaderUp(upperBoundRowCandidate) and
                    box.right - box.left > 7 and
                    2 * sheet.sumContentExist.SubmatrixSum(upperBoundRowCandidate) <= 
                    sheet.sumContentExist.SubmatrixSum(upperBoundRowCandidateDown) and
                    (sheet.sumContentExist.SubmatrixSum(upperBoundRowCandidate) < 7 or
                     sheet.ContentExistValueDensity(upperBoundRowCandidate) < 0.3 * 2):
                upperBoundRowCandidate = new Boundary(upperBoundRowCandidate.top + 1, 
                                                     upperBoundRowCandidate.top + 1, 
                                                     newBox.left, 
                                                     newBox.right)
                upperBoundRowCandidateDown = new Boundary(upperBoundRowCandidateDown.top + 1,
                                                         upperBoundRowCandidateDown.top + 1,
                                                         newBox.left,
                                                         newBox.right)
            else:
                break
        
        // Set new top bound
        newBox.top = upperBoundRowCandidate.top
        
        // If we found a better boundary, replace the box
        if not box.Equals(newBox) and newBox.top < newBox.bottom:
            removedBoxes.Add(box)
            appendBoxes.Add(newBox)
    
    // Apply changes
    boxes = RemoveBoxes(boxes, removedBoxes)
    boxes.AddRange(appendBoxes)
    
    return boxes

function IsHeaderUp(header, sheet):
    // Check if a row is a header row
    
    // Use cached result if available
    if headerUpMap.ContainsKey(header):
        return headerUpMap[header]
    
    // Do simple header check first
    if IsHeaderUpSimple(header, sheet):
        // Check if the row below might also be a header
        headerDownSide = new Boundary(header.top + 1, header.top + 1, header.left, header.right)
        
        // If rows are too similar and the next row is also a header, this might not be a header
        if sheet.ComputeSimilarRow(header, headerDownSide) < 0.15 and 
           IsHeaderUpSimple(headerDownSide, sheet):
            headerUpMap[header] = false
            return false
        
        // If both rows have exactly 2 cells with content and the same header rate,
        // they might be a split header
        else if sheet.sumContentExist.SubmatrixSum(header) == 2 and 
                sheet.sumContentExist.SubmatrixSum(headerDownSide) == 2 and
                HeaderRate(header) == HeaderRate(headerDownSide):
            return false
        
        // Otherwise, this is a header
        else:
            headerUpMap[header] = true
            return true
    
    // Not a header
    headerUpMap[header] = false
    return false

function IsHeaderUpSimple(header, sheet):
    // Basic check if a row looks like a header
    
    // Single cells can't be headers
    if header.right == header.left:
        return false
    
    // Too few content cells can't be headers
    if sheet.sumContentExist.SubmatrixSum(header) <= 4 and HeaderRate(header) <= 0.5:
        return false
    
    // Too few distinct values can't be headers
    if (AreaSize(header) > 4 and sheet.TextDistinctCount(header) <= 2) or
       (AreaSize(header) > 3 and sheet.TextDistinctCount(header) < 2):
        return false
    
    // Check right portion of header separately
    rightRegionOfHeader = new Boundary(header.top, 
                                       header.bottom, 
                                       Math.Min(header.left, header.right - 5) + 3, 
                                       header.right)
    
    // Headers typically have good content density and high header cell ratio
    if sheet.ContentExistValueDensity(header) > 2 * 0.3 and
       HeaderRate(header) > 0.4 and
       HeaderRate(rightRegionOfHeader) > 0.3:
        return true
    
    return false

function HeaderRate(region, step=6):
    // Calculate percentage of cells with header-like properties
    contentCells = 0
    allCells = 0
    headerLikeCells = 0
    
    for i = region.top to region.bottom:
        for j = region.left to region.right:
            allCells++
            
            // Define surrounding region for context
            if region.top == region.bottom:
                headerControledSurroundingRegion = new Boundary(i, i + step, j, j)
            else if region.left == region.right:
                headerControledSurroundingRegion = new Boundary(i, i, j, j + step)
            else:
                headerControledSurroundingRegion = new Boundary(i, i, j, j)
            
            if sheet.sumContentExist.SubmatrixSum(headerControledSurroundingRegion) != 0:
                contentCells++
                
                cell = sheet.featureMap[i - 1, j - 1]
                if cell.MarkText:
                    // Characteristics of header-like cells:
                    // 1. More alphabet characters than numbers
                    // 2. Significant alphabet content
                    // 3. Contains special characters
                    if (cell.AlphabetRatio >= cell.NumberRatio and cell.AlphabetRatio != 0) or
                       cell.AlphabetRatio * cell.TextLength > 2.5 or 
                       cell.SpCharRatio > 0:
                        headerLikeCells++
    
    if allCells == 0:
        return 0
        
    // Return ratio of header-like cells to valid content cells
    // Uses minimum threshold of 1/3 of all cells to prevent sparse headers
    return headerLikeCells / Max(contentCells, allCells / 3.0)

function FormulaCorrelationFilter(boxes, sheet):
    // Combine boxes that are related by formulas
    removedBoxes = new HashSet<Boundary>()
    appendBoxes = new HashSet<Boundary>()
    
    for i = 0 to boxes.Count - 1:
        boxFrom = boxes[i]
        
        for j = 0 to boxes.Count - 1:
            boxTo = boxes[j]
            
            if boxFrom.Equals(boxTo):
                continue
            
            // Skip boxes that are far away from each other
            if boxTo.bottom < boxFrom.top - 10 or 
               boxTo.right < boxFrom.left - 10 or 
               boxFrom.bottom < boxTo.top - 10 or 
               boxFrom.right < boxTo.left - 10:
                continue
            
            // Skip boxes that fully cross each other (accounting for headers)
            if (boxFrom.left <= boxTo.left - 2 or boxFrom.right >= boxTo.right + 2) and
               (boxFrom.top <= boxTo.top - 2 or boxFrom.bottom >= boxTo.bottom + 2):
                continue
            
            // Check vertical relation - boxFrom thinner than boxTo
            if boxFrom.left > boxTo.left - 2 and boxFrom.right < boxTo.right + 2:
                verticalCorrelation = IsFormulaCorrelation(boxFrom, boxTo, "vertical", sheet)
                
                if verticalCorrelation == 2 or verticalCorrelation == 1:
                    removedBoxes.Add(boxFrom)
                
                boxLarge = UnifyBox(boxFrom, boxTo)
                
                // Special case: boxFrom not contained in boxTo and gap is sparse
                if verticalCorrelation == 2 and
                   not (boxFrom.top >= boxTo.top and boxFrom.bottom <= boxTo.bottom) and
                   sheet.sumContentExist.SubmatrixSum(new Boundary(
                       boxTo.bottom + 3, boxFrom.top - 3, boxTo.left, boxTo.right)) <= 10:
                    // Remove boxTo and add the merged box
                    removedBoxes.Add(boxTo)
                    appendBoxes.Add(boxLarge)
                
                // Regular case: just add the merged box
                if verticalCorrelation == 2 and
                   not (not (boxFrom.top >= boxTo.top and boxFrom.bottom <= boxTo.bottom) and
                   sheet.sumContentExist.SubmatrixSum(new Boundary(
                       boxTo.bottom + 3, boxFrom.top - 3, boxTo.left, boxTo.right)) <= 10):
                    appendBoxes.Add(boxLarge)
            
            // Check horizontal relation
            horizontalCorrelation = IsFormulaCorrelation(boxFrom, boxTo, "horizontal", sheet)
            
            if boxFrom.top > boxTo.top - 2 and 
               boxFrom.bottom < boxTo.bottom + 2 and 
               horizontalCorrelation != 0:
                // Remove boxFrom as it's referenced by boxTo
                removedBoxes.Add(boxFrom)
                
                boxLarge = UnifyBox(boxFrom, boxTo)
                
                // Special case: boxFrom not contained in boxTo and gap is sparse
                if horizontalCorrelation == 2 and
                   not (boxFrom.left >= boxTo.left and boxFrom.right <= boxTo.right) and
                   sheet.sumContentExist.SubmatrixSum(new Boundary(
                       boxTo.top, boxTo.bottom, boxTo.right + 3, boxFrom.left - 3)) <= 10:
                    removedBoxes.Add(boxTo)
                    appendBoxes.Add(boxLarge)
                
                // Regular case: just add the merged box
                if horizontalCorrelation == 2 and
                   not (not (boxFrom.left >= boxTo.left and boxFrom.right <= boxTo.right) and
                   sheet.sumContentExist.SubmatrixSum(new Boundary(
                       boxTo.top, boxTo.bottom, boxTo.right + 3, boxFrom.left - 3)) <= 10):
                    appendBoxes.Add(boxLarge)
    
    // Apply changes
    boxes = RemoveBoxes(boxes, removedBoxes)
    boxes.AddRange(appendBoxes)
    
    // Remove duplicates
    return RemoveDuplicates(boxes)

function IsFormulaCorrelation(boxFrom, boxTo, direction, sheet):
    // Check if boxes are related through formulas
    
    // Examine all cells in boxFrom for formulas
    for row = boxFrom.top to boxFrom.bottom:
        for col = boxFrom.left to boxFrom.right:
            curCell = new Boundary(row, row, col, col)
            
            // Skip cells that are in the overlap area
            if direction == "vertical" and boxTo.top <= curCell.top and curCell.top <= boxTo.bottom:
                continue
            
            if direction == "horizontal" and boxTo.left <= curCell.left and curCell.left <= boxTo.right:
                continue
            
            // Check all formula references for this cell
            foreach referRange in sheet.formulaRanges[row, col]:
                if not Overlaps(referRange, boxTo):
                    continue
                
                // Determine formula relation type based on direction
                formulaRelation = direction == "vertical" 
                    ? IsFormulaRelatedUpDown(boxFrom, boxTo, curCell, referRange, sheet)
                    : IsFormulaRelatedLeftRight(boxFrom, boxTo, curCell, referRange, sheet)
                
                if formulaRelation != 0:
                    return formulaRelation
            }
        }
    }
    
    return 0

function IsFormulaRelatedUpDown(boxFrom, boxTo, cell, referRange, sheet):
    // Check if vertical formula relationship exists
    
    overlapRange = OverlapBox(referRange, boxTo)
    
    // Skip if reference is contained in the source box
    if ContainsBox(boxFrom, overlapRange, 1):
        return 0
    
    // Check if the boxes align vertically by column
    if Overlaps(boxFrom, new Boundary(1, sheet.Height, overlapRange.left, overlapRange.right)):
        box1Up1 = new Boundary(boxFrom.top - 1, boxFrom.top - 1, boxFrom.left, boxFrom.right)
        box1Up2 = new Boundary(boxFrom.top - 2, boxFrom.top - 2, boxFrom.left, boxFrom.right)
        
        // Check if boxFrom is below overlapRange and they share columns
        if (boxFrom.bottom > boxTo.bottom or boxFrom.top > boxTo.top) and 
            overlapRange.top < boxFrom.top and
            boxFrom.right > boxTo.left and boxTo.right > boxFrom.left and
            not IsHeaderUp(box1Up1, sheet) and not IsHeaderUp(box1Up2, sheet):
            
            // Return 1 if cell is contained in boxTo, 2 otherwise
            if ContainsBox(boxTo, cell):
                return 1
            else:
                return 2
    }
    
    return 0

function IsFormulaRelatedLeftRight(boxFrom, boxTo, cell, referRange, sheet):
    // Check if horizontal formula relationship exists
    
    overlapRange = OverlapBox(referRange, boxTo)
    
    // Skip if reference is contained in the source box
    if ContainsBox(boxFrom, overlapRange, 1):
        return 0
    
    // Check if the boxes align horizontally by row
    if Overlaps(boxFrom, new Boundary(overlapRange.top, overlapRange.bottom, 1, sheet.Width)):
        box1Left1 = new Boundary(boxFrom.top, boxFrom.bottom, boxFrom.left - 1, boxFrom.left - 1)
        box1Left2 = new Boundary(boxFrom.top, boxFrom.bottom, boxFrom.left - 2, boxFrom.left - 2)
        
        // Check if boxFrom is to the right of overlapRange and they share rows
        if (boxFrom.right > boxTo.right or boxFrom.left > boxTo.left) and 
            referRange.left < boxFrom.left and
            boxFrom.bottom > boxTo.top and boxTo.bottom > boxFrom.top and
            not IsHeaderLeft(box1Left1, sheet) and not IsHeaderLeft(box1Left2, sheet):
            
            // Return 1 if cell is contained in boxTo, 2 otherwise
            if ContainsBox(boxTo, cell):
                return 1
            else:
                return 2
    }
    
    return 0

function OverlapCohesionFilter(boxes, sheet):
    // Filter boxes that overlap cohesion regions
    removedBoxes = new HashSet<Boundary>()
    
    foreach box in boxes:
        if Overlaps(box, sheet.conhensionRegions, exceptForward=true, exceptBackward=true):
            removedBoxes.Add(box)
    }
    
    return RemoveBoxes(boxes, removedBoxes)

function NestingCombinationFilter(boxes, sheet):
    // Filter intermediate candidates in nesting combinations
    removedBoxes = new List<Boundary>()
    
    // Check vertical nesting
    foreach left in sheet.colBoundaryLines:
        foreach right in sheet.colBoundaryLines:
            if left >= right:
                continue
            
            // Find boxes with same left/right boundaries
            upDownBoxes = []
            foreach box in boxes:
                if box.left >= left - 1 and box.left <= left + 3 and 
                   box.right >= right - 1 and box.right <= right + 3:
                    upDownBoxes.Add(box)
            
            // Find intermediate boxes
            removedBoxes.AddRange(FindInterCandidates(upDownBoxes))
    
    // Check horizontal nesting
    foreach up in sheet.rowBoundaryLines:
        foreach down in sheet.rowBoundaryLines:
            if up >= down:
                continue
            
            // Find boxes with same top/bottom boundaries
            leftRightBoxes = []
            foreach box in boxes:
                if box.top >= up - 1 and box.bottom >= down - 1 and 
                   box.top <= up + 3 and box.bottom <= down + 3:
                    leftRightBoxes.Add(box)
            
            // Find intermediate boxes
            removedBoxes.AddRange(FindInterCandidates(leftRightBoxes))
    
    return RemoveBoxes(boxes, new HashSet<Boundary>(removedBoxes))

function FindInterCandidates(ranges):
    // Find boxes that are both containers and contained (intermediate)
    iterBoxes = new List<Boundary>()
    
    foreach box in ranges:
        markIn = false
        markOut = false
        
        foreach box2 in ranges:
            if not box.Equals(box2):
                if ContainsBox(box2, box, step=2):
                    markIn = true
                
                if ContainsBox(box, box2, step=2):
                    markOut = true
            }
        }
        
        // If box both contains and is contained by other boxes, it's an intermediate
        if markIn and markOut:
            iterBoxes.Add(box)
    }
    
    return iterBoxes

function SplittedEmptyLinesFilter(boxes, sheet):
    // Filter boxes that are split by empty rows/columns
    removedBoxes = new HashSet<Boundary>()
    
    foreach box in boxes:
        if removedBoxes.Contains(box):
            continue
        
        if not VerifyBoxSplit(box, sheet):
            removedBoxes.Add(box)
    }
    
    return RemoveBoxes(boxes, removedBoxes)

function RefineBoundaries(boxes, sheet):
    // Multiple passes for boundary refinement
    int cntBoxes = -1
    
    // Keep refining until no more changes
    while boxes.Count != cntBoxes:
        boxes = RemoveDuplicates(boxes)
        cntBoxes = boxes.Count
        
        // Find left boundary that's not sparse
        boxes = FindLeftBoundaryNotSparse(boxes, sheet)
        
        // Find first row with compact contents
        boxes = FindUpBoundaryNotSparse(boxes, sheet)
        
        // Trim sparse edges
        boxes = SparseBoundariesTrim(boxes, sheet)
        
        // Trim bottom boundary
        boxes = BottomBoundaryTrim(boxes, sheet)
        
        // Refine upper boundary for compactness
        boxes = UpBoundaryCompactTrim(boxes, sheet)
        
        // Filter boxes with null edges
        boxes = NoneBorderFilter(boxes, sheet)
    }
    
    return boxes
```

# Spreadsheet Compression Algorithm - Detailed Pseudo Code

The spreadsheet compression system employs two complementary approaches: data type aggregation and structural compression. I'll map out both algorithms in detail.

## 1. Data Type Aggregation Algorithm

This algorithm identifies regions of similar data types and replaces specific values with categorical representations.

```pseudocode
function AggregateTableData(inputFile, outputFile):
    for each data in inputFile:
        // Extract table data from prompt
        prompt = data["messages"][1]["content"]
        cellRows = ExtractCellData(prompt)
        nfsRows = ExtractNumberFormatData(prompt)
        
        // Optional formatting data extraction
        if CONFIG.FMT_TAG:
            formatData = ExtractFormatData(prompt)
        
        // Convert rows to matrices for processing
        cellMatrix = ParseRowsToMatrix(cellRows)
        nfsMatrix = ParseRowsToMatrix(nfsRows)
        
        // 1. Find regions with similar data types
        areas = AggregateToSimilarAreas(cellMatrix, nfsMatrix)
        
        // 2. Get coordinate metadata
        firstAddress = ParseExcelRow(cellRows[0])[0].split(",")[0]
        matches = ExtractCoordinates(firstAddress)
        rowOffset = int(matches.numeric_part) - 1
        colOffset = ColumnLetterToIndex(matches.uppercase_part) - 1
        
        // 3. Process each identified area to create formal area definitions 
        newAreas = []
        for each area in areas:
            // Convert to Excel coordinate system
            beginNumIndex = [area[0][0] + rowOffset, area[0][1] + colOffset]
            endNumIndex = [area[1][0] + rowOffset, area[1][1] + colOffset]
            beginAddress = IndexToExcelAddress(beginNumIndex)
            endAddress = IndexToExcelAddress(endNumIndex)
            
            // Store area with its data type
            newAreas.push((beginAddress + ":" + endAddress, area[2]))
            
            // If area is multi-cell and has definite type (not text)
            if beginAddress != endAddress and area[2] != 9:
                data["areas"].push((beginAddress + ":" + endAddress, area[2]))
        
        // 4. Transform the cell contents based on detected areas
        for each area in newAreas:
            beginAddress = area[0].split(":")[0]
            endAddress = area[0].split(":")[1]
            valueType = area[1]
            
            // Replace cell content with type information
            cellMatrix = ReplaceWithTypeInfo(cellMatrix, beginAddress, endAddress, valueType)
        
        // 5. Generate new prompt with type-aggregated data
        newPrompt = FormatMatrixToPrompt(cellMatrix, formatData)
        data["messages"][1]["content"] = newPrompt
        
        // Write to output file
        outputFile.write(data)
```

### AggregateToSimilarAreas Function (Core Aggregation Logic)

```pseudocode
function AggregateToSimilarAreas(cellMatrix, nfsMatrix):
    rows = len(cellMatrix)
    cols = len(cellMatrix[0])
    visited = Initialize2DArray(rows, cols, false)
    
    areas = []
    
    // DFS-based region growing algorithm
    for r in range(rows):
        for c in range(cols):
            if not visited[r][c] and cellMatrix[r][c] != "":
                // Determine value type of seed cell
                valueType = GetCellDataType(nfsMatrix[r][c], cellMatrix[r][c])
                
                // Grow region with DFS
                bounds = DFSGrow(r, c, valueType, cellMatrix, nfsMatrix, visited)
                
                // Add valid region to areas list
                if bounds[0] <= bounds[2] and bounds[1] <= bounds[3]:
                    areas.push(((bounds[0], bounds[1]), (bounds[2], bounds[3]), valueType))
    
    return areas

function DFSGrow(r, c, valueType, cellMatrix, nfsMatrix, visited):
    // Check if cell is valid and has same type
    if not IsValidCell(r, c, valueType, cellMatrix, nfsMatrix, visited):
        return [r, c, r-1, c-1]  // Invalid bounds
    
    // Mark as visited
    visited[r][c] = true
    
    // Initialize region bounds
    bounds = [r, c, r, c]
    
    // Explore in four directions
    for (dr, dc) in [(-1,0), (1,0), (0,-1), (0,1)]:
        newR = r + dr
        newC = c + dc
        
        if IsValidCell(newR, newC, valueType, cellMatrix, nfsMatrix, visited):
            // Recursively grow region
            newBounds = DFSGrow(newR, newC, valueType, cellMatrix, nfsMatrix, visited)
            
            // Update overall bounds
            bounds[0] = min(bounds[0], newBounds[0])  // top
            bounds[1] = min(bounds[1], newBounds[1])  // left
            bounds[2] = max(bounds[2], newBounds[2])  // bottom
            bounds[3] = max(bounds[3], newBounds[3])  // right
    
    return bounds

function IsValidCell(r, c, valueType, cellMatrix, nfsMatrix, visited):
    if not (0 <= r < rows and 0 <= c < cols and not visited[r][c]):
        return false
    
    // Check if current cell has same type as target type
    currentType = GetCellDataType(nfsMatrix[r][c], cellMatrix[r][c])
    return currentType == valueType
```

### GetCellDataType Function

```pseudocode
function GetCellDataType(nfsCell, cell):
    if cell == "":
        return -1
    
    if CONFIG.NFS_TAG:
        // Use number format string if available
        if nfsCell == "None":
            // Check if it's a year
            if MatchesPattern(cell, r'^19\d{2}$|^20\d{2}$'):
                return 0  // YearData
            else:
                // Identify data type from content
                return IdentifyNumberType(cell)
        else:
            return nfsCell
    else:
        // Use format dictionary if available
        if nfsCell in CONFIG.DIC:
            return CONFIG.DIC[nfsCell]
        
        // Check if it's a year
        if MatchesPattern(cell, r'^19\d{2}$|^20\d{2}$'):
            return 0  // YearData
        
        // Identify data type from content
        return IdentifyNumberType(cell)

function IdentifyNumberType(s):
    if MatchesPattern(s, r'^-?\d+$'):
        return 1  // IntNum
    elif MatchesPattern(s, r'^-?\d+\.\d+$'):
        return 2  // FloatNum
    elif IsPercentage(s):
        return 3  // PercentageNum
    elif IsScientificNotation(s):
        return 4  // ScientificNum
    elif IsDate(s):
        return 5  // DateData
    elif IsTime(s):
        return 6  // TimeData
    elif IsCurrency(s):
        return 7  // CurrencyData
    elif IsEmail(s):
        return 8  // EmailData
    else:
        return 9  // Text or unknown
```

## 2. Structural Compression Algorithm

This algorithm identifies and removes structurally redundant rows and columns while preserving table semantics.

```pseudocode
function CompressStructure(inputFilePath, outputFilePath, mappingFilePath):
    count = 0
    
    with open(inputFilePath, outputFilePath, mappingFilePath) as (inputFile, outputFile, mappingFile):
        for each line in inputFile:
            data = ParseJSON(line)
            prompt = data["messages"][1]["content"]
            fileName = data["file_name"]
            labels = data["messages"][2]["content"]
            
            // 1. Extract boundary information from labels
            rowBoundary = []
            colBoundary = []
            
            for each label in labels:
                for each address in label:
                    // Extract coordinates from addresses like "A1:B2"
                    addresses = address.split(":")
                    
                    // Parse row/column from each address
                    for each addr in addresses:
                        matches = ExtractCoordinates(addr)
                        rowBoundary.push(int(matches.numeric_part))
                        colBoundary.push(ColumnLetterToIndex(matches.uppercase_part))
            
            // 2. Get additional boundary information from external source
            additionalBoundaries = FindBoundaries(fileName)
            for each boundary in additionalBoundaries:
                parts = boundary.split(",")
                rowBoundary.push(int(parts[0]))
                rowBoundary.push(int(parts[1]))
                colBoundary.push(int(parts[2]))
                colBoundary.push(int(parts[3]))
            
            // Sort and remove duplicates
            rowBoundary = SortAndDeduplicate(rowBoundary)
            colBoundary = SortAndDeduplicate(colBoundary)
            
            // 3. Compress table structure by removing unnecessary rows and columns
            txtRows = ExtractCellData(prompt)
            nfsRows = ExtractNumberFormatData(prompt)
            
            // Remove rows that are not near boundaries
            txtCompressed = CompressRows(txtRows, rowBoundary)
            nfsCompressed = CompressRows(nfsRows, rowBoundary)
            
            // Remove columns that are not near boundaries
            txtCompressed = CompressColumns(txtCompressed, colBoundary)
            nfsCompressed = CompressColumns(nfsCompressed, colBoundary)
            
            // 4. Remove completely empty rows/columns
            txtCompressed, nfsCompressed = DeleteEmptySpaces(txtCompressed, nfsCompressed)
            
            // 5. Rearrange coordinates to normalize addressing
            newTxtRows, coordinateMap, reverseCoordinateMap = RearrangeCoordinates(txtCompressed)
            newNfsRows, _, _ = RearrangeCoordinates(nfsCompressed)
            
            // 6. Update label addresses with new coordinates
            newLabels = UpdateLabelCoordinates(labels, reverseCoordinateMap)
            
            // 7. Create new prompt with compressed data
            promptCompressed = FormatRowsToPrompt(newTxtRows, newNfsRows)
            
            // 8. Update data and write to output
            data["messages"][1]["content"] = promptCompressed
            data["messages"][2]["content"] = newLabels
            data["now_length"] = CalculateTokenLength(data)
            
            outputFile.write(data)
            
            // 9. Store coordinate mapping for future reference
            mapping = {
                "file_name": fileName,
                "reflection": coordinateMap,
                "reflection_r": reverseCoordinateMap
            }
            mappingFile.write(mapping)
```

### Row and Column Compression Functions

```pseudocode
function CompressRows(rows, rowBoundaries):
    // Extract initial coordinate information
    firstCell = rows[0].split("|")[1].split(",")[0]
    matches = ExtractCoordinates(firstCell)
    rowOffset = int(matches.numeric_part) - 1
    
    // Initialize tracking array for rows to keep
    keepRows = [0] * (len(rows) + 1)
    
    // Mark rows to keep: those near boundaries and buffer zones
    for rowIndex in rowBoundaries:
        // Convert to zero-based index relative to current table
        tableRowIndex = rowIndex - rowOffset
        
        // Skip if out of bounds
        if tableRowIndex > len(rows):
            tableRowIndex = len(rows)
        
        // Mark this row
        keepRows[tableRowIndex] = 1
        
        // Mark buffer zone (DELTA rows in each direction)
        if (tableRowIndex - CONFIG.DELTA) >= 1 and (tableRowIndex + CONFIG.DELTA) <= len(rows):
            for i in range(tableRowIndex - CONFIG.DELTA, tableRowIndex + CONFIG.DELTA + 1):
                keepRows[i] = 1
        elif (tableRowIndex - CONFIG.DELTA) < 1 and (tableRowIndex + CONFIG.DELTA) <= len(rows):
            for i in range(1, tableRowIndex + CONFIG.DELTA + 1):
                keepRows[i] = 1
        elif (tableRowIndex - CONFIG.DELTA) >= 1 and (tableRowIndex + CONFIG.DELTA) > len(rows):
            for i in range(tableRowIndex - CONFIG.DELTA, len(rows) + 1):
                keepRows[i] = 1
        elif (tableRowIndex - CONFIG.DELTA) < 1 and (tableRowIndex + CONFIG.DELTA) > len(rows):
            for i in range(1, len(rows) + 1):
                keepRows[i] = 1
    
    // Keep only marked rows
    result = []
    for j in range(len(rows)):
        if keepRows[j + 1] == 1:
            result.push(rows[j])
    
    return result

function CompressColumns(rows, colBoundaries):
    // Similar logic to CompressRows but for columns
    // Extract initial coordinate information
    firstCell = rows[0].split("|")[1].split(",")[0]
    matches = ExtractCoordinates(firstCell)
    colOffset = ColumnLetterToIndex(matches.uppercase_part) - 1
    
    // Get width (number of columns) from first row
    width = len(ParseExcelRow(rows[0]))
    
    // Initialize tracking array for columns to keep
    keepCols = [0] * (width + 1)
    
    // Mark columns to keep: those near boundaries and buffer zones
    for colIndex in colBoundaries:
        // Convert to zero-based index relative to current table
        tableColIndex = colIndex - colOffset
        
        // Skip if out of bounds
        if tableColIndex > width:
            tableColIndex = width
        
        // Mark this column
        keepCols[tableColIndex] = 1
        
        // Mark buffer zone (similar to rows but with column indices)
        // ... (similar buffer zone marking logic as CompressRows)
    
    // Create new rows with only kept columns
    result = []
    for row in rows:
        parsedRow = ParseExcelRow(row)
        newRow = ""
        
        for j in range(len(parsedRow)):
            if keepCols[j + 1] == 1:
                newRow += "|" + parsedRow[j]
        
        result.push(newRow)
    
    return result
```

### Empty Space Detection and Removal

```pseudocode
function DeleteEmptySpaces(txtRows, nfsRows):
    // 1. Detect empty rows
    rowTag = GetEmptyRowTags(txtRows)
    rowIntervals = FindConsecutiveEmptyIntervals(rowTag)
    
    // Mark row intervals for keeping (keep interval endpoints)
    for interval in rowIntervals:
        // Skip if interval is at the edge
        if interval[0] == 0 or interval[1] == len(rowTag) - 1:
            continue
            
        // Mark interval boundaries for keeping
        rowTag[interval[0]] = 2
        rowTag[interval[1]] = 2
    
    // 2. Detect empty columns
    colTag = GetEmptyColumnTags(txtRows)
    colIntervals = FindConsecutiveEmptyIntervals(colTag)
    
    // Mark column intervals for keeping (keep interval endpoints)
    for interval in colIntervals:
        // Skip if interval is at the edge
        if interval[0] == 0 or interval[1] == len(colTag) - 1:
            continue
            
        // Mark interval boundaries for keeping
        colTag[interval[0]] = 2
        colTag[interval[1]] = 2
    
    // 3. Remove empty rows (but keep marked boundaries)
    newCellRows = []
    for i in range(len(txtRows)):
        if rowTag[i] != 1:  // Not completely empty or is a boundary
            newCellRows.push(txtRows[i])
    
    newNfsRows = []
    for j in range(len(nfsRows)):
        if rowTag[j] != 1:  // Not completely empty or is a boundary
            newNfsRows.push(nfsRows[j])
    
    return newCellRows, newNfsRows

function GetEmptyRowTags(rows):
    // Check each row for emptiness
    tags = [0] * len(rows)
    for i in range(len(rows)):
        if IsRowEmpty(rows[i]):
            tags[i] = 1
    return tags

function IsRowEmpty(row):
    // Check if all cells in row are empty
    cells = ParseExcelRow(row)
    for cell in cells:
        cellValue = cell.split(",", 1)[1]
        if cellValue != "":
            return false
    return true

function GetEmptyColumnTags(rows):
    // Convert rows to column-centric view and check emptiness
    parsedRows = [ParseExcelRow(row) for row in rows]
    
    // Convert to 2D array of values
    cellValues = []
    for row in parsedRows:
        rowValues = []
        for cell in row:
            rowValues.push(cell.split(",", 1)[1])
        cellValues.push(rowValues)
    
    // Check each column
    colCount = len(cellValues[0])
    tags = [1] * colCount  // Assume all empty initially
    
    for j in range(colCount):
        for i in range(len(cellValues)):
            if cellValues[i][j] != " ":
                tags[j] = 0  // Mark as non-empty
                break
                
    return tags
```

### Coordinate Transformation

```pseudocode
function RearrangeCoordinates(rows):
    newRows = []
    forwardMap = {}  // Original -> New
    reverseMap = {}  // New -> Original
    
    // Create new coordinates in sequential order
    for i in range(len(rows)):
        rowCells = ParseExcelRow(rows[i])
        newRowCells = []
        
        for j in range(len(rowCells)):
            cell = rowCells[j].split(',', 1)
            originalAddress = cell[0]
            cellValue = cell[1]
            
            // Create new sequential address
            newAddress = ColumnIndexToLetter(j + 1) + str(i + 1)
            
            // Store mapping
            forwardMap[originalAddress] = newAddress
            reverseMap[newAddress] = originalAddress
            
            newCell = newAddress + "," + cellValue
            newRowCells.push(newCell)
        
        // Create new row string
        rowStr = ""
        for cell in newRowCells:
            rowStr += "|" + cell
            
        newRows.push(rowStr)
    
    return newRows, forwardMap, reverseMap

function UpdateLabelCoordinates(labels, reverseMap):
    for label in labels:
        for i in range(len(label)):
            addresses = label[i].split(":")
            // Map both parts of range
            newAddresses = reverseMap[addresses[0]] + ":" + reverseMap[addresses[1]]
            label[i] = newAddresses
            
    return labels
```

## 3. Value Address Transformation

After compression, the algorithm transforms value addresses to make them more efficient for model interpretation:

```pseudocode
function ValueAddressTransform(inputFile, outputFile):
    with open(inputFile, outputFile) as (input, output):
        for each line in input:
            data = ParseJSON(line)
            prompt = data["messages"][1]["content"]
            areas = data["areas1"]
            
            // Extract row/column dimensions
            rows = ExtractTableRowsFromPrompt(prompt)
            input = [ParseExcelRow(row) for row in rows]
            firstAddress = input[0][0].split(",", 1)[0]
            matches = ExtractCoordinates(firstAddress)
            
            rowOffset = int(matches.numeric_part) - 1
            colOffset = ColumnLetterToIndex(matches.uppercase_part) - 1
            rowCount = len(input)
            colCount = len(input[0])
            
            // Convert areas to address-content tuples
            newInput = ""
            for area in areas:
                dataType = area[1]
                addresses = area[0].split(":")
                
                // Convert data type from numeric code to string description
                typeLabel = ConvertTypeCodeToLabel(dataType)
                
                // Create tuple: (TypeLabel|Address)
                newInput += "(" + typeLabel + "|" + addresses[0] + ")"
                if addresses[0] != addresses[1]:
                    newInput += ":" + addresses[1]
                newInput += ","
            
            // Remove trailing comma
            newInput = newInput[:-1]
            
            // Create table description
            description = "The spreadsheet has " + str(rowCount) + " rows and " + 
                         str(colCount) + " columns. Column names:" + 
                         GetColumnNames(colCount, colOffset) + "; Row numbers:" + 
                         GetRowNumbers(rowCount, rowOffset)
            
            // Update prompt with compressed value-address representation
            newPrompt = INSTRUCTION + "\nDescription: " + description + "\nInput: " + newInput
            
            data["messages"][1]["content"] = newPrompt
            
            // Clean up and write output
            del data["areas"]
            del data["areas1"]
            data["now_length"] = CalculateTokenLength(data)
            
            output.write(data)
```

This comprehensive algorithm systematically transforms spreadsheets into more compact representations while preserving the essential structure and semantics needed for downstream tasks like table detection and question answering.