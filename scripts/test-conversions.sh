#!/usr/bin/env bash
# test-conversions.sh
# Unified test, benchmark, and tracing agent script for XLCR native image workflow.
#
# Validates file output (size > 0, correct MIME type via `file`), never exit codes.
# Tests all three backends: Aspose, LibreOffice, and Core (POI/PDFBox/Tika).
#
# Modes:
#   --mode test   Pass/fail table (default)
#   --mode bench  Timing comparison table
#   --mode both   Both test and benchmark
#   --mode agent  GraalVM tracing agent (captures native-image metadata)
#
# Runners:
#   --runner jvm     JVM only
#   --runner native  Native only
#   --runner both    Both (default)
#
# Usage:
#   ./scripts/test-conversions.sh --mode test
#   ./scripts/test-conversions.sh --mode test --runner native
#   ./scripts/test-conversions.sh --mode bench --runner native
#   ./scripts/test-conversions.sh --mode agent  # GraalVM tracing (JVM only)

set -uo pipefail

# ── Configuration ─────────────────────────────────────────────────────
MODE="test"
RUNNER="both"
WORK_DIR="/tmp/xlcr-test"
TESTDATA="/xlcr/testdata"
NATIVE_BIN="/xlcr/xlcr-native"
METADATA_DIR="${METADATA_DIR:-}"  # Set via env for agent mode

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --mode)   MODE="$2"; shift 2 ;;
        --runner) RUNNER="$2"; shift 2 ;;
        *)        echo "Unknown argument: $1"; exit 1 ;;
    esac
done

# ── Agent mode setup ─────────────────────────────────────────────────
if [[ "$MODE" == "agent" ]]; then
    RUNNER="jvm"  # Agent mode is always JVM-only
    METADATA_DIR="${METADATA_DIR:-/metadata-output}"
    mkdir -p "$METADATA_DIR"
fi

# ── Find JVM components ──────────────────────────────────────────────
JAVA_CMD="java"

if [[ "$MODE" == "agent" ]]; then
    # Agent mode needs GraalVM java for the -agentlib: flag
    JAVA_CMD=$(find /root/.cache/coursier -path '*/graalvm*/bin/java' -type f 2>/dev/null | head -1)
    if [[ -z "$JAVA_CMD" ]]; then
        JAVA_CMD=$(find /xlcr/out -path '*/graalvm*/bin/java' -type f 2>/dev/null | head -1)
    fi
    if [[ -z "$JAVA_CMD" ]]; then
        echo "ERROR: Could not find GraalVM java binary for tracing agent"
        echo "Searched: /root/.cache/coursier/ and /xlcr/out/"
        echo "Ensure 'mill xlcr[3.3.4].nativeImageTool' was run during build."
        exit 1
    fi
    echo "Found GraalVM java: $JAVA_CMD"
fi

ASSEMBLY_JAR=$(find /xlcr/out -path '*/assembly.dest/out.jar' -type f 2>/dev/null | head -1)

if [[ -z "$ASSEMBLY_JAR" ]] && [[ "$RUNNER" != "native" ]]; then
    echo "ERROR: Assembly JAR not found in /xlcr/out/"
    echo "Build with: mill 'xlcr[3.3.4].assembly'"
    exit 1
fi

if [[ "$MODE" != "agent" ]] && [[ ! -f "$NATIVE_BIN" ]] && [[ "$RUNNER" != "jvm" ]]; then
    echo "WARNING: Native binary not found at $NATIVE_BIN"
    if [[ "$RUNNER" == "native" ]]; then
        echo "ERROR: --runner native but no native binary found"
        exit 1
    fi
    RUNNER="jvm"
    echo "Falling back to --runner jvm"
fi

# ── Results tracking ─────────────────────────────────────────────────
declare -a TEST_NAMES=()
declare -a JVM_RESULTS=()
declare -a NATIVE_RESULTS=()
declare -a JVM_TIMES=()
declare -a NATIVE_TIMES=()

