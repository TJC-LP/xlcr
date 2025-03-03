# XLCR Development Guide

## Build Commands
- `sbt compile` - Compile the project
- `sbt assembly` - Create executable JAR files
- `sbt run` - Run the application
- `sbt "server/run"` - Run the server component
- `sbt "coreSpreadsheetLLM/run -i input.xlsx -o output.json"` - Run the SpreadsheetLLM module

## Test Commands
- `sbt test` - Run all tests
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
- `core-aspose` - Integration with Aspose for PDF conversion
- `core-spreadsheetllm` - Excel compression for LLM processing
- `server` - Kotlin-based server for interactive editing

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