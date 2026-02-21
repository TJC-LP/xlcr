# XLCR Development Guide

## Java Version Requirements

XLCR modules have different Java version requirements:
- **Core, Aspose, LibreOffice**: Java 17+ (tested with Java 17, 21, 25)
- **Spark Module**: Java 17 or 21 only (Spark 3.x limitation - Java 25 not supported)

### Running Tests with Java 25

If you're using Java 25 locally, you have two options:

**Option 1: Skip Spark tests** (recommended for LibreOffice/core development)
```bash
./mill core[3.3.4].test core-aspose[3.3.4].test core-libreoffice[3.3.4].test
```

**Option 2: Use Java 17 for Spark tests**
```bash
# Set Java 17 for this session
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mill __.test
```

### Aspose License for Local Testing

The `core-aspose` tests require a valid Aspose license to run without evaluation watermarks. CI uses the `ASPOSE_TOTAL_LICENSE_B64` environment variable. For local development:

**Option 1: Copy license to resources** (recommended)
```bash
cp Aspose.Total.Java.lic core-aspose/resources/
```

**Option 2: Set environment variable**
```bash
export ASPOSE_TOTAL_LICENSE_B64=$(base64 < Aspose.Total.Java.lic)
./mill 'core-aspose[3.3.4].test'
```

The license file in `core-aspose/resources/` is gitignored (`*.lic` pattern).

### Mill Daemon Caching

The `asposeEnabled` build flag is evaluated once when the Mill daemon compiles `build.mill`. If you add or remove license files from `core-aspose/resources/`, the daemon won't notice:
```bash
./mill shutdown && ./mill clean   # Force re-evaluation of asposeEnabled
```

### Aspose License Override Hierarchy

Environment variables control license resolution at runtime (precedence order):

| Variable | Effect |
|---|---|
| `XLCR_NO_ASPOSE_LICENSE=1` | Kill ALL license resolution — complete blackout (highest priority) |
| `XLCR_NO_CLASSPATH_LICENSE=1` | Skip JAR-bundled licenses; CWD files + env vars still work |
| `ASPOSE_TOTAL_LICENSE_B64` | Base64-encoded total license (all products) |
| `ASPOSE_WORDS_LICENSE_B64` | Per-product: Words only |
| `ASPOSE_CELLS_LICENSE_B64` | Per-product: Cells only |
| `ASPOSE_SLIDES_LICENSE_B64` | Per-product: Slides only |
| `ASPOSE_PDF_LICENSE_B64` | Per-product: Pdf only |
| `ASPOSE_EMAIL_LICENSE_B64` | Per-product: Email only |
| `ASPOSE_ZIP_LICENSE_B64` | Per-product: Zip only |
| `XLCR_ASPOSE_ENABLED=1` | Build-time override: include core-aspose even without a detected license |

## Build Commands
- `./mill __.compile` - Compile all modules for all Scala versions
- `./mill __[3.3.4].compile` - Compile all modules with Scala 3.3.4
- `./mill __[2.13.14].compile` - Compile all modules with Scala 2.13.14
- `./mill core[3.3.4].compile` - Compile a specific module with a specific Scala version
- `./mill __.assembly` - Create executable JAR files
- `./mill core[3.3.4].run` - Run the core application

## Test Commands
- `./mill __.test` - Run all tests for all modules and Scala versions
- `./mill __[3.3.4].test` - Run all tests with Scala 3.3.4
- `./mill __[2.13.14].test` - Run all tests with Scala 2.13.14
- `./mill core[3.3.4].test` - Run tests for a specific module
- `./mill core[3.3.4].test.testOnly com.tjclp.xlcr.ConfigSpec` - Run a single test class
- `./mill __.checkFormat` - Check code formatting
- `./mill __.reformat` - Reformat code

## Scala Versions
XLCR supports cross-building for:
- **Scala 3.3.4** (primary version)
- **Scala 2.13.14** (cross-build support)

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
- `core` - Core functionality: Tika text extraction, splitters (PDF/Excel/PowerPoint/Word/Email/Archives), XLSX→ODS conversion
- `core-aspose` - Integration with Aspose for PDF conversion and document transformations (HIGH priority)
- `core-libreoffice` - Integration with LibreOffice for open-source document conversions (DEFAULT priority fallback)
- `core-spark` - Spark DataFrame integration for document processing
- `data` - Directory containing sample Excel files for testing

