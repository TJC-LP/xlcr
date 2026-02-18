#!/usr/bin/env bash
# test-conversions.sh
# Unified test and benchmark script for XLCR native image workflow.
#
# Validates file output (size > 0, correct MIME type via `file`), never exit codes.
# Runs both JVM and native in the same container with the same fixtures.
#
# Modes:
#   --mode test   Pass/fail table (default)
#   --mode bench  Timing comparison table
#   --mode both   Both test and benchmark
#
# Runners:
#   --runner jvm     JVM only
#   --runner native  Native only
#   --runner both    Both (default)
#
# Usage:
#   ./scripts/test-conversions.sh --mode test
#   ./scripts/test-conversions.sh --mode bench --runner native
#   ./scripts/test-conversions.sh --mode both

set -uo pipefail

# ── Configuration ─────────────────────────────────────────────────────
MODE="test"
RUNNER="both"
WORK_DIR="/tmp/xlcr-test"
TESTDATA="/xlcr/testdata"
NATIVE_BIN="/xlcr/xlcr-native"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --mode)   MODE="$2"; shift 2 ;;
        --runner) RUNNER="$2"; shift 2 ;;
        *)        echo "Unknown argument: $1"; exit 1 ;;
    esac
done

# ── Find JVM components ──────────────────────────────────────────────
ASSEMBLY_JAR=$(find /xlcr/out -path '*/assembly.dest/out.jar' -type f 2>/dev/null | head -1)

if [[ -z "$ASSEMBLY_JAR" ]] && [[ "$RUNNER" != "native" ]]; then
    echo "ERROR: Assembly JAR not found in /xlcr/out/"
    echo "Build with: mill 'xlcr[3.3.4].assembly'"
    exit 1
fi

if [[ ! -f "$NATIVE_BIN" ]] && [[ "$RUNNER" != "jvm" ]]; then
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

validate_ods() {
    # ODS is a ZIP-based OpenDocument format
    local f="$1"
    [[ -f "$f" ]] && [[ -s "$f" ]] && file "$f" | grep -qiE "Zip archive|OpenDocument"
}

validate_split_dir() {
    local d="$1"
    [[ -d "$d" ]] && [[ "$(ls -A "$d" 2>/dev/null)" ]]
}

validate_info_output() {
    local output="$1"
    echo "$output" | grep -qi "MIME Type\|mimeType"
}

validate_backend_info() {
    local output="$1"
    echo "$output" | grep -qi "Backend Status\|Aspose Backend\|backend"
}

# ── Runner functions ─────────────────────────────────────────────────

run_jvm() {
    # Run XLCR via JVM (assembly JAR)
    java \
        -Dsun.misc.unsafe.memory.access=allow \
        --add-opens=java.base/sun.misc=ALL-UNNAMED \
        --add-opens=java.base/java.lang=ALL-UNNAMED \
        -Djava.awt.headless=true \
        -jar "$ASSEMBLY_JAR" \
        "$@" 2>&1
}

run_native() {
    # Run XLCR via native binary
    JAVA_HOME="${JAVA_HOME:-/opt/graalvm}" "$NATIVE_BIN" "$@" 2>&1
}

# ── Timing helper ────────────────────────────────────────────────────

