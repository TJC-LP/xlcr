# XLCR Development Guide

## Build Commands
- `sbt compile` - Compile the project with default Scala version (2.12)
- `sbt compileScala2` - Compile all modules with Scala 2.12
- `sbt compileScala3` - Compile Scala 3 compatible modules with Scala 3.3.4
- `sbt assembly` - Create executable JAR files
- `sbt run` - Run the application
- `sbt "server/run"` - Run the server component
- `sbt "coreSpreadsheetLLM/run -i input.xlsx -o output.json"` - Run the SpreadsheetLLM module

Note: See SCALA.md for more details on Scala version compatibility

## Test Commands
- `sbt test` - Run all tests with default Scala version (2.12)
- `sbt testScala2` - Run all tests with Scala 2.12
- `sbt testScala3` - Run tests for Scala 3 compatible modules
- `sbt "testOnly com.tjclp.xlcr.ConfigSpec"` - Run a single test class
- `sbt "testOnly com.tjclp.xlcr.ConfigSpec -- -z 'parse valid command line arguments'"` - Run a specific test case
- `sbt "coreSpreadsheetLLM/test"` - Run all SpreadsheetLLM module tests

## Code Style
- Scala 3 with functional programming principles
- Immutable data structures preferred
- Package organization follows com.tjclp.xlcr convention
- CamelCase for methods/variables, PascalCase for classes/objects
- Prefer Option/Either for error handling over exceptions
- Bridges follow standard patterns (SimpleBridge, SymmetricBridge, etc.)
- Make illegal states unrepresentable through type system
- Models should be immutable case classes with well-defined interfaces
- Parser/Renderer pairs should be symmetric

## Module Structure
The project is organized into these main modules:
- `core` - Core functionality and abstract interfaces
- `core-aspose` - Integration with Aspose for PDF conversion and document transformations
- `core-spreadsheetllm` - Excel compression for LLM processing
- `server` - Kotlin-based server for interactive editing
- `data` - Directory containing sample Excel files for testing

## HTML and PowerPoint Conversion
The core-aspose module includes bidirectional conversion between HTML and PowerPoint formats, as well as PDF to PowerPoint conversion:

### Supported Conversions
- **HTML → PPTX**: Convert HTML documents to PowerPoint Open XML format
- **HTML → PPT**: Convert HTML documents to PowerPoint 97-2003 format
- **PPTX → HTML**: Export PowerPoint presentations to HTML
- **PPT → HTML**: Export legacy PowerPoint files to HTML
- **PDF → PPTX**: Convert PDF documents to PowerPoint Open XML format (each page becomes a slide)
- **PDF → PPT**: Convert PDF documents to PowerPoint 97-2003 format (each page becomes a slide)

### CLI Usage Examples
```bash
# Convert HTML to PowerPoint (PPTX)
sbt "run -i presentation.html -o output.pptx"

# Convert HTML to legacy PowerPoint (PPT)
sbt "run -i presentation.html -o output.ppt"

# Convert PowerPoint to HTML
sbt "run -i presentation.pptx -o output.html"

# Convert PowerPoint to HTML with master slide removal (cleaner output)
sbt "run -i presentation.pptx -o output.html --strip-masters"

# Convert legacy PowerPoint to HTML
sbt "run -i presentation.ppt -o output.html"

# Template swapping workflow: strip template, convert, apply new template
sbt "run -i old-template.pptx -o clean.html --strip-masters"
sbt "run -i clean.html -o new-presentation.pptx"
# Then apply new template in PowerPoint

# Convert PDF to PowerPoint (PPTX) - each page becomes a slide
sbt "run -i document.pdf -o presentation.pptx"

# Convert PDF to legacy PowerPoint (PPT)
sbt "run -i document.pdf -o presentation.ppt"
```

### Technical Details
- Uses Aspose.Slides for Java for conversion
- Default HTML import/export options (no custom configuration)
- Handles HTML structure with best-effort slide creation
- Preserves formatting and layout where possible
- Automatically removes unused master slides and layout slides during HTML → PowerPoint and PDF → PowerPoint conversions
- PDF conversion features:
  - Each page in the PDF becomes a slide in the PowerPoint presentation
  - Automatically handles encrypted and restricted PDFs (removes copy/edit restrictions)
  - Uses Aspose.PDF to create unlocked copies when needed
  - Supports password-protected PDFs (if password is available)