# ── Validation functions ─────────────────────────────────────────────

validate_pdf() {
    local f="$1"
    [[ -f "$f" ]] && [[ -s "$f" ]] && file "$f" | grep -qi "PDF document"
}

validate_zip() {
    # PPTX, XLSX, DOCX are ZIP-based Office formats
    local f="$1"
    [[ -f "$f" ]] && [[ -s "$f" ]] && file "$f" | grep -qiE "Zip archive|Microsoft OOXML|Microsoft (Excel|Word|PowerPoint)"
}

validate_html() {
    local f="$1"
    [[ -f "$f" ]] && [[ -s "$f" ]] && grep -qi "<html" "$f"
}

validate_png() {
    local f="$1"
    [[ -f "$f" ]] && [[ -s "$f" ]] && file "$f" | grep -qi "PNG image"
}

validate_jpeg() {
    local f="$1"
    [[ -f "$f" ]] && [[ -s "$f" ]] && file "$f" | grep -qi "JPEG image"
}

validate_ods() {
    local f="$1"
    [[ -f "$f" ]] && [[ -s "$f" ]] && file "$f" | grep -qiE "Zip archive|OpenDocument"
}

validate_text() {
    local f="$1"
    [[ -f "$f" ]] && [[ -s "$f" ]]
}

validate_xml() {
    local f="$1"
    [[ -f "$f" ]] && [[ -s "$f" ]] && grep -qiE "<?xml|<html" "$f"
}

validate_cdf() {
    # CDF = Compound Document Format (DOC, XLS, PPT legacy formats)
    local f="$1"
    [[ -f "$f" ]] && [[ -s "$f" ]] && file "$f" | grep -qiE "Composite Document|CDF|Microsoft"
}

validate_split_dir() {
    local d="$1"
    [[ -d "$d" ]] && [[ "$(ls -A "$d" 2>/dev/null)" ]]
}

validate_info_output() {
    local output="$1"
    # Use bash pattern match to avoid piping large debug outputs through echo
    [[ "${output,,}" == *"mime type"* || "${output,,}" == *"mimetype"* ]]
}

validate_backend_info() {
    local output="$1"
    [[ "${output,,}" == *"backend status"* || "${output,,}" == *"aspose backend"* ]]
}

# ── Runner functions ─────────────────────────────────────────────────

run_jvm() {
    local agent_flag=""
    if [[ "$MODE" == "agent" && -n "$METADATA_DIR" ]]; then
        agent_flag="-agentlib:native-image-agent=config-merge-dir=${METADATA_DIR}"
    fi
    "$JAVA_CMD" \
        $agent_flag \
        -Dsun.misc.unsafe.memory.access=allow \
        --add-opens=java.base/sun.misc=ALL-UNNAMED \
        --add-opens=java.base/java.lang=ALL-UNNAMED \
        -Djava.awt.headless=true \
        -jar "$ASSEMBLY_JAR" \
        "$@" 2>&1
}

run_native() {
    JAVA_HOME="${JAVA_HOME:-/opt/graalvm}" "$NATIVE_BIN" "$@" 2>&1
}

# ── Test execution ───────────────────────────────────────────────────

