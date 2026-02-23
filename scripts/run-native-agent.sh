#!/usr/bin/env bash
# run-native-agent.sh
# Runs inside Docker to exercise ALL XLCR conversion paths with the
# GraalVM tracing agent, accumulating metadata via config-merge-dir.
#
# The tracing agent records all reflection, JNI, proxy, resource, and
# serialization usage — exactly what native-image needs.
#
# Coverage strategy:
#   Phase 1: Aspose conversions (highest priority backend)
#   Phase 2: Aspose splits
#   Phase 3: LibreOffice conversions (--backend libreoffice)
#   Phase 4: Core-only conversions + splits (--backend xlcr, POI/PDFBox/Tika)
#   Phase 5: Info / metadata for all MIME types (Tika parsers)
#   Phase 6: Backend info

set -euo pipefail

METADATA_DIR="/metadata-output"
WORK_DIR="/tmp/xlcr-agent"
mkdir -p "$WORK_DIR"

# ── Find GraalVM java binary ────────────────────────────────────────
# Mill downloads GraalVM via coursier to ~/.cache/coursier/ (not out/).
GRAALVM_JAVA=$(find /root/.cache/coursier -path '*/graalvm*/bin/java' -type f 2>/dev/null | head -1)

# Fallback: search /xlcr/out/ in case Mill layout changes
if [ -z "$GRAALVM_JAVA" ]; then
    GRAALVM_JAVA=$(find /xlcr/out -path '*/graalvm*/bin/java' -type f 2>/dev/null | head -1)
fi

if [ -z "$GRAALVM_JAVA" ]; then
    echo "ERROR: Could not find GraalVM java binary"
    echo "Searched: /root/.cache/coursier/ and /xlcr/out/"
    echo "Ensure 'mill xlcr[3.3.4].nativeImageTool' was run during build."
    exit 1
fi

echo "Found GraalVM java: $GRAALVM_JAVA"

# ── Find assembly JAR ───────────────────────────────────────────────
ASSEMBLY_JAR=$(find /xlcr/out -path '*/assembly.dest/out.jar' -type f 2>/dev/null | head -1)

if [ -z "$ASSEMBLY_JAR" ]; then
    echo "ERROR: Could not find assembly JAR in /xlcr/out/"
    exit 1
fi

echo "Found assembly JAR: $ASSEMBLY_JAR"

# ── Agent helpers ─────────────────────────────────────────────────
# Test counter
TEST_NUM=0

# Runs the CLI with the tracing agent. Uses config-merge-dir so each
# invocation accumulates into the same metadata directory.
run_with_agent() {
    local description="$1"
    shift
    TEST_NUM=$((TEST_NUM + 1))

    echo ""
    echo "════════════════════════════════════════════════════════════"
    echo "  ${TEST_NUM}. $description"
    echo "════════════════════════════════════════════════════════════"

    # Run with tracing agent; allow failures (some paths may crash,
    # but the agent still captures metadata up to the crash point)
    "$GRAALVM_JAVA" \
        "-agentlib:native-image-agent=config-merge-dir=${METADATA_DIR}" \
        -Dsun.misc.unsafe.memory.access=allow \
        --add-opens=java.base/sun.misc=ALL-UNNAMED \
        --add-opens=java.base/java.lang=ALL-UNNAMED \
        -Djava.awt.headless=true \
        -jar "$ASSEMBLY_JAR" \
        "$@" \
    && echo "  ✓ Success" \
    || echo "  ✗ Failed (metadata still captured up to failure point)"
}

# ── Run conversions ─────────────────────────────────────────────────

echo ""
echo "Starting GraalVM tracing agent runs..."
echo "Metadata output: $METADATA_DIR"
echo ""

# ══════════════════════════════════════════════════════════════════════
# Phase 1: Aspose conversions (highest priority backend)
# ══════════════════════════════════════════════════════════════════════

echo ""
echo "▶ Phase 1: Aspose conversions"
echo ""

# HTML → PDF (Aspose.PDF)
run_with_agent "HTML → PDF (Aspose.PDF)" \
    convert -i /xlcr/testdata/test.html -o "$WORK_DIR/test.pdf"

# HTML → PPTX (Aspose.Slides)
run_with_agent "HTML → PPTX (Aspose.Slides)" \
    convert -i /xlcr/testdata/test.html -o "$WORK_DIR/test.pptx"