- Optional `--strip-masters` flag creates clean copies during PowerPoint → HTML conversions
  - Creates a new blank presentation and copies slide content only
  - Strips all masters, layouts, footers, logos, and template elements
  - Resulting presentation has only blank layouts with default masters
  - Enables cleaner HTML output without any template/layout/footer overhead
  - Facilitates template swapping workflows - strip old templates, convert to HTML, then apply new templates
  - Significantly reduces file sizes by removing all template and branding data
  - Perfect for re-theming presentations or removing corporate branding
- Supports round-trip conversions (HTML → PPTX → HTML)

## SpreadsheetLLM Module
This module compresses Excel spreadsheets into an LLM-friendly JSON format:

### Command Line Options
- `-i, --input <file>` - Input Excel file path (required)
- `-o, --output <file>` - Output JSON file path (required)
- `--anchor-threshold <n>` - Neighbor rows/cols to keep (default: 1)
- `--no-anchor` - Disable anchor-based pruning
- `--no-format` - Disable format-based aggregation
- `--no-preserve-coordinates` - Disable preservation of original Excel coordinates
- `--no-table-detection` - Disable multi-table detection in sheets
- `--min-gap-size <n>` - Minimum gap size for table detection (default: 3)
- `--semantic-compression` - Enable semantic compression for text-heavy cells
- `--no-enhanced-formulas` - Disable enhanced formula relationship detection
- `--threads <n>` - Number of threads for parallel processing
- `--verbose` - Enable verbose logging
- `--debug-data-detection` - Enable detailed debugging for date and number detection issues

### Coordinate Preservation Feature
The SpreadsheetLLM module includes a robust coordinate preservation system:
- Preserves original cell coordinates throughout the compression pipeline
- Maintains accurate Excel references even after significant coordinate shifts
- Ensures that cell locations in the output JSON match their original positions
- Can be disabled with `--no-preserve-coordinates` when original positions aren't important
- Essential for accurate formula references and structural understanding
- Provides consistent cell addressing regardless of compression operations

### Advanced Compression Features
The SpreadsheetLLM module includes several advanced compression techniques:

#### Table Detection
- Automatically identifies multiple tables within a single sheet
- Uses sophisticated gap analysis to detect table boundaries
- Enhanced column detection with smaller gap size for side-by-side tables
- Intelligently detects sparse tables, row-oriented and column-oriented layouts
- Tables are included in the output JSON with range and header information
- Can be disabled with `--no-table-detection` if needed
- Minimum gap size can be adjusted with `--min-gap-size` (default: 3)
- Column gap detection uses smaller value (min-gap-size minus 1) for better separation

## SheetCompressor Alignment Plan
This section outlines our plan to more closely align the AnchorExtractor implementation with the original SheetCompressor specification from structural-anchor-orig.md. Each feature will be implemented in phases to maintain stability while improving functionality.

### Phase 1: Core Algorithm Alignment
1. **Cohesion Region Implementation**
   - Add `CohesionRegion` class to detect regions that should stay together
   - Implement `detectCohesionRegions` method based on original spec
   - Add `OverlapCohensionFilter` and `OverlapBorderCohensionFilter` logic from original spec
   - Integrate with existing table detection pipeline
   - Target completion: 2 weeks

2. **Enhanced Formula Correlation**
   - Expand formula relationship detection with `FormulaCorrelationFilter`
   - Implement logic to detect related formulas across table boundaries
   - Add formula reference graph to improve table detection
   - Track formula dependencies in both directions
   - Target completion: 3 weeks

3. **Advanced Header Handler**
   - Enhance current header detection with multi-level recognition
   - Implement more sophisticated header heuristics from original spec
   - Add support for specialized header trimming
   - Add advanced pattern detection for header keywords
   - Target completion: 2 weeks

### Phase 2: Specialized Filter Implementation
1. **Pivot Table Handler**
   - Add specific detection and handling for pivot tables
   - Implement `OverlapPivotFilter` from original spec
   - Add pivot table preservation logic
   - Target completion: 2 weeks

