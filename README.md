# XLCR 
## e**X**tensible **L**anguage **C**omputation **R**untime

XLCR is a powerful and flexible command-line tool designed for language processing and computation tasks. It provides a runtime environment for extracting, analyzing, and transforming content between various file formats.

## Features

Core Module (`core/run`):
- Excel to JSON/Markdown/SVG conversions and back
- PowerPoint to JSON conversions and back
- Tika-based text and XML extraction from various formats
- SVG to PNG conversion support
- Extensible bridge architecture for format conversions
- Diff/merge support for compatible formats
- Directory-based batch processing

Aspose Module (`coreAspose/run`):
- Professional PDF output from Word, Excel, PowerPoint, and Email files
- High-quality document conversion using Aspose libraries
- Requires valid Aspose license(s) for production use

SpreadsheetLLM Module (`coreSpreadsheetLLM/run`):
- Compress Excel spreadsheets into LLM-friendly JSON format
- Based on Microsoft's SpreadsheetLLM/SheetCompressor research
- Three-stage compression pipeline: anchor extraction, inverted index, format aggregation
- Reduces token usage while preserving essential structure

Server Module (Experimental):
- WebSocket-based document editing server
- Model Context Protocol (MCP) integration
- Currently under development and not recommended for production use

## Prerequisites

- Java 11 or higher
- SBT (Scala Build Tool)

## Installation

1. Clone the repository:
   ```
   git clone https://github.com/TJC-LP/xlcr.git
   cd xlcr
   ```

2. Build the project:
   ```
   sbt compile
   ```

## Usage

XLCR provides two main command-line interfaces:

### Core Module
Basic format conversions using open-source libraries:
```
sbt
> core/run --input "<input_file>" --output "<output_file>" [--diff true]
```

### Aspose Module
Professional document conversion using Aspose:
```
sbt
> coreAspose/run --input "<input_file>" --output "<output_file>" [--licenseTotal "<license_path>"]
```

Additional Aspose license options:
- `--licenseWords`: Aspose.Words license path
- `--licenseCells`: Aspose.Cells license path
- `--licenseEmail`: Aspose.Email license path
- `--licenseSlides`: Aspose.Slides license path

### SpreadsheetLLM Module
Compress Excel spreadsheets for LLM processing:
```
sbt
> coreSpreadsheetLLM/run --input "<input_file>" --output "<output_file>" [--anchor-threshold <n>]
```

Additional SpreadsheetLLM options:
- `--no-anchor`: Disable anchor-based pruning, keeping full sheet content
- `--no-format`: Disable format-based aggregation, keeping all values as-is
- `--threads <n>`: Control parallel processing thread count

### Directory Processing
Both modules support directory-based processing with MIME type mapping:
```
> core/run --input "input_dir" --output "output_dir" --mapping "xlsx=json,docx=pdf"
```

The output format is determined by file extension or explicit MIME type mapping.

## Development

To run tests:
```
sbt test
```

To create a distributable package:
```
sbt package
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

Note: The Aspose module requires valid Aspose licenses for production use. Evaluation/trial licenses can be obtained from Aspose directly.