time_ms() {
    # Returns wall-clock milliseconds for a command
    local start end
    start=$(date +%s%N 2>/dev/null || python3 -c 'import time; print(int(time.time()*1e9))')
    "$@" >/dev/null 2>&1
    end=$(date +%s%N 2>/dev/null || python3 -c 'import time; print(int(time.time()*1e9))')
    echo $(( (end - start) / 1000000 ))
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

    # JVM run
    if [[ "$RUNNER" == "jvm" || "$RUNNER" == "both" ]]; then
        local jvm_work="$WORK_DIR/jvm"
        mkdir -p "$jvm_work"

        # Replace WORK placeholder with jvm work dir
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

        # Validate
        if [[ "$validator_arg" == "stdout" ]]; then
            if $validator "$jvm_out"; then
                jvm_result="PASS"
            else
                jvm_result="FAIL"
            fi
        else
            if $validator "$jvm_varg"; then
                jvm_result="PASS"
            else
                jvm_result="FAIL"
            fi
        fi
    fi

    # Native run
    if [[ "$RUNNER" == "native" || "$RUNNER" == "both" ]]; then
        local native_work="$WORK_DIR/native"
        mkdir -p "$native_work"

        # Replace WORK placeholder with native work dir
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

        # Validate
        if [[ "$validator_arg" == "stdout" ]]; then
            if $validator "$native_out"; then
                native_result="PASS"
            else
                native_result="FAIL"
            fi
        else
            if $validator "$native_varg"; then
                native_result="PASS"
            else
                native_result="FAIL"
            fi
        fi
    fi

    JVM_RESULTS+=("$jvm_result")
    NATIVE_RESULTS+=("$native_result")
    JVM_TIMES+=("$jvm_time")
    NATIVE_TIMES+=("$native_time")

    # Print progress dot
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
    echo "XLCR Test Results"
    echo "================="
    echo ""

    if [[ "$MODE" == "bench" || "$MODE" == "both" ]]; then
        printf "%-28s %-8s %-8s %10s %12s %8s\n" \
            "Test" "JVM" "Native" "JVM Time" "Native Time" "Speedup"
        printf "%-28s %-8s %-8s %10s %12s %8s\n" \
            "----" "---" "------" "--------" "-----------" "-------"
    else
        printf "%-28s %-8s %-8s\n" "Test" "JVM" "Native"
        printf "%-28s %-8s %-8s\n" "----" "---" "------"
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
            local jt_fmt="-"
            local nt_fmt="-"
            [[ "$jt" != "-" ]] && jt_fmt="${jt}ms"
            [[ "$nt" != "-" ]] && nt_fmt="${nt}ms"
            printf "%-28s %-8s %-8s %10s %12s %8s\n" \
                "$name" "$jr" "$nr" "$jt_fmt" "$nt_fmt" "$speedup"
        else
            printf "%-28s %-8s %-8s\n" "$name" "$jr" "$nr"
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

    # Exit with failure if any tests failed
    if [[ $jvm_fail -gt 0 || $native_fail -gt 0 ]]; then
        return 1
    fi
    return 0
}

# ── Main test matrix ─────────────────────────────────────────────────