2. **Border Analysis Enhancement**
   - Improve border detection with more sophisticated analysis
   - Add border cohesion logic from original spec
   - Implement specialized border-based filtering
   - Target completion: 1 week

3. **Advanced Filter Suite**
   - Implement `CombineContainsFillAreaFilterSoft` from original spec
   - Add `NestingCombinationFilter` for handling nested tables
   - Implement `SplittedEmptyLinesFilter` for better gap detection
   - Target completion: 2 weeks

### Phase 3: Advanced Features
1. **Machine Learning Integration** (optional)
   - Research feasibility of ML/heuristic hybrid approach
   - Prototype ML-based table detection similar to `TableDetectionMLHeuHybrid.cs`
   - Evaluate performance against pure heuristic approach
   - Target completion: 4 weeks (research phase)

2. **Comprehensive Test Suite**
   - Develop test cases using the examples from original spec
   - Create benchmark suite to compare implementations
   - Add visual debugging tools for table detection
   - Target completion: 2 weeks

3. **Performance Optimization**
   - Profile and optimize critical path algorithms
   - Improve parallel processing capabilities
   - Optimize memory usage for large spreadsheets
   - Target completion: 2 weeks

### Implementation Priorities
1. Core Cohesion and Formula features (highest)
2. Header handling improvements (high)
3. Specialized filters (medium)
4. ML integration (low, optional)

### Testing Methodology
Each implementation phase will include:
- Unit tests comparing outputs against original spec examples
- Integration tests with full compression pipeline
- Performance benchmarks against current implementation
- Visual validation tools to verify correct table detection

### Implementation Steps for Cohesion Region (Phase 1.1)

```scala
/**
 * Implements cohesion region detection and filtering based on original SheetCompressor spec.
 * Cohesion regions represent areas that should be kept together during table detection.
 */
def detectCohesionRegions(grid: SheetGrid): List[TableRegion] = {
  logger.info("Detecting cohesion regions")
  
  // 1. Detect potential cohesion regions based on formatting cues
  val formattingBasedRegions = detectFormattingCohesionRegions(grid)
  
  // 2. Detect regions with strong content relationships
  val contentBasedRegions = detectContentCohesionRegions(grid)
  
  // 3. Find merged cell regions that indicate cohesion
  val mergedCellRegions = detectMergedCellRegions(grid)
  
  // 4. Combine and refine all cohesion regions
  val allCohesionRegions = (formattingBasedRegions ++ contentBasedRegions ++ mergedCellRegions)
    .distinct
    .filterNot(region => region.width < 2 || region.height < 2)
  
  logger.info(s"Detected ${allCohesionRegions.size} cohesion regions")
  allCohesionRegions
}

/**
 * Filter that removes table candidates overlapping with cohesion regions
 * unless they fully contain or are fully contained by the cohesion region.
 */
def overlapCohesionFilter(tableRegions: List[TableRegion], cohesionRegions: List[TableRegion]): List[TableRegion] = {
  logger.info("Applying cohesion overlap filter")
  
  // Implementation based on DetectorFilters.cs:OverlapCohensionFilter
  tableRegions.filterNot { tableRegion =>
    cohesionRegions.exists { cohesionRegion =>
      // Check if they overlap but neither fully contains the other
      regionsOverlap(tableRegion, cohesionRegion) && 
      !regionContains(tableRegion, cohesionRegion) && 
      !regionContains(cohesionRegion, tableRegion)
    }
  }
}
```

### Implementation Steps for Formula Correlation (Phase 1.2)