# PDF → HTML (Aspose.PDF HtmlSaveOptions)
if [ -f "$WORK_DIR/test.pdf" ]; then
    run_with_agent "PDF → HTML (Aspose.PDF)" \
        convert -i "$WORK_DIR/test.pdf" -o "$WORK_DIR/from-pdf.html"
fi

# PDF → PPTX (Aspose.PDF + Aspose.Slides)
if [ -f "$WORK_DIR/test.pdf" ]; then
    run_with_agent "PDF → PPTX (Aspose.PDF + Aspose.Slides)" \
        convert -i "$WORK_DIR/test.pdf" -o "$WORK_DIR/from-pdf.pptx"
fi

# PDF → PNG (Aspose.PDF image rendering)
if [ -f "$WORK_DIR/test.pdf" ]; then
    run_with_agent "PDF → PNG (Aspose.PDF)" \
        convert -i "$WORK_DIR/test.pdf" -o "$WORK_DIR/test.png"
fi

# PNG → PDF (Aspose.PDF image import)
if [ -f "$WORK_DIR/test.png" ]; then
    run_with_agent "PNG → PDF (Aspose.PDF)" \
        convert -i "$WORK_DIR/test.png" -o "$WORK_DIR/from-png.pdf"
fi

# JPEG → PDF (Aspose.PDF image import — different codec path)
if [ -f "/xlcr/testdata/test.jpg" ]; then
    run_with_agent "JPEG → PDF (Aspose.PDF)" \
        convert -i /xlcr/testdata/test.jpg -o "$WORK_DIR/from-jpg.pdf"
fi

# PPTX → PDF (Aspose.Slides)
if [ -f "$WORK_DIR/test.pptx" ]; then
    run_with_agent "PPTX → PDF (Aspose.Slides)" \
        convert -i "$WORK_DIR/test.pptx" -o "$WORK_DIR/from-pptx.pdf"
fi

# PPTX → HTML (Aspose.Slides)
if [ -f "$WORK_DIR/test.pptx" ]; then
    run_with_agent "PPTX → HTML (Aspose.Slides)" \
        convert -i "$WORK_DIR/test.pptx" -o "$WORK_DIR/from-pptx.html"
fi

# DOCX → PDF (Aspose.Words)
if [ -f "/xlcr/testdata/test.docx" ]; then
    run_with_agent "DOCX → PDF (Aspose.Words)" \
        convert -i /xlcr/testdata/test.docx -o "$WORK_DIR/from-docx.pdf"
fi

# XLSX → PDF (Aspose.Cells)
if [ -f "/xlcr/testdata/test.xlsx" ]; then
    run_with_agent "XLSX → PDF (Aspose.Cells)" \
        convert -i /xlcr/testdata/test.xlsx -o "$WORK_DIR/from-xlsx.pdf"
fi

# XLSX → ODS (POI + ODFDOM)
if [ -f "/xlcr/testdata/test.xlsx" ]; then
    run_with_agent "XLSX → ODS (POI + ODFDOM)" \
        convert -i /xlcr/testdata/test.xlsx -o "$WORK_DIR/from-xlsx.ods"
fi

# XLSX → HTML (Aspose.Cells)
if [ -f "/xlcr/testdata/test.xlsx" ]; then
    run_with_agent "XLSX → HTML (Aspose.Cells)" \
        convert -i /xlcr/testdata/test.xlsx -o "$WORK_DIR/from-xlsx.html"
fi

# ══════════════════════════════════════════════════════════════════════
# Phase 2: Aspose splits
# ══════════════════════════════════════════════════════════════════════

echo ""
echo "▶ Phase 2: Aspose splits"
echo ""

# Split PDF into pages (Aspose.PDF)
if [ -f "$WORK_DIR/test.pdf" ]; then
    mkdir -p "$WORK_DIR/split-pdf"
    run_with_agent "Split PDF pages (Aspose.PDF)" \
        split -i "$WORK_DIR/test.pdf" -d "$WORK_DIR/split-pdf" --extract
fi

# Split PPTX into slides (Aspose.Slides)
if [ -f "$WORK_DIR/test.pptx" ]; then
    mkdir -p "$WORK_DIR/split-pptx"
    run_with_agent "Split PPTX slides (Aspose.Slides)" \
        split -i "$WORK_DIR/test.pptx" -d "$WORK_DIR/split-pptx" --extract
fi

