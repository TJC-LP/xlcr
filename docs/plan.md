# core-spreadsheetllm Implementation Plan

## Overview

The `core-spreadsheetllm` module will implement Microsoft's SpreadsheetLLM/SheetCompressor approach as a document conversion pipeline compatible with the existing XLCR architecture. This module will provide bridges to convert Excel spreadsheets to a compact, LLM-friendly JSON representation while preserving essential structural information.

## Project Structure

Following the existing XLCR design patterns:

```
xlcr/
├── core-spreadsheetllm/
│   ├── src/
│   │   ├── main/
│   │   │   ├── resources/
│   │   │   │   └── logback.xml
│   │   │   └── scala/
│   │   │       └── com/tjclp/xlcr/
│   │   │           └── spreadsheetllm/
│   │   │               ├── SpreadsheetLLMConfig.scala
│   │   │               ├── main.scala
│   │   │               ├── bridges/
│   │   │               │   └── spreadsheetllm/
│   │   │               │       └── SpreadsheetLLMBridgeRegistry.scala
│   │   │               │       └── ExcelToLLMJsonBridge.scala
│   │   │               ├── models/
│   │   │               │   ├── CompressedSheet.scala
│   │   │               │   └── CompressedWorkbook.scala
│   │   │               ├── parsers/
│   │   │               │   └── spreadsheetllm/
│   │   │               │       └── ExcelToLLMParser.scala
│   │   │               ├── renderers/
│   │   │               │   └── spreadsheetllm/
│   │   │               │       └── LLMJsonRenderer.scala
│   │   │               └── compression/
│   │   │                   ├── AnchorExtractor.scala
│   │   │                   ├── InvertedIndexTranslator.scala
│   │   │                   ├── DataFormatAggregator.scala
│   │   │                   └── CompressionPipeline.scala
│   │   └── test/
│   │       └── scala/
│   │           └── com/tjclp/xlcr/
│   │               └── spreadsheetllm/
│   │                   ├── SpreadsheetLLMBridgeRegistrySpec.scala
│   │                   ├── compression/
│   │                   │   ├── AnchorExtractorSpec.scala
│   │                   │   ├── InvertedIndexTranslatorSpec.scala
│   │                   │   └── DataFormatAggregatorSpec.scala
│   │                   ├── parsers/
│   │                   └── renderers/
```

## Implementation Components

### 1. Model Classes

Create the internal model representations:

- **CompressedSheet**: Represents a single spreadsheet after compression
  - Contains cell content, addresses, and format information
  - Supports structure-preserving annotations

- **CompressedWorkbook**: Collection of compressed sheets with metadata
  - Sheet names
  - Workbook-level properties

### 2. Compression Pipeline

The core compression algorithms divided into three main stages:

#### a) Anchor Extractor

- Identifies structural anchors (headers, table boundaries)
- Prunes unnecessary content while preserving structure
- Remaps coordinates for contiguous representation

#### b) Inverted Index Translator

- Converts grid data to a compact dictionary format
- Maps unique cell content to locations
- Merges duplicate values and consolidates ranges

#### c) Data Format Aggregator

- Detects patterns in cell formats and types
- Clusters similar cell regions (numbers, dates, etc.)
- Replaces values with type descriptors to reduce token usage

### 3. Parser Implementation

Create an Excel parser that leverages Apache POI to:

- Support multiple file formats (.xlsx, .xls, .xlsm, .xlsb)
- Handle large files efficiently with streaming API
- Extract cell data, formulas, and formatting

### 4. Renderer Implementation

Implement a JSON renderer that:

- Produces structured output with markdown elements
- Follows the SpreadsheetLLM encoding format
- Balances between token minimization and structure preservation

### 5. Bridge Component

Create a bridge that connects the parser and renderer:

- **ExcelToLLMJsonBridge**: Converts Excel files to LLM-friendly JSON
- Registers with the XLCR BridgeRegistry for normal pipeline use

### 6. CLI Interface 

Implement a command-line interface similar to other XLCR modules:

```
$ sbt "core-spreadsheetllm/run -i input.xlsx -o output.json"
```

With options for compression controls:
- `--anchor-threshold`: Sets neighborhood size around anchors
- `--aggregate-formats`: Enables/disables format aggregation
- `--threads`: Controls parallelism for large files

## Implementation Phases

### Phase 1: Core Framework and Models

1. Set up project structure in build.sbt
2. Create model classes (CompressedSheet, CompressedWorkbook)
3. Implement basic configuration handling

### Phase 2: Compression Pipeline

1. Implement AnchorExtractor
2. Create InvertedIndexTranslator
3. Build DataFormatAggregator
4. Develop tests for each component

### Phase 3: Excel Parser and JSON Renderer

1. Create ExcelToLLMParser using Apache POI
2. Implement LLMJsonRenderer with markdown support
3. Test with various Excel file formats

### Phase 4: Bridge and Integration

1. Build ExcelToLLMJsonBridge connecting parser and renderer
2. Register bridge with the XLCR BridgeRegistry
3. Create command-line interface with options
4. Test end-to-end pipeline functionality

### Phase 5: Optimization and Refinement

1. Add parallel processing for large sheets
2. Optimize memory usage for streaming large files
3. Tune compression parameters for optimal balance
4. Benchmark and document performance characteristics

## Success Criteria

1. Successfully compresses spreadsheets with 20-25x token reduction
2. Preserves essential structural information
3. Produces valid JSON with embedded markdown
4. Handles all Excel file formats
5. Works with large files efficiently
6. Integrates with the existing XLCR pipeline architecture