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
- `--no-coordinate-correction` - Disable auto coordinate correction for large sheets
- `--correction-value <n>` - Value for coordinate correction (default: 2)
- `--threads <n>` - Number of threads for parallel processing
- `--verbose` - Enable verbose logging

### Coordinate Correction Feature
The SpreadsheetLLM module includes a coordinate correction system for large sheets:
- Preserves logical structure when compressing sparse spreadsheets
- Stores original coordinates to detect and correct shifts
- Can be disabled with `--no-coordinate-correction` if needed
- The correction value defaults to 2 but can be adjusted with `--correction-value`
- Fixes the "off by 2" issue that can occur in large sheets