# Split XLSX into sheets (Aspose.Cells)
if [ -f "/xlcr/testdata/test.xlsx" ]; then
    mkdir -p "$WORK_DIR/split-xlsx"
    run_with_agent "Split XLSX sheets (Aspose.Cells)" \
        split -i /xlcr/testdata/test.xlsx -d "$WORK_DIR/split-xlsx" --extract
fi

# Split DOCX into sections (Aspose.Words)
if [ -f "/xlcr/testdata/test.docx" ]; then
    mkdir -p "$WORK_DIR/split-docx"
    run_with_agent "Split DOCX sections (Aspose.Words)" \
        split -i /xlcr/testdata/test.docx -d "$WORK_DIR/split-docx" --extract
fi

# ══════════════════════════════════════════════════════════════════════
# Phase 3: LibreOffice conversions (--backend libreoffice)
# ══════════════════════════════════════════════════════════════════════

echo ""
echo "▶ Phase 3: LibreOffice conversions"
echo ""

# DOCX → PDF (LibreOffice / JODConverter)
if [ -f "/xlcr/testdata/test.docx" ]; then
    run_with_agent "DOCX → PDF (LibreOffice)" \
        convert -i /xlcr/testdata/test.docx -o "$WORK_DIR/lo-docx.pdf" --backend libreoffice
fi

# XLSX → PDF (LibreOffice)
if [ -f "/xlcr/testdata/test.xlsx" ]; then
    run_with_agent "XLSX → PDF (LibreOffice)" \
        convert -i /xlcr/testdata/test.xlsx -o "$WORK_DIR/lo-xlsx.pdf" --backend libreoffice
fi

# PPTX → PDF (LibreOffice)
if [ -f "$WORK_DIR/test.pptx" ]; then
    run_with_agent "PPTX → PDF (LibreOffice)" \
        convert -i "$WORK_DIR/test.pptx" -o "$WORK_DIR/lo-pptx.pdf" --backend libreoffice
fi

# ODS → PDF (LibreOffice)
if [ -f "$WORK_DIR/from-xlsx.ods" ]; then
    run_with_agent "ODS → PDF (LibreOffice)" \
        convert -i "$WORK_DIR/from-xlsx.ods" -o "$WORK_DIR/lo-ods.pdf" --backend libreoffice
fi

# DOCX → DOC (LibreOffice format conversion — generates legacy DOC for later tests)
if [ -f "/xlcr/testdata/test.docx" ]; then
    run_with_agent "DOCX → DOC (LibreOffice)" \
        convert -i /xlcr/testdata/test.docx -o "$WORK_DIR/test.doc" --backend libreoffice
fi

# XLSX → XLS (LibreOffice format conversion — generates legacy XLS)
if [ -f "/xlcr/testdata/test.xlsx" ]; then
    run_with_agent "XLSX → XLS (LibreOffice)" \
        convert -i /xlcr/testdata/test.xlsx -o "$WORK_DIR/test.xls" --backend libreoffice
fi

# PPTX → PPT (LibreOffice format conversion — generates legacy PPT)
if [ -f "$WORK_DIR/test.pptx" ]; then
    run_with_agent "PPTX → PPT (LibreOffice)" \
        convert -i "$WORK_DIR/test.pptx" -o "$WORK_DIR/test.ppt" --backend libreoffice
fi

# DOC → PDF (LibreOffice — legacy format)
if [ -f "$WORK_DIR/test.doc" ]; then
    run_with_agent "DOC → PDF (LibreOffice)" \
        convert -i "$WORK_DIR/test.doc" -o "$WORK_DIR/lo-doc.pdf" --backend libreoffice
fi

# XLS → PDF (LibreOffice — legacy format)
if [ -f "$WORK_DIR/test.xls" ]; then
    run_with_agent "XLS → PDF (LibreOffice)" \
        convert -i "$WORK_DIR/test.xls" -o "$WORK_DIR/lo-xls.pdf" --backend libreoffice
fi

# PPT → PDF (LibreOffice — legacy format)
if [ -f "$WORK_DIR/test.ppt" ]; then
    run_with_agent "PPT → PDF (LibreOffice)" \
        convert -i "$WORK_DIR/test.ppt" -o "$WORK_DIR/lo-ppt.pdf" --backend libreoffice
fi

# ══════════════════════════════════════════════════════════════════════
# Phase 4: Core-only conversions + splits (--backend xlcr)
# ══════════════════════════════════════════════════════════════════════

echo ""
echo "▶ Phase 4: Core-only conversions + splits (POI / PDFBox / Tika)"
echo ""

