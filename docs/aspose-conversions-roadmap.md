# Aspose Conversions Roadmap

## Current State (Tier 1 Complete)

XLCR implements **~80 Aspose-backed conversion routes** across 6 Aspose products. The Tier 1 expansion added 35 high-value conversions that required zero new MIME type definitions.

### Tier 1 Additions (Implemented)

| Category | Conversions | Product |
|----------|------------|---------|
| **PDF -> editable docs** | PDF -> DOCX, DOC, XLSX | Aspose.PDF |
| **Word -> web/text** | DOCX/DOC/DOCM -> HTML, Plain Text, Markdown | Aspose.Words |
| **Excel -> data** | XLSX/XLS/XLSM/XLSB/ODS -> CSV; XLSX -> JSON, Markdown | Aspose.Cells |
| **RTF support** | RTF -> PDF/DOCX/HTML; DOCX -> RTF | Aspose.Words |
| **Email -> HTML** | EML/MSG -> HTML | Aspose.Email + Words |
| **Email interchange** | EML <-> MSG | Aspose.Email |
| **Document thumbnails** | DOCX -> PNG/JPEG; PPTX/PPT -> PNG/JPEG | Aspose.Words/Slides |

---

## Tier 2: Planned (Not Yet Implemented)

These conversions use formats already defined in `Mime.scala` and add ~15 new routes.

### OpenDocument via Aspose (Words + Slides)

Currently only reachable via LibreOffice fallback. Adding Aspose routes provides better quality and removes the LibreOffice dependency requirement.

| From | To | Product | Notes |
|------|-----|---------|-------|
| ODT | PDF | Words | Open standard word processing |
| ODT | DOCX | Words | OpenDocument to Office conversion |
| DOCX | ODT | Words | Office to OpenDocument |
| ODP | PPTX | Slides | OpenDocument to Office presentation |
| PPTX | ODP | Slides | Office to OpenDocument presentation |

### CSV Import (Cells)

| From | To | Product | Notes |
|------|-----|---------|-------|
| CSV | XLSX | Cells | CSV formatting and structuring |
| CSV | PDF | Cells | CSV print-ready output |

### Vector/Archival Image Output

| From | To | Product | Notes |
|------|-----|---------|-------|
| XLSX | SVG | Cells | Chart/visual vector export |
| PDF | SVG | PDF | Per-page vector output |
| PDF | TIFF | PDF | Multi-page archival imaging (TiffDevice) |
| PDF | BMP | PDF | Bitmap rendering (BmpDevice) |

### Multi-Page PDF to Images

Enhance existing PDF -> PNG/JPEG to render all pages instead of only the first. Could be implemented as:
- A new `allPages` option in `ConvertOptions` that returns a ZIP of images
- Or as a splitter variant that outputs image fragments

---

## Tier 3: Future (Requires New MIME Types)

These need new opaque type definitions added to `Mime.scala`.

### EPUB Support (Words + PDF)

E-book publishing format. Would need:
- `opaque type Epub <: Mime` with value `"application/epub+zip"`
- Extension mapping: `"epub"` -> `Mime.epub`

| From | To | Product |
|------|-----|---------|
| DOCX | EPUB | Words |
| PDF | EPUB | PDF |

### PPSX (PowerPoint Show)

Self-playing presentation format. Would need:
- `opaque type Ppsx <: Mime` with value `"application/vnd.openxmlformats-officedocument.presentationml.slideshow"`
- Extension mapping: `"ppsx"` -> `Mime.ppsx`

| From | To | Product |
|------|-----|---------|
| PPTX | PPSX | Slides |

---

## Aspose API Capabilities Not Planned

These are available in the Aspose libraries but not prioritized:

| Format | Library | Why Deferred |
|--------|---------|-------------|
| XPS | Words, PDF, Slides | Niche Microsoft format, low demand |
| PostScript (PS/EPS) | Words, PDF | Largely replaced by PDF |
| MOBI/AZW3 | Words | Kindle-specific, EPUB is preferred |
| PCL | Words | Printer control language, niche |
| EMF | Cells, PDF | Windows-specific metafile |
| MHTML | Words, Email | Web archive format, limited use |
| XLAM/XLTX | Cells | Template/add-in formats |
| POTX/POTM | Slides | Template formats |