```scala
/**
 * Detect formula relationships between cells and use them to improve table detection.
 * Based on the formula correlation filter in the original SheetCompressor spec.
 */
def formulaCorrelationFilter(grid: SheetGrid, tableRegions: List[TableRegion]): List[TableRegion] = {
  logger.info("Applying formula correlation filter")
  
  // 1. Extract all formulas and their references from the grid
  val formulaReferences = extractFormulaReferences(grid)
  
  // 2. Build a dependency graph of formula relationships
  val formulaGraph = buildFormulaGraph(formulaReferences)
  
  // 3. Identify strongly connected formula components
  val connectedComponents = findConnectedFormulas(formulaGraph)
  
  // 4. Create formula-based table regions
  val formulaRegions = connectedComponents.map { component =>
    createTableRegionFromFormulas(component, grid)
  }
  
  // 5. Merge existing table regions with formula regions where appropriate
  val enhancedRegions = mergeWithFormulaRegions(tableRegions, formulaRegions)
  
  logger.info(s"Formula correlation produced ${enhancedRegions.size} enhanced table regions")
  enhancedRegions
}

/**
 * Extract formula references from cells in the grid.
 * Returns a map of cell coordinates to the cells they reference in formulas.
 */
def extractFormulaReferences(grid: SheetGrid): Map[(Int, Int), Set[(Int, Int)]] = {
  // Implementation details for formula reference extraction
  grid.cells.collect {
    case ((row, col), cell) if cell.isFormula && cell.cellData.isDefined =>
      val cellData = cell.cellData.get
      val referencedCells = cellData.formula
        .map(parseFormulaReferences)
        .getOrElse(Set.empty)
      
      (row, col) -> referencedCells
  }.toMap
}
```

### Implementation Steps for Advanced Header Detection (Phase 1.3)

```scala
/**
 * Enhanced header detection that implements the full logic from HeaderReco.cs.
 * Identifies multiple header types including up headers, left headers, and multi-level headers.
 */
def enhancedHeaderDetection(grid: SheetGrid, region: TableRegion): (Set[Int], Set[Int]) = {
  logger.info(s"Detecting headers for region: $region")
  
  // 1. Detect up headers (column headers)
  val upHeaders = detectUpHeaders(grid, region)
  
  // 2. Detect left headers (row headers)
  val leftHeaders = detectLeftHeaders(grid, region)
  
  // 3. Detect multi-level headers
  val multiLevelUpHeaders = detectMultiLevelHeaders(grid, region, Dimension.Row)
  val multiLevelLeftHeaders = detectMultiLevelHeaders(grid, region, Dimension.Column)
  
  // 4. Combine all header detections
  val allRowHeaders = upHeaders ++ multiLevelUpHeaders
  val allColHeaders = leftHeaders ++ multiLevelLeftHeaders
  
  logger.info(s"Detected ${allRowHeaders.size} row headers and ${allColHeaders.size} column headers")
  (allRowHeaders, allColHeaders)
}

/**
 * Improved implementation of header row detection based on HeaderReco.cs:IsHeaderUp
 */
def isHeaderRow(grid: SheetGrid, rowIndex: Int, colStart: Int, colEnd: Int): Double = {
  // Skip if row is out of bounds
  if rowIndex < 0 || rowIndex >= grid.rowCount then
    return 0.0
    
  // Count cells in the row
  val cells = (colStart to colEnd).flatMap(col => grid.cells.get((rowIndex, col)))
  if cells.isEmpty then
    return 0.0
    
  // Calculate header score based on multiple criteria from original spec
  val boldScore = cells.count(_.isBold).toDouble / cells.size
  val emptyScore = 1.0 - (cells.count(_.isEmpty).toDouble / cells.size)
  val alphabetScore = cells.map(_.alphabetRatio).sum / cells.size
  val numberScore = 1.0 - (cells.map(_.numberRatio).sum / cells.size)
  val borderScore = cells.count(cell => cell.hasBottomBorder || cell.hasTopBorder).toDouble / cells.size
  val fillColorScore = cells.count(_.hasFillColor).toDouble / cells.size
  
  // Combine scores with weighting from original algorithm
  val combinedScore = (
    boldScore * 0.3 + 
    emptyScore * 0.15 + 
    alphabetScore * 0.2 + 
    numberScore * 0.1 + 
    borderScore * 0.15 + 
    fillColorScore * 0.1
  )
  
  // Apply additional adjustments from original algorithm
  val finalScore = applyHeaderHeuristics(combinedScore, cells)
  
  finalScore
}
```

### Implementation Steps for Specialized Filters (Phase 2)