main() {
    echo "XLCR Conversion Test Suite"
    echo "=========================="
    echo "Mode: $MODE | Runner: $RUNNER"
    echo ""

    # Clean work directory
    rm -rf "$WORK_DIR"
    mkdir -p "$WORK_DIR/jvm" "$WORK_DIR/native"

    printf "Running tests: "

    # ── HTML conversions ──────────────────────────────────────────────

    # 1. HTML → PDF
    run_test "HTML -> PDF" validate_pdf "__WORK__/test.pdf" \
        convert -i "$TESTDATA/test.html" -o "__WORK__/test.pdf"

    # 2. HTML → PPTX
    run_test "HTML -> PPTX" validate_zip "__WORK__/test.pptx" \
        convert -i "$TESTDATA/test.html" -o "__WORK__/test.pptx"

    # ── PDF conversions (depend on HTML → PDF output) ─────────────────
    # Use a pre-generated PDF for these tests to avoid cascading failures.
    # Generate it once from JVM which is known-good.
    local test_pdf="$WORK_DIR/fixtures/test.pdf"
    mkdir -p "$WORK_DIR/fixtures"
    run_jvm convert -i "$TESTDATA/test.html" -o "$test_pdf" >/dev/null 2>&1 || true

    if [[ -f "$test_pdf" ]]; then
        # 3. PDF → HTML
        run_test "PDF -> HTML" validate_html "__WORK__/from-pdf.html" \
            convert -i "$test_pdf" -o "__WORK__/from-pdf.html"

        # 4. PDF → PPTX
        run_test "PDF -> PPTX" validate_zip "__WORK__/from-pdf.pptx" \
            convert -i "$test_pdf" -o "__WORK__/from-pdf.pptx"

        # 5. PDF → PNG
        run_test "PDF -> PNG" validate_png "__WORK__/test.png" \
            convert -i "$test_pdf" -o "__WORK__/test.png"
    else
        echo ""
        echo "WARNING: Could not generate test PDF — skipping PDF-based tests"
    fi

    # 6. PNG → PDF (generate a PNG first via JVM)
    local test_png="$WORK_DIR/fixtures/test.png"
    if [[ -f "$test_pdf" ]]; then
        run_jvm convert -i "$test_pdf" -o "$test_png" >/dev/null 2>&1 || true
    fi
    if [[ -f "$test_png" ]]; then
        run_test "PNG -> PDF" validate_pdf "__WORK__/from-png.pdf" \
            convert -i "$test_png" -o "__WORK__/from-png.pdf"
    fi

    # ── PowerPoint conversions ────────────────────────────────────────
    # Generate a PPTX fixture via JVM
    local test_pptx="$WORK_DIR/fixtures/test.pptx"
    run_jvm convert -i "$TESTDATA/test.html" -o "$test_pptx" >/dev/null 2>&1 || true

    if [[ -f "$test_pptx" ]]; then
        # 7. PPTX → PDF
        run_test "PPTX -> PDF" validate_pdf "__WORK__/from-pptx.pdf" \
            convert -i "$test_pptx" -o "__WORK__/from-pptx.pdf"

        # 8. PPTX → HTML
        run_test "PPTX -> HTML" validate_html "__WORK__/from-pptx.html" \
            convert -i "$test_pptx" -o "__WORK__/from-pptx.html"
    fi

    # ── Word conversions ──────────────────────────────────────────────

    if [[ -f "$TESTDATA/test.docx" ]]; then
        # 9. DOCX → PDF
        run_test "DOCX -> PDF" validate_pdf "__WORK__/from-docx.pdf" \
            convert -i "$TESTDATA/test.docx" -o "__WORK__/from-docx.pdf"
    fi

    # ── Excel conversions ─────────────────────────────────────────────

    if [[ -f "$TESTDATA/test.xlsx" ]]; then
        # 10. XLSX → PDF
        run_test "XLSX -> PDF" validate_pdf "__WORK__/from-xlsx.pdf" \
            convert -i "$TESTDATA/test.xlsx" -o "__WORK__/from-xlsx.pdf"

        # 11. XLSX → ODS
        run_test "XLSX -> ODS" validate_ods "__WORK__/from-xlsx.ods" \
            convert -i "$TESTDATA/test.xlsx" -o "__WORK__/from-xlsx.ods"

        # 12. XLSX → HTML
        run_test "XLSX -> HTML" validate_html "__WORK__/from-xlsx.html" \
            convert -i "$TESTDATA/test.xlsx" -o "__WORK__/from-xlsx.html"
    fi

    # ── Split operations ──────────────────────────────────────────────

    if [[ -f "$test_pdf" ]]; then
        # 13. Split PDF
        run_test "Split PDF" validate_split_dir "__WORK__/split-pdf" \
            split -i "$test_pdf" -d "__WORK__/split-pdf" --extract
    fi

    if [[ -f "$test_pptx" ]]; then
        # 14. Split PPTX
        run_test "Split PPTX" validate_split_dir "__WORK__/split-pptx" \
            split -i "$test_pptx" -d "__WORK__/split-pptx" --extract
    fi

    if [[ -f "$TESTDATA/test.xlsx" ]]; then
        # 15. Split XLSX
        run_test "Split XLSX" validate_split_dir "__WORK__/split-xlsx" \
            split -i "$TESTDATA/test.xlsx" -d "__WORK__/split-xlsx" --extract
    fi

    if [[ -f "$TESTDATA/test.docx" ]]; then
        # 16. Split DOCX
        run_test "Split DOCX" validate_split_dir "__WORK__/split-docx" \
            split -i "$TESTDATA/test.docx" -d "__WORK__/split-docx" --extract
    fi

    # ── Info / metadata ───────────────────────────────────────────────

    if [[ -f "$TESTDATA/test.xlsx" ]]; then
        # 17. Info XLSX
        run_test "Info XLSX" validate_info_output "stdout" \
            info -i "$TESTDATA/test.xlsx"
    fi

    # 18. Info HTML
    run_test "Info HTML" validate_info_output "stdout" \
        info -i "$TESTDATA/test.html"

    if [[ -f "$TESTDATA/test.docx" ]]; then
        # 19. Info DOCX
        run_test "Info DOCX" validate_info_output "stdout" \
            info -i "$TESTDATA/test.docx"
    fi

    # 20. Backend info
    run_test "Backend info" validate_backend_info "stdout" \
        --backend-info

    # ── Results ───────────────────────────────────────────────────────
    print_results
}

main "$@"
