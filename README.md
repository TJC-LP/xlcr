# XLCR 
## e**X**tensible **L**anguage **C**omputation **R**untime

XLCR is a powerful and flexible command-line tool designed for language processing and computation tasks. It provides a runtime environment for extracting, analyzing, and transforming content between various file formats.

## Features

Core Module (`core/run`):
- Excel to JSON/Markdown/SVG conversions and back
- PowerPoint to JSON conversions and back
- Tika-based text and XML extraction from various formats
- SVG to PNG conversion support
- Document splitting (pages, sheets, slides, archive entries, etc.)
- Configurable failure handling for robust document processing
- ZIP and other archive format extraction with recursive support
- Extensible bridge architecture for format conversions
- Diff/merge support for compatible formats
- Directory-based batch processing

Aspose Module (`coreAspose/run`):
- Professional PDF output from Word, Excel, PowerPoint, and Email files
- High-quality document conversion using Aspose libraries
- Enhanced ZIP archive extraction using Aspose.ZIP with recursive capabilities and zipbomb protection
- Professional-grade document splitting
- Requires valid Aspose license(s) for production use
- Shares the same CLI interface as core module for consistency

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

XLCR provides two main command-line interfaces, each supporting both conversion (file-to-file) and splitting (file-to-directory) operations:

### Core Module
Basic format conversions using open-source libraries:
```
sbt
> core/run --input "<input_file>" --output "<output_file>" [--diff true]
```

#### Split Mode
Split documents into individual pieces (pages, sheets, slides, archive entries, etc.):
```
sbt
> core/run --input "<input_file>" --output "<output_directory>" --split [--strategy <strategy>] [--type <output_type>] [--recursive] [--max-recursion-depth <n>]
```

Available split strategies:
- `page`: Split PDF documents into individual pages (default for PDFs)
- `sheet`: Split Excel workbooks into individual sheets (default for Excel)
- `slide`: Split PowerPoint presentations into individual slides (default for PowerPoint)
- `attachment`: Extract attachments from email files (default for EML/MSG)
- `embedded`: Extract entries from archive files (default for ZIP/TAR/etc.)
- `heading`: Split Word documents by headings
- `paragraph`, `row`, `column`, `sentence`: Other available strategies

Examples:
```
# Split a PDF into individual pages
> core/run --input "document.pdf" --output "pages/" --split

# Split an Excel file into individual sheets as JSON files
> core/run --input "workbook.xlsx" --output "sheets/" --split --type "json"

# Extract all files from a ZIP archive
> core/run --input "archive.zip" --output "extracted/" --split --strategy "embedded"

# Recursively extract nested ZIP archives (with zipbomb protection)
> core/run --input "nested_archive.zip" --output "extracted/" --split --strategy "embedded" --recursive

# Split a PowerPoint presentation into individual PNG images
> core/run --input "data/import/powerpoint.pptx" --output "data/export/slides/" --split --type "png"

# Convert PDF to high-quality JPEG images
> core/run --input "document.pdf" --output "images/" --split --strategy page --format jpg --dpi 300 --quality 0.9
```

### Aspose Module
Professional document conversion using Aspose:
```
sbt
> coreAspose/run --input "<input_file>" --output "<output_file>" [--licenseTotal "<license_path>"]
```

The Aspose module also supports split mode with enhanced capabilities:
```
sbt
> coreAspose/run --input "<input_file>" --output "<output_directory>" --split [--strategy <strategy>] [--recursive] [--max-recursion-depth <n>]
```

Examples:
```
# Convert Word document to PDF and split into pages using Aspose
> coreAspose/run --input "document.docx" --output "pages/" --split --strategy "page"

# Extract files from ZIP archive using Aspose.ZIP
> coreAspose/run --input "archive.zip" --output "extracted/" --split

# Recursively extract nested ZIP archives (handles ZIP files within ZIP files)
> coreAspose/run --input "nested_archive.zip" --output "extracted/" --split --recursive

# Convert PDF to PNG images with specific dimensions and quality settings
> coreAspose/run --input "document.pdf" --output "images/" --split --strategy page --format png --max-width 1600 --max-height 1200 --dpi 150
```

Additional Aspose module options:

Archive extraction options:
- `--recursive`: Enable recursive extraction of archives (ZIP within ZIP)
- `--max-recursion-depth <n>`: Maximum recursion depth for nested archives (default: 5)

PDF to image conversion options:
- `--format`: Output format for PDF pages: pdf (default), png, or jpg
- `--max-width`: Maximum width in pixels for image output (default: 2000)
- `--max-height`: Maximum height in pixels for image output (default: 2000)
- `--max-size`: Maximum size in bytes for image output (default: 5MB)
- `--dpi`: DPI for PDF rendering (default: 300)
- `--quality`: JPEG quality (0.0-1.0, default: 0.85)

License options:
- `--licenseWords`: Aspose.Words license path
- `--licenseCells`: Aspose.Cells license path
- `--licenseEmail`: Aspose.Email license path
- `--licenseSlides`: Aspose.Slides license path
- `--licenseZip`: Aspose.ZIP license path

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