### Core Module Capabilities
The core module provides:
- **Tika Text Extraction**: Universal fallback for extracting plain text or XML from any document
- **Document Splitters**: Extract pages/sheets/slides from documents
  - PDF: PdfPageSplitter
  - Excel: ExcelXlsSheetSplitter, ExcelXlsxSheetSplitter, OdsSheetSplitter
  - PowerPoint: PowerPointPptSlideSplitter, PowerPointPptxSlideSplitter
  - Word: WordDocRouterSplitter, WordDocxRouterSplitter
  - Email: EmailAttachmentSplitter, OutlookMsgSplitter
  - Archives: ZipEntrySplitter
  - Text: TextSplitter, CsvSplitter
- **Format Conversion**: XLSX → ODS (ExcelToOdsBridge)

For Excel/PowerPoint JSON conversions, use the `~/git/xl` library instead.

## Document Conversion
The core-aspose module includes comprehensive document conversion capabilities:

### Supported Conversions
- **HTML ↔ PowerPoint**: Bidirectional conversion between HTML and PowerPoint formats
  - HTML → PPTX (PowerPoint Open XML)
  - HTML → PPT (PowerPoint 97-2003)
  - PPTX → HTML
  - PPT → HTML
- **PDF → PowerPoint**: Direct conversion from PDF to editable PowerPoint
  - PDF → PPTX (each page becomes a slide)
  - PDF → PPT (each page becomes a slide)
- **PDF → HTML**: Convert PDF to structured HTML (NEW - recommended for best editability!)
- **Two-Stage Workflow**: PDF → HTML → PowerPoint (best for editable output)

### CLI Usage Examples
```bash
# Convert HTML to PowerPoint (PPTX)
./mill core[3.3.4].run -i presentation.html -o output.pptx

# Convert HTML to legacy PowerPoint (PPT)
./mill core[3.3.4].run -i presentation.html -o output.ppt

# Convert PowerPoint to HTML
./mill core[3.3.4].run -i presentation.pptx -o output.html

# Convert PowerPoint to HTML with master slide removal (cleaner output)
./mill core[3.3.4].run -i presentation.pptx -o output.html --strip-masters

# Convert legacy PowerPoint to HTML
./mill core[3.3.4].run -i presentation.ppt -o output.html

# Template swapping workflow: strip template, convert, apply new template
./mill core[3.3.4].run -i old-template.pptx -o clean.html --strip-masters
./mill core[3.3.4].run -i clean.html -o new-presentation.pptx
# Then apply new template in PowerPoint

# Convert PDF to PowerPoint (PPTX) - each page becomes a slide
./mill core[3.3.4].run -i document.pdf -o presentation.pptx

# Convert PDF to legacy PowerPoint (PPT)
./mill core[3.3.4].run -i document.pdf -o presentation.ppt

# === PDF → HTML Conversion (NEW!) ===

# Convert PDF to HTML (preserves structure better)
./mill core[3.3.4].run -i document.pdf -o output.html

# Convert encrypted PDF to HTML
./mill core[3.3.4].run -i encrypted.pdf -o output.html  # Auto-handles restrictions

# === Two-Stage Workflow (RECOMMENDED for Best Editability) ===

# Stage 1: PDF → HTML (extract structured content)
./mill core[3.3.4].run -i document.pdf -o intermediate.html

# Stage 2: HTML → PowerPoint (create editable slides)
./mill core[3.3.4].run -i intermediate.html -o presentation.pptx

# Why two-stage? File size: 76MB direct vs 254KB two-stage!
```

### Technical Details

**PowerPoint Conversion**:
- Uses Aspose.Slides for Java for HTML ↔ PowerPoint
- Handles HTML structure with best-effort slide creation
- Automatically removes unused master slides and layout slides

**PDF → HTML Conversion** (NEW):
- Uses Aspose.PDF for Java with HtmlSaveOptions
- Flowing layout mode for better editability (vs fixed positioning)
- Embeds all resources (fonts, images) into single HTML file
- Preserves text as editable text (not images)
- Table structure preservation enabled
- Automatically handles encrypted and restricted PDFs (removes copy/edit restrictions)

