#!/usr/bin/env bash
# run-native-agent.sh
# Runs inside Docker to exercise all XLCR conversion paths with the
# GraalVM tracing agent, accumulating metadata via config-merge-dir.
#
# The tracing agent records all reflection, JNI, proxy, resource, and
# serialization usage — exactly what native-image needs.

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

# ── Agent helper ────────────────────────────────────────────────────
# Runs the CLI with the tracing agent. Uses config-merge-dir so each
# invocation accumulates into the same metadata directory.
run_with_agent() {
    local description="$1"
    shift

    echo ""
    echo "════════════════════════════════════════════════════════════"
    echo "  $description"
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

# ── HTML conversions (Aspose.PDF + Aspose.Slides) ───────────────────

# 1. HTML → PDF (baseline — this already works in native image)
run_with_agent "1. HTML → PDF (Aspose.PDF)" \
    convert -i /xlcr/testdata/test.html -o "$WORK_DIR/test.pdf"

# 2. HTML → PPTX (Aspose.Slides)
run_with_agent "2. HTML → PPTX (Aspose.Slides)" \
    convert -i /xlcr/testdata/test.html -o "$WORK_DIR/test.pptx"

# ── PDF conversions (Aspose.PDF + Aspose.Slides) ───────────────────

# 3. PDF → HTML (Aspose.PDF HtmlSaveOptions)
if [ -f "$WORK_DIR/test.pdf" ]; then
    run_with_agent "3. PDF → HTML (Aspose.PDF)" \
        convert -i "$WORK_DIR/test.pdf" -o "$WORK_DIR/from-pdf.html"
else
    echo "SKIP: PDF → HTML (no test.pdf generated from step 1)"
fi

# 4. PDF → PPTX (Aspose.PDF + Aspose.Slides)
if [ -f "$WORK_DIR/test.pdf" ]; then
    run_with_agent "4. PDF → PPTX (Aspose.PDF + Aspose.Slides)" \
        convert -i "$WORK_DIR/test.pdf" -o "$WORK_DIR/from-pdf.pptx"
else
    echo "SKIP: PDF → PPTX (no test.pdf generated from step 1)"
fi

# 5. PDF → PNG (Aspose.PDF image rendering — also generates test.png for step 6)
if [ -f "$WORK_DIR/test.pdf" ]; then
    run_with_agent "5. PDF → PNG (Aspose.PDF)" \
        convert -i "$WORK_DIR/test.pdf" -o "$WORK_DIR/test.png"
else
    echo "SKIP: PDF → PNG (no test.pdf)"
fi

# 6. PNG → PDF (Aspose.PDF image import — uses PNG generated in step 5)
if [ -f "$WORK_DIR/test.png" ]; then
    run_with_agent "6. PNG → PDF (Aspose.PDF)" \
        convert -i "$WORK_DIR/test.png" -o "$WORK_DIR/from-png.pdf"
else
    echo "SKIP: PNG → PDF (no test.png from step 5)"
fi

# ── PowerPoint conversions (Aspose.Slides) ──────────────────────────

# 7. PPTX → PDF (Aspose.Slides)
if [ -f "$WORK_DIR/test.pptx" ]; then
    run_with_agent "7. PPTX → PDF (Aspose.Slides)" \
        convert -i "$WORK_DIR/test.pptx" -o "$WORK_DIR/from-pptx.pdf"
else
    echo "SKIP: PPTX → PDF (no test.pptx)"
fi

# 8. PPTX → HTML (Aspose.Slides)
if [ -f "$WORK_DIR/test.pptx" ]; then
    run_with_agent "8. PPTX → HTML (Aspose.Slides)" \
        convert -i "$WORK_DIR/test.pptx" -o "$WORK_DIR/from-pptx.html"
else
    echo "SKIP: PPTX → HTML (no test.pptx)"
fi

# ── Word conversions (Aspose.Words) ─────────────────────────────────

# 9. DOCX → PDF (Aspose.Words)
if [ -f "/xlcr/testdata/test.docx" ]; then
    run_with_agent "9. DOCX → PDF (Aspose.Words)" \
        convert -i /xlcr/testdata/test.docx -o "$WORK_DIR/from-docx.pdf"
else
    echo "SKIP: DOCX → PDF (no test.docx)"
fi

# ── Excel conversions (Aspose.Cells) ────────────────────────────────

# 10. XLSX → PDF (Aspose.Cells)
if [ -f "/xlcr/testdata/test.xlsx" ]; then
    run_with_agent "10. XLSX → PDF (Aspose.Cells)" \
        convert -i /xlcr/testdata/test.xlsx -o "$WORK_DIR/from-xlsx.pdf"
else
    echo "SKIP: XLSX → PDF (no test.xlsx)"
fi

# 11. XLSX → ODS (POI + ODFDOM)
if [ -f "/xlcr/testdata/test.xlsx" ]; then
    run_with_agent "11. XLSX → ODS (POI + ODFDOM)" \
        convert -i /xlcr/testdata/test.xlsx -o "$WORK_DIR/from-xlsx.ods"
else
    echo "SKIP: XLSX → ODS (no test.xlsx)"
fi

# 12. XLSX → HTML (Aspose.Cells)
if [ -f "/xlcr/testdata/test.xlsx" ]; then
    run_with_agent "12. XLSX → HTML (Aspose.Cells)" \
        convert -i /xlcr/testdata/test.xlsx -o "$WORK_DIR/from-xlsx.html"
else
    echo "SKIP: XLSX → HTML (no test.xlsx)"
fi

# ── Splitting operations ────────────────────────────────────────────

# 13. Split PDF into pages (Aspose.PDF page splitting)
if [ -f "$WORK_DIR/test.pdf" ]; then
    mkdir -p "$WORK_DIR/split-pdf"
    run_with_agent "13. Split PDF pages (Aspose.PDF)" \
        split -i "$WORK_DIR/test.pdf" -d "$WORK_DIR/split-pdf" --extract
else
    echo "SKIP: Split PDF (no test.pdf)"
fi

# 14. Split PPTX into slides (Aspose.Slides slide splitting)
if [ -f "$WORK_DIR/test.pptx" ]; then
    mkdir -p "$WORK_DIR/split-pptx"
    run_with_agent "14. Split PPTX slides (Aspose.Slides)" \
        split -i "$WORK_DIR/test.pptx" -d "$WORK_DIR/split-pptx" --extract
else
    echo "SKIP: Split PPTX (no test.pptx)"
fi

# 15. Split XLSX into sheets (Aspose.Cells/POI)
if [ -f "/xlcr/testdata/test.xlsx" ]; then
    mkdir -p "$WORK_DIR/split-xlsx"
    run_with_agent "15. Split XLSX sheets (Aspose.Cells/POI)" \
        split -i /xlcr/testdata/test.xlsx -d "$WORK_DIR/split-xlsx" --extract
else
    echo "SKIP: Split XLSX (no test.xlsx)"
fi

# 16. Split DOCX into pages (Aspose.Words)
if [ -f "/xlcr/testdata/test.docx" ]; then
    mkdir -p "$WORK_DIR/split-docx"
    run_with_agent "16. Split DOCX pages (Aspose.Words)" \
        split -i /xlcr/testdata/test.docx -d "$WORK_DIR/split-docx" --extract
else
    echo "SKIP: Split DOCX (no test.docx)"
fi

# ── Info / metadata ─────────────────────────────────────────────────

# 17. Info on XLSX (exercises Tika metadata for Excel)
if [ -f "/xlcr/testdata/test.xlsx" ]; then
    run_with_agent "17. info XLSX (Tika metadata)" \
        info -i /xlcr/testdata/test.xlsx
fi

# 18. Info on HTML (exercises Tika metadata for HTML)
run_with_agent "18. info HTML (Tika metadata)" \
    info -i /xlcr/testdata/test.html

# 19. Info on DOCX (exercises Tika metadata for Word)
if [ -f "/xlcr/testdata/test.docx" ]; then
    run_with_agent "19. info DOCX (Tika metadata)" \
        info -i /xlcr/testdata/test.docx
fi

# 20. Backend info (exercises backend detection)
run_with_agent "20. backend-info" \
    --backend-info

# ── Summary ─────────────────────────────────────────────────────────

echo ""
echo "════════════════════════════════════════════════════════════"
echo "  Tracing complete"
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