run_test() {
    local name="$1"
    local validator="$2"
    local validator_arg="$3"  # file path or "stdout"
    shift 3
    # Remaining args: XLCR CLI arguments

    TEST_NAMES+=("$name")

    local jvm_result="-" native_result="-"
    local jvm_time="-" native_time="-"
    local jvm_out="" native_out=""

    # Agent mode: run JVM with tracing, skip validation
    if [[ "$MODE" == "agent" ]]; then
        local agent_work="$WORK_DIR/agent"
        mkdir -p "$agent_work"

        local agent_args=()
        for arg in "$@"; do
            agent_args+=("${arg//__WORK__/$agent_work}")
        done

        run_jvm "${agent_args[@]}" >/dev/null 2>&1 || true
        JVM_RESULTS+=("OK")
        NATIVE_RESULTS+=("-")
        JVM_TIMES+=("-")
        NATIVE_TIMES+=("-")
        printf "."
        return
    fi

    # JVM run
    if [[ "$RUNNER" == "jvm" || "$RUNNER" == "both" ]]; then
        local jvm_work="$WORK_DIR/jvm"
        mkdir -p "$jvm_work"

        local jvm_args=()
        for arg in "$@"; do
            jvm_args+=("${arg//__WORK__/$jvm_work}")
        done

        local jvm_varg="${validator_arg//__WORK__/$jvm_work}"

        if [[ "$MODE" == "bench" || "$MODE" == "both" ]]; then
            local start end
            start=$(date +%s%N 2>/dev/null || python3 -c 'import time; print(int(time.time()*1e9))')
            jvm_out=$(run_jvm "${jvm_args[@]}")
            end=$(date +%s%N 2>/dev/null || python3 -c 'import time; print(int(time.time()*1e9))')
            jvm_time=$(( (end - start) / 1000000 ))
        else
            jvm_out=$(run_jvm "${jvm_args[@]}")
        fi

        if [[ "$validator_arg" == "stdout" ]]; then
            if $validator "$jvm_out"; then jvm_result="PASS"; else jvm_result="FAIL"; fi
        else
            if $validator "$jvm_varg"; then jvm_result="PASS"; else jvm_result="FAIL"; fi
        fi
    fi

    # Native run
    if [[ "$RUNNER" == "native" || "$RUNNER" == "both" ]]; then
        local native_work="$WORK_DIR/native"
        mkdir -p "$native_work"

        local native_args=()
        for arg in "$@"; do
            native_args+=("${arg//__WORK__/$native_work}")
        done

        local native_varg="${validator_arg//__WORK__/$native_work}"

        if [[ "$MODE" == "bench" || "$MODE" == "both" ]]; then
            local start end
            start=$(date +%s%N 2>/dev/null || python3 -c 'import time; print(int(time.time()*1e9))')
            native_out=$(run_native "${native_args[@]}")
            end=$(date +%s%N 2>/dev/null || python3 -c 'import time; print(int(time.time()*1e9))')
            native_time=$(( (end - start) / 1000000 ))
        else
            native_out=$(run_native "${native_args[@]}")
        fi

        if [[ "$validator_arg" == "stdout" ]]; then
            if $validator "$native_out"; then native_result="PASS"; else native_result="FAIL"; fi
        else
            if $validator "$native_varg"; then native_result="PASS"; else native_result="FAIL"; fi
        fi
    fi

    JVM_RESULTS+=("$jvm_result")
    NATIVE_RESULTS+=("$native_result")
    JVM_TIMES+=("$jvm_time")
    NATIVE_TIMES+=("$native_time")

    if [[ "$jvm_result" == "FAIL" || "$native_result" == "FAIL" ]]; then
        printf "x"
    else
        printf "."
    fi
}

# ── Print results table ──────────────────────────────────────────────