```scala
/**
 * Implementation of SplittedEmptyLinesFilter from original spec.
 * Detects continuous empty rows/cols that can split a box into unrelated regions.
 */
def splittedEmptyLinesFilter(grid: SheetGrid, regions: List[TableRegion]): List[TableRegion] = {
  logger.info("Applying split empty lines filter")
  
  regions.filter { region =>
    verifyBoxSplit(grid, region)
  }
}

/**
 * Verifies a box doesn't have continuous empty lines that split it into two unrelated regions.
 * Implementation based on DetectorFilters.cs:VerifyBoxSplit
 */
def verifyBoxSplit(grid: SheetGrid, region: TableRegion): Boolean = {
  // Implementation for empty line detection and verification
  val rowOffset = if region.height > 12 then 2 else 0
  val colOffset = if region.width > 12 then 2 else 0
  
  // Check for empty rows that split the region
  for (i <- region.topRow + 3 + rowOffset until region.bottomRow - 4) {
    // Check for three continuous rows without contents
    val emptyRegion = TableRegion(i, i + 2, region.leftCol, region.rightCol, Set.empty, Set.empty)
    val centerRow = TableRegion(i + 1, i + 1, region.leftCol, region.rightCol, Set.empty, Set.empty)
    
    val contentInCenterRow = countCellsInRegion(grid, centerRow) 
    if contentInCenterRow < 3 then
      val contentInEmptyRegion = countCellsInRegion(grid, emptyRegion)
      if contentInEmptyRegion == 0 then
        // Check above/below regions to see if they're related
        val hasContentAbove = findContentfulRowAbove(grid, i, region)
        val hasContentBelow = findContentfulRowBelow(grid, i + 2, region)
        
        if hasContentAbove && hasContentBelow then
          return false
  }
  
  // Similar check for columns
  for (j <- region.leftCol + 3 + colOffset until region.rightCol - 4) {
    // Implement empty column check similarly to row check above
    // ...
  }
  
  true
}

/**
 * Implementation of pivot table handling from the original spec.
 */
def overlapPivotFilter(grid: SheetGrid, regions: List[TableRegion], pivotRegions: List[TableRegion]): List[TableRegion] = {
  logger.info(s"Applying pivot table filter with ${pivotRegions.size} pivot regions")
  
  if pivotRegions.isEmpty then
    return regions
    
  // Remove regions overlapping with pivot tables, add pivot regions as candidates
  val regionsToRemove = regions.filter { region =>
    pivotRegions.exists { pivot => regionsOverlap(region, pivot) }
  }
  
  // Add all pivot regions as table candidates
  val filteredRegions = regions.diff(regionsToRemove)
  val resultRegions = filteredRegions ++ pivotRegions
  
  logger.info(s"Pivot table filter resulted in ${resultRegions.size} regions")
  resultRegions
}

/**
 * Merges filtered regions based on merged cells from the original spec.
 */
def mergeFilter(grid: SheetGrid, regions: List[TableRegion], mergedBoxes: List[TableRegion]): List[TableRegion] = {
  logger.info(s"Applying merge filter with ${mergedBoxes.size} merged regions")
  
  if mergedBoxes.isEmpty then
    return regions
  
  // Remove regions that are contained by merged boxes or contain merged boxes
  regions.filterNot { region =>
    mergedBoxes.exists { mergeBox =>
      // Implementation based on DetectorFilters.cs:MergeFilter
      (containsBoxWithTolerance(region, mergeBox, 2) && 
       containsBoxWithTolerance(mergeBox, region, 2)) || 
      mergeBox == region
    }
  }
}

/**
 * Implementation of border-based cohesion detection and filtering from the original spec.
 */
def overlapBorderCohesionFilter(grid: SheetGrid, regions: List[TableRegion], borderRegions: List[TableRegion]): List[TableRegion] = {
  logger.info(s"Applying border cohesion filter with ${borderRegions.size} border regions")
  
  // Implementation based on DetectorFilters.cs:OverlapBorderCohensionFilter
  regions.filterNot { region =>
    borderRegions.exists { borderRegion =>
      // Check if they overlap but don't fully contain each other
      regionsOverlap(region, borderRegion, exceptForward = true, exceptBackward = true)
    }
  }
}

/**
 * Helper method to check if regions overlap with special handling for forward/backward containment.
 */
def regionsOverlap(region1: TableRegion, region2: TableRegion, 
                 exceptForward: Boolean = false, 
                 exceptBackward: Boolean = false): Boolean = {
  // Skip if region1 fully contains region2 and we're excepting forward containment
  if exceptForward && regionContains(region1, region2) then
    return false
    
  // Skip if region2 fully contains region1 and we're excepting backward containment
  if exceptBackward && regionContains(region2, region1) then
    return false
    
  // Check if they overlap
  !(region1.rightCol < region2.leftCol || 
    region1.leftCol > region2.rightCol || 
    region1.bottomRow < region2.topRow || 
    region1.topRow > region2.bottomRow)
}
```

