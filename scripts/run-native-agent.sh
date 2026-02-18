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

# 1. HTML → PDF (baseline — this already works in native image)
run_with_agent "HTML → PDF (baseline)" \
    convert -i /xlcr/testdata/test.html -o "$WORK_DIR/test.pdf"

# 2. HTML → PPTX (crashes in native image — Aspose.Slides)
run_with_agent "HTML → PPTX (Aspose.Slides)" \
    convert -i /xlcr/testdata/test.html -o "$WORK_DIR/test.pptx"

# 3. PDF → HTML (crashes in native image — Aspose.PDF)
if [ -f "$WORK_DIR/test.pdf" ]; then
    run_with_agent "PDF → HTML (Aspose.PDF)" \
        convert -i "$WORK_DIR/test.pdf" -o "$WORK_DIR/from-pdf.html"
else
    echo "SKIP: PDF → HTML (no test.pdf generated from step 1)"
fi

# 4. PDF → PPTX (crashes in native image — Aspose.PDF + Aspose.Slides)
if [ -f "$WORK_DIR/test.pdf" ]; then
    run_with_agent "PDF → PPTX (Aspose.PDF + Aspose.Slides)" \
        convert -i "$WORK_DIR/test.pdf" -o "$WORK_DIR/from-pdf.pptx"
else
    echo "SKIP: PDF → PPTX (no test.pdf generated from step 1)"
fi

# ── Splitting operations ────────────────────────────────────────────

# 5. Split PDF into pages (Aspose.PDF page splitting)
if [ -f "$WORK_DIR/test.pdf" ]; then
    mkdir -p "$WORK_DIR/split-pdf"
    run_with_agent "Split PDF pages (Aspose.PDF)" \
        split -i "$WORK_DIR/test.pdf" -o "$WORK_DIR/split-pdf" --extract
else
    echo "SKIP: Split PDF (no test.pdf)"
fi

# 6. Split PPTX into slides (Aspose.Slides slide splitting)
if [ -f "$WORK_DIR/test.pptx" ]; then
    mkdir -p "$WORK_DIR/split-pptx"
    run_with_agent "Split PPTX slides (Aspose.Slides)" \
        split -i "$WORK_DIR/test.pptx" -o "$WORK_DIR/split-pptx" --extract
else
    echo "SKIP: Split PPTX (no test.pptx)"
fi

# 7. Split XLSX into sheets (exercises Excel sheet splitting)
if [ -f "/xlcr/testdata/test.xlsx" ]; then
    mkdir -p "$WORK_DIR/split-xlsx"
    run_with_agent "Split XLSX sheets (POI/Aspose)" \
        split -i /xlcr/testdata/test.xlsx -o "$WORK_DIR/split-xlsx" --extract
else
    echo "SKIP: Split XLSX (no test.xlsx)"
fi

# ── Excel operations ────────────────────────────────────────────────

# 8. XLSX → PDF (Aspose.Cells)
if [ -f "/xlcr/testdata/test.xlsx" ]; then
    run_with_agent "XLSX → PDF (Aspose.Cells)" \
        convert -i /xlcr/testdata/test.xlsx -o "$WORK_DIR/from-xlsx.pdf"
else
    echo "SKIP: XLSX → PDF (no test.xlsx)"
fi

# 9. XLSX → ODS (POI + ODFDOM)
if [ -f "/xlcr/testdata/test.xlsx" ]; then
    run_with_agent "XLSX → ODS (POI + ODFDOM)" \
        convert -i /xlcr/testdata/test.xlsx -o "$WORK_DIR/from-xlsx.ods"
else
    echo "SKIP: XLSX → ODS (no test.xlsx)"
fi

# 10. XLSX → HTML (Aspose.Cells)
if [ -f "/xlcr/testdata/test.xlsx" ]; then
    run_with_agent "XLSX → HTML (Aspose.Cells)" \
        convert -i /xlcr/testdata/test.xlsx -o "$WORK_DIR/from-xlsx.html"
else
    echo "SKIP: XLSX → HTML (no test.xlsx)"
fi

# ── Info / metadata ─────────────────────────────────────────────────

# 11. Info on XLSX (exercises Tika metadata for Excel)
if [ -f "/xlcr/testdata/test.xlsx" ]; then
    run_with_agent "info XLSX (Tika metadata)" \
        info -i /xlcr/testdata/test.xlsx
fi

# 12. Info on HTML (exercises Tika metadata for HTML)
run_with_agent "info HTML (Tika metadata)" \
    info -i /xlcr/testdata/test.html

# 13. Backend info (exercises backend detection)
run_with_agent "backend-info" \
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