print_results() {
    echo ""
    echo ""

    # Agent mode: print metadata summary instead of pass/fail table
    if [[ "$MODE" == "agent" ]]; then
        echo "XLCR Tracing Agent Results"
        echo "========================="
        echo ""
        echo "Traced ${#TEST_NAMES[@]} operations"
        echo ""
        echo "Metadata files in $METADATA_DIR:"
        ls -la "$METADATA_DIR/" 2>/dev/null || echo "  (no files)"
        echo ""
        if [[ -f "$METADATA_DIR/reachability-metadata.json" ]]; then
            local entry_count
            entry_count=$(grep -c '"type"' "$METADATA_DIR/reachability-metadata.json" 2>/dev/null || echo "0")
            echo "Reflection entries: $entry_count"
        fi
        echo ""
        echo "Done. Extract with: docker cp <container>:$METADATA_DIR/ ./metadata-output/"
        return 0
    fi

    echo "XLCR Test Results"
    echo "================="
    echo ""

    if [[ "$MODE" == "bench" || "$MODE" == "both" ]]; then
        printf "%-32s %-8s %-8s %10s %12s %8s\n" \
            "Test" "JVM" "Native" "JVM Time" "Native Time" "Speedup"
        printf "%-32s %-8s %-8s %10s %12s %8s\n" \
            "----" "---" "------" "--------" "-----------" "-------"
    else
        printf "%-32s %-8s %-8s\n" "Test" "JVM" "Native"
        printf "%-32s %-8s %-8s\n" "----" "---" "------"
    fi

    local total=0 jvm_pass=0 native_pass=0 jvm_fail=0 native_fail=0

    for i in "${!TEST_NAMES[@]}"; do
        total=$((total + 1))
        local name="${TEST_NAMES[$i]}"
        local jr="${JVM_RESULTS[$i]}"
        local nr="${NATIVE_RESULTS[$i]}"
        local jt="${JVM_TIMES[$i]}"
        local nt="${NATIVE_TIMES[$i]}"

        [[ "$jr" == "PASS" ]] && jvm_pass=$((jvm_pass + 1))
        [[ "$jr" == "FAIL" ]] && jvm_fail=$((jvm_fail + 1))
        [[ "$nr" == "PASS" ]] && native_pass=$((native_pass + 1))
        [[ "$nr" == "FAIL" ]] && native_fail=$((native_fail + 1))

        if [[ "$MODE" == "bench" || "$MODE" == "both" ]]; then
            local speedup="-"
            if [[ "$jt" != "-" && "$nt" != "-" && "$nt" -gt 0 ]]; then
                speedup=$(python3 -c "print(f'{$jt/$nt:.1f}x')")
            fi
            local jt_fmt="-" nt_fmt="-"
            [[ "$jt" != "-" ]] && jt_fmt="${jt}ms"
            [[ "$nt" != "-" ]] && nt_fmt="${nt}ms"
            printf "%-32s %-8s %-8s %10s %12s %8s\n" \
                "$name" "$jr" "$nr" "$jt_fmt" "$nt_fmt" "$speedup"
        else
            printf "%-32s %-8s %-8s\n" "$name" "$jr" "$nr"
        fi
    done

    echo ""
    echo "Summary: $total tests"
    if [[ "$RUNNER" == "jvm" || "$RUNNER" == "both" ]]; then
        echo "  JVM:    $jvm_pass passed, $jvm_fail failed"
    fi
    if [[ "$RUNNER" == "native" || "$RUNNER" == "both" ]]; then
        echo "  Native: $native_pass passed, $native_fail failed"
    fi

    if [[ $jvm_fail -gt 0 || $native_fail -gt 0 ]]; then
        return 1
    fi
    return 0
}

# ── Main test matrix ─────────────────────────────────────────────────