**PDF → PowerPoint Conversion** (Direct):
- Each page in the PDF becomes a slide
- Uses Aspose.Slides' addFromPdf() method
- Good visual fidelity, moderate editability
- Automatically handles encrypted and restricted PDFs
- Larger file sizes (76MB for 94-page document)

**PDF → HTML → PowerPoint** (Two-Stage - RECOMMENDED):
- Best editability and smallest file size
- Better structure preservation through HTML intermediate format
- Dramatically smaller output (254KB vs 76MB for 94-page document)
- Recommended when PowerPoint editability is priority
- Slightly lower visual fidelity vs direct conversion
- Optional `--strip-masters` flag creates clean copies during PowerPoint → HTML conversions
  - Creates a new blank presentation and copies slide content only
  - Strips all masters, layouts, footers, logos, and template elements
  - Resulting presentation has only blank layouts with default masters
  - Enables cleaner HTML output without any template/layout/footer overhead
  - Facilitates template swapping workflows - strip old templates, convert to HTML, then apply new templates
  - Significantly reduces file sizes by removing all template and branding data
  - Perfect for re-theming presentations or removing corporate branding
- Supports round-trip conversions (HTML → PPTX → HTML)

## LibreOffice Backend (Open-Source Alternative)

XLCR includes an optional LibreOffice-based conversion backend that serves as an open-source fallback when Aspose is not available.

### Features
- **Open Source**: No commercial license required
- **Automatic Fallback**: Activates when Aspose is unavailable (lower priority)
- **Headless Operation**: Runs LibreOffice in headless mode via JODConverter
- **Wide Format Support**: Handles Microsoft Office and OpenDocument formats

### Supported Conversions
The LibreOffice backend (`core-libreoffice` module) supports these conversions to PDF:
- **Word**: DOC, DOCX → PDF
- **Excel**: XLS, XLSX, XLSM → PDF
- **PowerPoint**: PPT, PPTX → PDF
- **OpenDocument**: ODS (Calc spreadsheets) → PDF

### Installation Requirements

LibreOffice must be installed on your system:

**macOS:**
```bash
brew install --cask libreoffice
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt-get install libreoffice
```

**Windows:**
Download from https://www.libreoffice.org/download/

### Configuration

By default, XLCR looks for LibreOffice in platform-specific locations:
- **macOS**: `/Applications/LibreOffice.app/Contents`
- **Linux**: `/usr/lib/libreoffice`
- **Windows**: `C:\Program Files\LibreOffice`

To specify a custom path, set the environment variable:
```bash
export LIBREOFFICE_HOME=/path/to/libreoffice
```

### Usage

The LibreOffice backend works automatically as a fallback. No code changes needed:

```bash
# These commands will use Aspose if available, LibreOffice otherwise
./mill core[3.3.4].run -i document.docx -o output.pdf
./mill core[3.3.4].run -i spreadsheet.xlsx -o output.pdf
./mill core[3.3.4].run -i presentation.pptx -o output.pdf
./mill core[3.3.4].run -i spreadsheet.ods -o output.pdf
```

### Priority System

XLCR uses a priority-based bridge selection:
- **HIGH Priority**: Aspose bridges (preferred when available)
- **DEFAULT Priority**: LibreOffice bridges (fallback)
- **LOW Priority**: Last-resort implementations

This ensures the best available converter is always used automatically.

### Performance Considerations

- **Aspose**: Pure Java, faster, no external dependencies, better quality
- **LibreOffice**: Requires external process, slower startup, but free and open-source
- **Recommendation**: Use Aspose for production, LibreOffice for development/testing

### Module Structure

The `core-libreoffice` module follows the same bridge pattern as other backends:
- `core-libreoffice/src/main/scala/bridges/` - Bridge implementations
- `core-libreoffice/src/main/scala/config/` - JODConverter configuration
- `core-libreoffice/src/main/scala/registration/` - SPI registration

### Building

To build the LibreOffice module:
```bash
./mill core-libreoffice[3.3.4].compile
./mill core-libreoffice[3.3.4].test
./mill core-libreoffice[3.3.4].assembly
```
