# XLCR Development Guide

## Java Version Requirements

XLCR modules have different Java version requirements:
- **Core, Aspose, LibreOffice, CLI, Server**: Java 17+ (tested with Java 17, 21, 25)

### Aspose License for Local Testing

The `core-aspose` tests require a valid Aspose license to run without evaluation watermarks. CI uses the `ASPOSE_TOTAL_LICENSE_B64` environment variable. For local development:

**Option 1: Copy license to resources** (recommended)
```bash
cp Aspose.Total.Java.lic core-aspose/resources/
```

**Option 2: Set environment variable**
```bash
export ASPOSE_TOTAL_LICENSE_B64=$(base64 < Aspose.Total.Java.lic)
./mill 'core-aspose.test'
```

The license file in `core-aspose/resources/` is gitignored (`*.lic` pattern).

### Aspose Build & License Environment Variables

**Build-time** (controls module inclusion in JAR):

| Variable | Effect |
|---|---|
| `XLCR_NO_ASPOSE=1` | Exclude core-aspose from build (smaller JAR, no Aspose deps) |

Aspose is included by default. Use `XLCR_NO_ASPOSE=1` only for lightweight deployments.

**Runtime** (controls license resolution, precedence order):

| Variable | Effect |
|---|---|
| `XLCR_NO_ASPOSE_LICENSE=1` | Kill ALL license resolution -- complete blackout (highest priority) |
| `XLCR_NO_CLASSPATH_LICENSE=1` | Skip JAR-bundled licenses; CWD files + env vars still work |
| `ASPOSE_TOTAL_LICENSE_B64` | Base64-encoded total license (all products) |
| `ASPOSE_WORDS_LICENSE_B64` | Per-product: Words only |
| `ASPOSE_CELLS_LICENSE_B64` | Per-product: Cells only |
| `ASPOSE_SLIDES_LICENSE_B64` | Per-product: Slides only |
| `ASPOSE_PDF_LICENSE_B64` | Per-product: Pdf only |
| `ASPOSE_EMAIL_LICENSE_B64` | Per-product: Email only |
| `ASPOSE_ZIP_LICENSE_B64` | Per-product: Zip only |

## Build Commands
- `./mill __.compile` - Compile all modules
- `./mill core.compile` - Compile a specific module
- `./mill __.assembly` - Create executable JAR files

## Test Commands
- `./mill __.test` - Run all tests
- `./mill core.test` - Run tests for a specific module
- `./mill core.test.testOnly com.tjclp.xlcr.ConfigSpec` - Run a single test class
- `./mill __.checkFormat` - Check code formatting
- `./mill __.reformat` - Reformat code

## Code Style
- Scala 3 with functional programming principles
- Immutable data structures preferred
- Package organization follows com.tjclp.xlcr convention
- CamelCase for methods/variables, PascalCase for classes/objects
- Prefer Option/Either for error handling over exceptions
- Make illegal states unrepresentable through type system
- Models should be immutable case classes with well-defined interfaces

## Module Structure
The project is organized into these main modules:
- `core` - Tika text extraction, document splitters (PDF/Excel/PowerPoint/Word/Email/Archives), XLSX->ODS conversion
- `core-aspose` - Aspose-based document conversions (HIGH priority backend)
- `core-libreoffice` - LibreOffice-based conversions via JODConverter (DEFAULT priority fallback)
- `cli` - Command-line interface (`xlcr convert`, `xlcr server`)
- `server` - HTTP server (ZIO HTTP)
- `xlcr` - Unified assembly module wiring all backends together

## Conversion Dispatch: UnifiedTransforms

All conversions route through `UnifiedTransforms` (compile-time wired in the `xlcr` module). When no backend is specified, it tries each in order with automatic fallback:

1. **Aspose** (BackendWiring) -- highest quality, requires license
2. **LibreOffice** (LibreOfficeTransforms) -- open-source fallback
3. **XLCR Core** (XlcrTransforms) -- POI/Tika/PDFBox built-ins