main() {
    if [[ "$MODE" == "agent" ]]; then
        echo "XLCR Tracing Agent"
        echo "=================="
        echo "Metadata output: $METADATA_DIR"
    else
        echo "XLCR Conversion Test Suite"
        echo "=========================="
        echo "Mode: $MODE | Runner: $RUNNER"
    fi
    echo ""

    # Clean work directory
    rm -rf "$WORK_DIR"
    mkdir -p "$WORK_DIR/jvm" "$WORK_DIR/native" "$WORK_DIR/fixtures"

    # ── Generate fixtures ─────────────────────────────────────────────
    # Pre-generate fixtures via JVM to avoid cascading failures.
    echo "Generating fixtures..."
    local FIX="$WORK_DIR/fixtures"

    run_jvm convert -i "$TESTDATA/test.html" -o "$FIX/test.pdf" >/dev/null 2>&1 || true
    run_jvm convert -i "$TESTDATA/test.html" -o "$FIX/test.pptx" >/dev/null 2>&1 || true

    if [[ -f "$FIX/test.pdf" ]]; then
        run_jvm convert -i "$FIX/test.pdf" -o "$FIX/test.png" >/dev/null 2>&1 || true
    fi

    # Generate legacy format fixtures via LibreOffice
    if [[ -f "$TESTDATA/test.docx" ]]; then
        run_jvm convert -i "$TESTDATA/test.docx" -o "$FIX/test.doc" --backend libreoffice >/dev/null 2>&1 || true
    fi
    if [[ -f "$TESTDATA/test.xlsx" ]]; then
        run_jvm convert -i "$TESTDATA/test.xlsx" -o "$FIX/test.xls" --backend libreoffice >/dev/null 2>&1 || true
        run_jvm convert -i "$TESTDATA/test.xlsx" -o "$FIX/test.ods" >/dev/null 2>&1 || true
    fi
    if [[ -f "$FIX/test.pptx" ]]; then
        run_jvm convert -i "$FIX/test.pptx" -o "$FIX/test.ppt" --backend libreoffice >/dev/null 2>&1 || true
    fi

    echo "Fixtures ready."
    echo ""
    printf "Running tests: "

    # ══════════════════════════════════════════════════════════════════
    # Phase 1: Aspose conversions
    # ══════════════════════════════════════════════════════════════════

    # HTML conversions
    run_test "HTML -> PDF" validate_pdf "__WORK__/test.pdf" \
        convert -i "$TESTDATA/test.html" -o "__WORK__/test.pdf"

    run_test "HTML -> PPTX" validate_zip "__WORK__/test.pptx" \
        convert -i "$TESTDATA/test.html" -o "__WORK__/test.pptx"

    # PDF conversions
    if [[ -f "$FIX/test.pdf" ]]; then
        run_test "PDF -> HTML" validate_html "__WORK__/from-pdf.html" \
            convert -i "$FIX/test.pdf" -o "__WORK__/from-pdf.html"

        run_test "PDF -> PPTX" validate_zip "__WORK__/from-pdf.pptx" \
            convert -i "$FIX/test.pdf" -o "__WORK__/from-pdf.pptx"

        run_test "PDF -> PNG" validate_png "__WORK__/test.png" \
            convert -i "$FIX/test.pdf" -o "__WORK__/test.png"

        run_test "PDF -> JPEG" validate_jpeg "__WORK__/test.jpg" \
            convert -i "$FIX/test.pdf" -o "__WORK__/test.jpg"
    fi

    # Image → PDF
    if [[ -f "$FIX/test.png" ]]; then
        run_test "PNG -> PDF" validate_pdf "__WORK__/from-png.pdf" \
            convert -i "$FIX/test.png" -o "__WORK__/from-png.pdf"
    fi
    if [[ -f "$TESTDATA/test.jpg" ]]; then
        run_test "JPEG -> PDF" validate_pdf "__WORK__/from-jpg.pdf" \
            convert -i "$TESTDATA/test.jpg" -o "__WORK__/from-jpg.pdf"
    fi

    # PowerPoint conversions
    if [[ -f "$FIX/test.pptx" ]]; then
        run_test "PPTX -> PDF" validate_pdf "__WORK__/from-pptx.pdf" \
            convert -i "$FIX/test.pptx" -o "__WORK__/from-pptx.pdf"

        run_test "PPTX -> HTML" validate_html "__WORK__/from-pptx.html" \
            convert -i "$FIX/test.pptx" -o "__WORK__/from-pptx.html"
    fi

    # Word conversions
    if [[ -f "$TESTDATA/test.docx" ]]; then
        run_test "DOCX -> PDF" validate_pdf "__WORK__/from-docx.pdf" \
            convert -i "$TESTDATA/test.docx" -o "__WORK__/from-docx.pdf"
    fi

    # Excel conversions
    if [[ -f "$TESTDATA/test.xlsx" ]]; then
        run_test "XLSX -> PDF" validate_pdf "__WORK__/from-xlsx.pdf" \
            convert -i "$TESTDATA/test.xlsx" -o "__WORK__/from-xlsx.pdf"

        run_test "XLSX -> ODS" validate_ods "__WORK__/from-xlsx.ods" \
            convert -i "$TESTDATA/test.xlsx" -o "__WORK__/from-xlsx.ods"

        run_test "XLSX -> HTML" validate_html "__WORK__/from-xlsx.html" \
            convert -i "$TESTDATA/test.xlsx" -o "__WORK__/from-xlsx.html"
    fi

    # Email conversions
    if [[ -f "$TESTDATA/test.eml" ]]; then
        run_test "EML -> PDF" validate_pdf "__WORK__/from-eml.pdf" \
            convert -i "$TESTDATA/test.eml" -o "__WORK__/from-eml.pdf"
    fi

    # ══════════════════════════════════════════════════════════════════
    # Phase 2: Aspose splits
    # ══════════════════════════════════════════════════════════════════

    if [[ -f "$FIX/test.pdf" ]]; then
        run_test "Split PDF" validate_split_dir "__WORK__/split-pdf" \
            split -i "$FIX/test.pdf" -d "__WORK__/split-pdf" --extract
    fi

    if [[ -f "$FIX/test.pptx" ]]; then
        run_test "Split PPTX" validate_split_dir "__WORK__/split-pptx" \
            split -i "$FIX/test.pptx" -d "__WORK__/split-pptx" --extract
    fi

    if [[ -f "$TESTDATA/test.xlsx" ]]; then
        run_test "Split XLSX" validate_split_dir "__WORK__/split-xlsx" \
            split -i "$TESTDATA/test.xlsx" -d "__WORK__/split-xlsx" --extract
    fi

    if [[ -f "$TESTDATA/test.docx" ]]; then
        run_test "Split DOCX" validate_split_dir "__WORK__/split-docx" \
            split -i "$TESTDATA/test.docx" -d "__WORK__/split-docx" --extract
    fi

    if [[ -f "$TESTDATA/test.eml" ]]; then
        run_test "Split EML" validate_split_dir "__WORK__/split-eml" \
            split -i "$TESTDATA/test.eml" -d "__WORK__/split-eml" --extract
    fi

    if [[ -f "$TESTDATA/test.zip" ]]; then
        run_test "Split ZIP" validate_split_dir "__WORK__/split-zip" \
            split -i "$TESTDATA/test.zip" -d "__WORK__/split-zip" --extract
    fi

    # ══════════════════════════════════════════════════════════════════
    # Phase 3: LibreOffice conversions (--backend libreoffice)
    # ══════════════════════════════════════════════════════════════════

    if [[ -f "$TESTDATA/test.docx" ]]; then
        run_test "LO: DOCX -> PDF" validate_pdf "__WORK__/lo-docx.pdf" \
            convert -i "$TESTDATA/test.docx" -o "__WORK__/lo-docx.pdf" --backend libreoffice
    fi

    if [[ -f "$TESTDATA/test.xlsx" ]]; then
        run_test "LO: XLSX -> PDF" validate_pdf "__WORK__/lo-xlsx.pdf" \
            convert -i "$TESTDATA/test.xlsx" -o "__WORK__/lo-xlsx.pdf" --backend libreoffice
    fi

    if [[ -f "$FIX/test.pptx" ]]; then
        run_test "LO: PPTX -> PDF" validate_pdf "__WORK__/lo-pptx.pdf" \
            convert -i "$FIX/test.pptx" -o "__WORK__/lo-pptx.pdf" --backend libreoffice
    fi

    if [[ -f "$FIX/test.ods" ]]; then
        run_test "LO: ODS -> PDF" validate_pdf "__WORK__/lo-ods.pdf" \
            convert -i "$FIX/test.ods" -o "__WORK__/lo-ods.pdf" --backend libreoffice
    fi

    if [[ -f "$FIX/test.doc" ]]; then
        run_test "LO: DOC -> PDF" validate_pdf "__WORK__/lo-doc.pdf" \
            convert -i "$FIX/test.doc" -o "__WORK__/lo-doc.pdf" --backend libreoffice
    fi

    if [[ -f "$FIX/test.xls" ]]; then
        run_test "LO: XLS -> PDF" validate_pdf "__WORK__/lo-xls.pdf" \
            convert -i "$FIX/test.xls" -o "__WORK__/lo-xls.pdf" --backend libreoffice
    fi

    if [[ -f "$FIX/test.ppt" ]]; then
        run_test "LO: PPT -> PDF" validate_pdf "__WORK__/lo-ppt.pdf" \
            convert -i "$FIX/test.ppt" -o "__WORK__/lo-ppt.pdf" --backend libreoffice
    fi

    # LibreOffice format conversions
    if [[ -f "$TESTDATA/test.docx" ]]; then
        run_test "LO: DOCX -> DOC" validate_cdf "__WORK__/lo-docx.doc" \
            convert -i "$TESTDATA/test.docx" -o "__WORK__/lo-docx.doc" --backend libreoffice
    fi

    if [[ -f "$TESTDATA/test.xlsx" ]]; then
        run_test "LO: XLSX -> XLS" validate_cdf "__WORK__/lo-xlsx.xls" \
            convert -i "$TESTDATA/test.xlsx" -o "__WORK__/lo-xlsx.xls" --backend libreoffice
    fi

    if [[ -f "$FIX/test.pptx" ]]; then
        run_test "LO: PPTX -> PPT" validate_cdf "__WORK__/lo-pptx.ppt" \
            convert -i "$FIX/test.pptx" -o "__WORK__/lo-pptx.ppt" --backend libreoffice
    fi

    # ══════════════════════════════════════════════════════════════════
    # Phase 4: Core-only conversions + splits (--backend xlcr)
    # ══════════════════════════════════════════════════════════════════

    # Tika text extraction
    if [[ -f "$FIX/test.pdf" ]]; then
        run_test "Core: PDF -> TXT" validate_text "__WORK__/core-pdf.txt" \
            convert -i "$FIX/test.pdf" -o "__WORK__/core-pdf.txt" --backend xlcr
    fi

    if [[ -f "$TESTDATA/test.docx" ]]; then
        run_test "Core: DOCX -> TXT" validate_text "__WORK__/core-docx.txt" \
            convert -i "$TESTDATA/test.docx" -o "__WORK__/core-docx.txt" --backend xlcr
    fi

    if [[ -f "$TESTDATA/test.xlsx" ]]; then
        run_test "Core: XLSX -> TXT" validate_text "__WORK__/core-xlsx.txt" \
            convert -i "$TESTDATA/test.xlsx" -o "__WORK__/core-xlsx.txt" --backend xlcr
    fi

    # Tika XML extraction
    if [[ -f "$FIX/test.pdf" ]]; then
        run_test "Core: PDF -> XML" validate_xml "__WORK__/core-pdf.xml" \
            convert -i "$FIX/test.pdf" -o "__WORK__/core-pdf.xml" --backend xlcr
    fi

    # Core XLSX → ODS (POI + ODFDOM)
    if [[ -f "$TESTDATA/test.xlsx" ]]; then
        run_test "Core: XLSX -> ODS" validate_ods "__WORK__/core-xlsx.ods" \
            convert -i "$TESTDATA/test.xlsx" -o "__WORK__/core-xlsx.ods" --backend xlcr
    fi

    # Core splits (POI/PDFBox)
    if [[ -f "$TESTDATA/test.xlsx" ]]; then
        run_test "Core: Split XLSX" validate_split_dir "__WORK__/core-split-xlsx" \
            split -i "$TESTDATA/test.xlsx" -d "__WORK__/core-split-xlsx" --extract --backend xlcr
    fi

    if [[ -f "$FIX/test.pptx" ]]; then
        run_test "Core: Split PPTX" validate_split_dir "__WORK__/core-split-pptx" \
            split -i "$FIX/test.pptx" -d "__WORK__/core-split-pptx" --extract --backend xlcr
    fi

    if [[ -f "$TESTDATA/test.docx" ]]; then
        run_test "Core: Split DOCX" validate_split_dir "__WORK__/core-split-docx" \
            split -i "$TESTDATA/test.docx" -d "__WORK__/core-split-docx" --extract --backend xlcr
    fi

    if [[ -f "$FIX/test.pdf" ]]; then
        run_test "Core: Split PDF" validate_split_dir "__WORK__/core-split-pdf" \
            split -i "$FIX/test.pdf" -d "__WORK__/core-split-pdf" --extract --backend xlcr
    fi

    # Core-unique splits (EML, ZIP, CSV — no Aspose equivalent in default priority)
    if [[ -f "$TESTDATA/test.eml" ]]; then
        run_test "Core: Split EML" validate_split_dir "__WORK__/core-split-eml" \
            split -i "$TESTDATA/test.eml" -d "__WORK__/core-split-eml" --extract --backend xlcr
    fi

    if [[ -f "$TESTDATA/test.zip" ]]; then
        run_test "Core: Split ZIP" validate_split_dir "__WORK__/core-split-zip" \
            split -i "$TESTDATA/test.zip" -d "__WORK__/core-split-zip" --extract --backend xlcr
    fi

    if [[ -f "$TESTDATA/test.csv" ]]; then
        run_test "Core: Split CSV" validate_split_dir "__WORK__/core-split-csv" \
            split -i "$TESTDATA/test.csv" -d "__WORK__/core-split-csv" --extract --backend xlcr
    fi

    # ══════════════════════════════════════════════════════════════════
    # Phase 5: Info / metadata (Tika parsers for all MIME types)
    # ══════════════════════════════════════════════════════════════════

    # Office formats
    if [[ -f "$TESTDATA/test.xlsx" ]]; then
        run_test "Info XLSX" validate_info_output "stdout" \
            info -i "$TESTDATA/test.xlsx"
    fi

    run_test "Info HTML" validate_info_output "stdout" \
        info -i "$TESTDATA/test.html"

    if [[ -f "$TESTDATA/test.docx" ]]; then
        run_test "Info DOCX" validate_info_output "stdout" \
            info -i "$TESTDATA/test.docx"
    fi

    if [[ -f "$TESTDATA/test.pdf" ]]; then
        run_test "Info PDF" validate_info_output "stdout" \
            info -i "$TESTDATA/test.pdf"
    fi

    if [[ -f "$FIX/test.pptx" ]]; then
        run_test "Info PPTX" validate_info_output "stdout" \
            info -i "$FIX/test.pptx"
    fi

    if [[ -f "$FIX/test.ods" ]]; then
        run_test "Info ODS" validate_info_output "stdout" \
            info -i "$FIX/test.ods"
    fi

    # Email / data / archive
    if [[ -f "$TESTDATA/test.eml" ]]; then
        run_test "Info EML" validate_info_output "stdout" \
            info -i "$TESTDATA/test.eml"
    fi

    if [[ -f "$TESTDATA/test.csv" ]]; then
        run_test "Info CSV" validate_info_output "stdout" \
            info -i "$TESTDATA/test.csv"
    fi

    if [[ -f "$TESTDATA/test.zip" ]]; then
        run_test "Info ZIP" validate_info_output "stdout" \
            info -i "$TESTDATA/test.zip"
    fi

    # Image formats
    if [[ -f "$TESTDATA/test.jpg" ]]; then
        run_test "Info JPEG" validate_info_output "stdout" \
            info -i "$TESTDATA/test.jpg"
    fi

    if [[ -f "$TESTDATA/test.tiff" ]]; then
        run_test "Info TIFF" validate_info_output "stdout" \
            info -i "$TESTDATA/test.tiff"
    fi

    # ══════════════════════════════════════════════════════════════════
    # Phase 6: Backend info
    # ══════════════════════════════════════════════════════════════════

    run_test "Backend info" validate_backend_info "stdout" \
        --backend-info

    # ── Results ───────────────────────────────────────────────────────
    print_results
}

main "$@"