# PDF → text/plain (Tika text extraction)
if [ -f "$WORK_DIR/test.pdf" ]; then
    run_with_agent "PDF → TXT (Tika text extraction)" \
        convert -i "$WORK_DIR/test.pdf" -o "$WORK_DIR/core-pdf.txt" --backend xlcr
fi

# DOCX → text/plain (Tika text extraction)
if [ -f "/xlcr/testdata/test.docx" ]; then
    run_with_agent "DOCX → TXT (Tika text extraction)" \
        convert -i /xlcr/testdata/test.docx -o "$WORK_DIR/core-docx.txt" --backend xlcr
fi

# XLSX → text/plain (Tika text extraction)
if [ -f "/xlcr/testdata/test.xlsx" ]; then
    run_with_agent "XLSX → TXT (Tika text extraction)" \
        convert -i /xlcr/testdata/test.xlsx -o "$WORK_DIR/core-xlsx.txt" --backend xlcr
fi

# PDF → XML (Tika XML extraction)
if [ -f "$WORK_DIR/test.pdf" ]; then
    run_with_agent "PDF → XML (Tika XML extraction)" \
        convert -i "$WORK_DIR/test.pdf" -o "$WORK_DIR/core-pdf.xml" --backend xlcr
fi

# XLSX → ODS (POI + ODFDOM — core only)
if [ -f "/xlcr/testdata/test.xlsx" ]; then
    run_with_agent "XLSX → ODS (Core: POI + ODFDOM)" \
        convert -i /xlcr/testdata/test.xlsx -o "$WORK_DIR/core-xlsx.ods" --backend xlcr
fi

# Split XLSX sheets (POI splitter)
if [ -f "/xlcr/testdata/test.xlsx" ]; then
    mkdir -p "$WORK_DIR/core-split-xlsx"
    run_with_agent "Split XLSX sheets (Core: POI)" \
        split -i /xlcr/testdata/test.xlsx -d "$WORK_DIR/core-split-xlsx" --extract --backend xlcr
fi

# Split PPTX slides (POI splitter)
if [ -f "$WORK_DIR/test.pptx" ]; then
    mkdir -p "$WORK_DIR/core-split-pptx"
    run_with_agent "Split PPTX slides (Core: POI)" \
        split -i "$WORK_DIR/test.pptx" -d "$WORK_DIR/core-split-pptx" --extract --backend xlcr
fi

# Split DOCX sections (POI splitter)
if [ -f "/xlcr/testdata/test.docx" ]; then
    mkdir -p "$WORK_DIR/core-split-docx"
    run_with_agent "Split DOCX sections (Core: POI)" \
        split -i /xlcr/testdata/test.docx -d "$WORK_DIR/core-split-docx" --extract --backend xlcr
fi

# Split PDF pages (PDFBox splitter)
if [ -f "$WORK_DIR/test.pdf" ]; then
    mkdir -p "$WORK_DIR/core-split-pdf"
    run_with_agent "Split PDF pages (Core: PDFBox)" \
        split -i "$WORK_DIR/test.pdf" -d "$WORK_DIR/core-split-pdf" --extract --backend xlcr
fi

# Split EML attachments (Jakarta Mail)
if [ -f "/xlcr/testdata/test.eml" ]; then
    mkdir -p "$WORK_DIR/core-split-eml"
    run_with_agent "Split EML attachments (Core: Jakarta Mail)" \
        split -i /xlcr/testdata/test.eml -d "$WORK_DIR/core-split-eml" --extract --backend xlcr
fi

# Split ZIP entries (java.util.zip)
if [ -f "/xlcr/testdata/test.zip" ]; then
    mkdir -p "$WORK_DIR/core-split-zip"
    run_with_agent "Split ZIP entries (Core: java.util.zip)" \
        split -i /xlcr/testdata/test.zip -d "$WORK_DIR/core-split-zip" --extract --backend xlcr
fi

# Split CSV rows
if [ -f "/xlcr/testdata/test.csv" ]; then
    mkdir -p "$WORK_DIR/core-split-csv"
    run_with_agent "Split CSV rows (Core)" \
        split -i /xlcr/testdata/test.csv -d "$WORK_DIR/core-split-csv" --extract --backend xlcr
fi

# Split XLS sheets (POI — legacy format)
if [ -f "$WORK_DIR/test.xls" ]; then
    mkdir -p "$WORK_DIR/core-split-xls"
    run_with_agent "Split XLS sheets (Core: POI legacy)" \
        split -i "$WORK_DIR/test.xls" -d "$WORK_DIR/core-split-xls" --extract --backend xlcr