#### Advanced Table Detection (Planned Enhancements)
The next generation of table detection will include these major improvements:

1. **Format-Aware Anchor Detection**
   - Enhanced use of formatting cues (borders, background colors)
   - Detection of header keywords and patterns
   - Better recognition of table titles and captions

2. **Density-Aware Gap Analysis**
   - Considers content density when identifying gaps
   - Prevents splitting tables with intentional spacer rows
   - Configurable density thresholds for different document types

3. **Header Detection and Row Classification**
   - Specialized detection of header rows based on formatting and content
   - Identification of footer/summary rows (e.g., totals, averages)
   - Multi-level header recognition for complex tables

4. **Content-Based Table Clustering**
   - Detection of tables based on content patterns and consistency
   - Column type consistency checking to validate table structures
   - Handling of tables without clear separators

5. **Multi-Pass Table Validation**
   - Strong validation criteria for high-confidence table detection
   - Relaxed fallback criteria to handle edge cases
   - Resolution of overlapping table candidates

6. **Enhanced Configuration Options**
   - Table content density thresholds
   - Header detection settings
   - Adjacent table merging controls
   - Selection between anchor-based, content-based, or hybrid algorithms

These enhancements will significantly improve detection accuracy, especially for complex spreadsheets with multiple adjacent tables, non-standard layouts, or tables with internal spacing.

#### Enhanced Data Format Detection
- Sophisticated pattern recognition for dates, numbers, and currencies
- Detects date formats like MM/DD/YYYY, DD-MMM-YYYY, YYYY-MM-DD
- Identifies currency formats with different symbols ($, €, £, etc.)
- Preserves number formatting information (decimal places, thousands separators)
- Enhances compression while maintaining important formatting cues
- Aligns with Python implementation patterns in TableDataAggregation.py
- Extended type detection for fractions, IP addresses, and emails
- Uses standardized format descriptors like "IntNum", "FloatNum", "DateData", etc.
- Compatible with Excel's Number Format Strings (NFS) for accurate type inference
- Improved scientific notation and numeric detection with enhanced patterns

#### Data Format Debugging
When investigating issues with data type detection, you can use the `--debug-data-detection` flag:
- Provides comprehensive logging of cell data type inference process
- Shows pattern matching details for date formats (YYYY-MM-DD, DD-MMM-YYYY, etc.)
- Logs numeric format detection including handling of thousands separators
- Reports suspicious text cells that might be misclassified dates or numbers
- Helps diagnose issues where cells are incorrectly aggregated as dates/numbers
- Displays Excel's internal flags and format strings for problematic cells
- Analyzes and reports spreadsheet regions with potential detection issues

#### Formula Handling
- Preserves formula expressions in backtick-formatted markdown
- Maps formulas to their target cells in a dedicated section
- Improves understanding of spreadsheet calculations
- Can be disabled with `--no-enhanced-formulas`

#### Semantic Compression
- Enabled with `--semantic-compression`
- Groups similar text values based on pattern recognition and semantic similarity
- Automatically detects and categorizes:
  - Country names (e.g., "United States", "Canada", "France")
  - Person names (e.g., "John Smith", "Jane Doe")
  - Company names (e.g., "Acme Corp", "Microsoft Inc.")
  - City names (e.g., "New York", "San Francisco")
  - Phone numbers, URLs, and other structured text formats
- Creates intelligent groupings for similar text values
- Preserves representative samples while reducing token usage
- Especially useful for spreadsheets with many similar text entries
- Maintains structural information while dramatically reducing token count