When a specific `backend` is requested, only that backend is used with no fallback.

## CLI Usage

```bash
# Basic conversion
xlcr convert -i input.docx -o output.pdf

# PowerPoint to HTML with master slide removal
xlcr convert -i presentation.pptx -o output.html --strip-masters

# Two-stage PDF -> editable PowerPoint (recommended)
xlcr convert -i document.pdf -o intermediate.html
xlcr convert -i intermediate.html -o presentation.pptx
```

## HTTP Server

### Starting the Server

```bash
xlcr server start --port 8080
```

### Server Options

| Flag | Env Var | Default | Description |
|---|---|---|---|
| `--host` | `XLCR_HOST` | `0.0.0.0` | Bind address |
| `--port`, `-p` | `XLCR_PORT` | `8080` | Listen port |
| `--max-request-size` | `XLCR_MAX_REQUEST_SIZE` | `104857600` (100MB) | Max request body bytes |
| `--lo-instances` | `XLCR_LO_INSTANCES` | `1` | LibreOffice process pool size |
| `--lo-restart-after` | `XLCR_LO_RESTART_AFTER` | `200` | Restart LO process after N conversions |
| `--lo-task-timeout` | `XLCR_LO_TASK_TIMEOUT` | `120000` (2min) | LO task execution timeout (ms) |
| `--lo-queue-timeout` | `XLCR_LO_QUEUE_TIMEOUT` | `30000` (30s) | LO task queue timeout (ms) |

CLI flags take precedence over env vars, which take precedence over defaults.

### Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | Server info and version |
| `GET` | `/health` | Health check (includes LO pool status) |
| `GET` | `/capabilities` | List all supported conversions and splits |
| `POST` | `/convert?to=` | Convert document to target format |
| `POST` | `/split` | Split document into fragments (returns ZIP) |
| `POST` | `/info` | Document metadata and available conversions |

### Query Parameters

| Param | Applies To | Description |
|---|---|---|
| `to` (required) | `/convert` | Target format: MIME type, extension, or alias (`pdf`, `text/plain`, `html`, etc.) |
| `backend` | `/convert`, `/split` | Force specific backend: `aspose`, `libreoffice`, or `xlcr` (no fallback) |
| `detect=tika` | `/convert`, `/split` | Force Tika content detection, ignore Content-Type header |

**Content-Type handling**: `application/octet-stream` and `application/x-www-form-urlencoded` are treated as unknown and trigger automatic Tika detection.

### Examples

```bash
# Convert DOCX to PDF
curl -X POST "http://localhost:8080/convert?to=pdf" \
  -H "Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document" \
  --data-binary @document.docx -o output.pdf

# Convert with Tika detection (unknown file type)
curl -X POST "http://localhost:8080/convert?to=html&detect=tika" \
  --data-binary @mystery-file -o output.html

# Force LibreOffice backend
curl -X POST "http://localhost:8080/convert?to=pdf&backend=libreoffice" \
  --data-binary @spreadsheet.xlsx -o output.pdf

# Health check
curl http://localhost:8080/health

# List capabilities
curl http://localhost:8080/capabilities
```

## LibreOffice Backend

### Installation

```bash
# macOS
brew install --cask libreoffice

# Linux (Ubuntu/Debian)
sudo apt-get install libreoffice
```

Default paths: `/Applications/LibreOffice.app/Contents` (macOS), `/usr/lib/libreoffice` (Linux).
Override with `LIBREOFFICE_HOME=/path/to/libreoffice`.

### Supported Conversions

DOCX, DOC, XLSX, XLS, XLSM, PPTX, PPT, ODT, ODP, RTF -> PDF

### Module Structure

```
core-libreoffice/src/main/scala/com/tjclp/xlcr/
  libreoffice/
    LibreOfficeTransforms.scala   # Transform dispatcher
    conversions.scala             # Conversion implementations
    splitters.scala               # Split implementations
  config/
    LibreOfficeConfig.scala       # JODConverter + process pool config
```