fi

# ══════════════════════════════════════════════════════════════════════
# Phase 5: Info / metadata for all MIME types (Tika parsers)
# ══════════════════════════════════════════════════════════════════════

echo ""
echo "▶ Phase 5: Info / metadata (Tika parsers for all MIME types)"
echo ""

# Office formats
if [ -f "/xlcr/testdata/test.xlsx" ]; then
    run_with_agent "info XLSX (Tika metadata)" \
        info -i /xlcr/testdata/test.xlsx
fi

run_with_agent "info HTML (Tika metadata)" \
    info -i /xlcr/testdata/test.html

if [ -f "/xlcr/testdata/test.docx" ]; then
    run_with_agent "info DOCX (Tika metadata)" \
        info -i /xlcr/testdata/test.docx
fi

if [ -f "/xlcr/testdata/test.pdf" ]; then
    run_with_agent "info PDF (Tika metadata / PDFBox)" \
        info -i /xlcr/testdata/test.pdf
fi

if [ -f "$WORK_DIR/test.pptx" ]; then
    run_with_agent "info PPTX (Tika OOXML Slides parser)" \
        info -i "$WORK_DIR/test.pptx"
fi

if [ -f "$WORK_DIR/from-xlsx.ods" ]; then
    run_with_agent "info ODS (Tika OpenDocument parser)" \
        info -i "$WORK_DIR/from-xlsx.ods"
fi

# Legacy Office formats
if [ -f "$WORK_DIR/test.doc" ]; then
    run_with_agent "info DOC (Tika legacy Word parser)" \
        info -i "$WORK_DIR/test.doc"
fi

if [ -f "$WORK_DIR/test.xls" ]; then
    run_with_agent "info XLS (Tika legacy Excel parser)" \
        info -i "$WORK_DIR/test.xls"
fi

if [ -f "$WORK_DIR/test.ppt" ]; then
    run_with_agent "info PPT (Tika legacy PowerPoint parser)" \
        info -i "$WORK_DIR/test.ppt"
fi

# Email formats
if [ -f "/xlcr/testdata/test.eml" ]; then
    run_with_agent "info EML (Tika RFC822 parser)" \
        info -i /xlcr/testdata/test.eml
fi

# Data formats
if [ -f "/xlcr/testdata/test.csv" ]; then
    run_with_agent "info CSV (Tika CSV parser)" \
        info -i /xlcr/testdata/test.csv
fi

# Archive formats
if [ -f "/xlcr/testdata/test.zip" ]; then
    run_with_agent "info ZIP (Tika archive parser)" \
        info -i /xlcr/testdata/test.zip
fi

# Image formats (exercises Tika image parsers + exiftool EXIF extraction)
if [ -f "/xlcr/testdata/test.jpg" ]; then
    run_with_agent "info JPEG (Tika image + EXIF)" \
        info -i /xlcr/testdata/test.jpg
fi

if [ -f "$WORK_DIR/test.png" ]; then
    run_with_agent "info PNG (Tika image parser)" \
        info -i "$WORK_DIR/test.png"
fi

if [ -f "/xlcr/testdata/test.tiff" ]; then
    run_with_agent "info TIFF (Tika image parser)" \
        info -i /xlcr/testdata/test.tiff
fi

# ══════════════════════════════════════════════════════════════════════
# Phase 6: Backend info
# ══════════════════════════════════════════════════════════════════════

echo ""
echo "▶ Phase 6: Backend info"
echo ""

run_with_agent "backend-info" \
    --backend-info

# ── Summary ─────────────────────────────────────────────────────────

echo ""
echo "════════════════════════════════════════════════════════════"
echo "  Tracing complete — $TEST_NUM test cases executed"
echo "════════════════════════════════════════════════════════════"
echo ""
echo "Metadata files in $METADATA_DIR:"
ls -la "$METADATA_DIR/"
echo ""

if [ -f "$METADATA_DIR/reachability-metadata.json" ]; then
    ENTRY_COUNT=$(grep -c '"type"' "$METADATA_DIR/reachability-metadata.json" 2>/dev/null || echo "0")
    echo "Reflection entries: $ENTRY_COUNT"
fi

echo "Done. Extract with: docker cp <container>:$METADATA_DIR/ ./metadata-output/"
