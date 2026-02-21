# XLCR

[![Maven Central](https://img.shields.io/maven-central/v/com.tjclp/xlcr-core_3)](https://central.sonatype.com/search?q=com.tjclp.xlcr)
[![CI](https://github.com/TJC-LP/xlcr/actions/workflows/ci.yml/badge.svg)](https://github.com/TJC-LP/xlcr/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**eXtensible Language Computation Runtime** - a document conversion and splitting toolkit for the JVM.

XLCR converts between document formats (PDF, DOCX, XLSX, PPTX, HTML, ODS, and more) and splits documents into fragments (pages, sheets, slides, attachments). It ships as both a CLI tool and a library publishable to Maven Central.

## Modules

| Module | Artifact | Description |
|--------|----------|-------------|
| **core** | `xlcr-core` | Tika text extraction, document splitters (PDF/Excel/PowerPoint/Word/Email/Archive), XLSX-to-ODS conversion |
| **core-aspose** | `xlcr-core-aspose` | Aspose-powered conversions: PDF/DOCX/XLSX/PPTX/HTML with HIGH priority (commercial license required) |
| **core-libreoffice** | `xlcr-core-libreoffice` | LibreOffice-powered conversions: DOC/XLS/PPT/ODS to PDF as open-source fallback |
| **core-spark** | `xlcr-core-spark` | Spark DataFrame integration for batch document processing |
| **xlcr** | `xlcr` | Unified CLI with compile-time transform discovery and automatic backend fallback (Scala 3 only) |
| **server** | `xlcr-server` | HTTP REST API for document conversion and splitting (Scala 3 only) |

Backend selection is automatic: Aspose (HIGH priority) > LibreOffice (DEFAULT) > Core. You can also select a backend explicitly with `--backend aspose` or `--backend libreoffice`.

## Prerequisites

- **Java 17+** (tested with Java 17 and 21)
- **Mill** build tool (included via `./mill` wrapper script)
- **LibreOffice** (optional, for `core-libreoffice` backend)
- **Aspose license** (optional, for `core-aspose` backend without watermarks)

## Quick Start

### Install from Source

```bash
git clone https://github.com/TJC-LP/xlcr.git
cd xlcr

# Build and install to ~/bin (no sudo)
make install-user

# Or install to /usr/local/bin (requires sudo)
make install
```

### Use as a Library

```scala
// build.mill (Mill)
def mvnDeps = Seq(
  mvn"com.tjclp::xlcr-core:0.1.0",
  mvn"com.tjclp::xlcr-core-aspose:0.1.0"  // optional
)
```

```scala
// build.sbt (sbt)
libraryDependencies ++= Seq(
  "com.tjclp" %% "xlcr-core" % "0.1.0",
  "com.tjclp" %% "xlcr-core-aspose" % "0.1.0"  // optional
)
```

Cross-published for **Scala 3.3.4** and **Scala 2.13.14**.

## CLI Usage

### Convert Documents

```bash
# Convert Word to PDF
xlcr convert -i document.docx -o output.pdf

# Convert with a specific backend
xlcr convert -i document.docx -o output.pdf --backend libreoffice

# Convert HTML to PowerPoint
xlcr convert -i presentation.html -o output.pptx

# Convert PDF to HTML (recommended for best editability)
xlcr convert -i document.pdf -o output.html
```

### Split Documents

```bash
# Split PDF into individual pages
xlcr split -i document.pdf -d pages/

# Split Excel workbook into sheets
xlcr split -i workbook.xlsx -d sheets/

# Split PowerPoint into slides
xlcr split -i presentation.pptx -d slides/

# Extract email attachments
xlcr split -i message.eml -d attachments/
```

### Other Commands

```bash
# Show document metadata
xlcr info -i document.pdf

# List all supported conversions
xlcr --backend-info

# Version
xlcr --version
```

### PowerPoint Workflows

```bash
# Strip template/branding for clean output
xlcr convert -i branded.pptx -o clean.html --strip-masters

# Two-stage PDF to PowerPoint (best editability, smallest files)
xlcr convert -i document.pdf -o intermediate.html
xlcr convert -i intermediate.html -o presentation.pptx
```

## HTTP Server

The server module exposes document conversion as a REST API:

```bash
# Start the server
./mill 'server[3.3.4].run'

# Or with custom port
XLCR_PORT=9000 ./mill 'server[3.3.4].run'
```

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/convert?to=<mime>` | Convert document to target format |
| `POST` | `/split` | Split document into fragments (ZIP output) |
| `POST` | `/info` | Get document metadata |
| `GET` | `/capabilities` | List all supported conversions |
| `GET` | `/health` | Health check |

### Examples

```bash
# Convert DOCX to PDF
curl -X POST "http://localhost:8080/convert?to=pdf" \
  -H "Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document" \
  --data-binary @document.docx -o output.pdf

# Split XLSX into sheets
curl -X POST "http://localhost:8080/split" \
  -H "Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" \
  --data-binary @workbook.xlsx -o sheets.zip

# List capabilities
curl http://localhost:8080/capabilities
```

## Development

### Build Commands

```bash
./mill __.compile                    # Compile all modules
./mill __[3.3.4].test                # Run all tests (Scala 3)
./mill __[2.13.14].test              # Run all tests (Scala 2.13)
./mill core[3.3.4].test              # Run tests for a specific module
./mill __.checkFormat                # Check code formatting
./mill __.reformat                   # Fix formatting
./mill __.assembly                   # Build fat JARs
```

### LibreOffice Setup

For the `core-libreoffice` module:

```bash
# macOS
brew install --cask libreoffice

# Ubuntu/Debian
sudo apt-get install libreoffice

# Custom path
export LIBREOFFICE_HOME=/path/to/libreoffice
```

### Aspose License

For `core-aspose` tests without watermarks:

```bash
# Option 1: Copy license to resources
cp Aspose.Total.Java.lic core-aspose/resources/

# Option 2: Environment variable
export ASPOSE_TOTAL_LICENSE_B64=$(base64 < Aspose.Total.Java.lic)
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

Note: The Aspose module requires valid Aspose licenses for production use. Evaluation/trial licenses can be obtained from Aspose directly.
