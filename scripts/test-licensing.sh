#!/usr/bin/env bash
# test-licensing.sh — Dogfood all Aspose license discovery paths
#
# Exercises per-product lazy licensing, env var resolution, CWD file discovery,
# and fallback to LibreOffice when specific products are unlicensed.
#
# Prerequisites:
#   make install-user   (with total license bundled in JAR)
#   LibreOffice installed (for fallback tests)
#
# Environment overrides used:
#   XLCR_NO_ASPOSE_LICENSE=1    — kill ALL license resolution (complete blackout)
#   XLCR_NO_CLASSPATH_LICENSE=1 — skip JAR-bundled licenses; CWD files + env vars still work

set -euo pipefail

TOTAL_LIC="Aspose.Total.Java.lic"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
WORKDIR=$(mktemp -d)
PASS=0
FAIL=0

# Test files
XLSX="$REPO_ROOT/data/export-excel/001_Sheet1.xlsx"
PPTX="$REPO_ROOT/data/test.pptx"

cleanup() { rm -rf "$WORKDIR"; }
trap cleanup EXIT

# Verify prerequisites
if ! command -v xlcr &>/dev/null; then
  echo "ERROR: xlcr not found. Run 'make install-user' first." >&2
  exit 1
fi
if [[ ! -f "$REPO_ROOT/$TOTAL_LIC" ]]; then
  echo "ERROR: $TOTAL_LIC not found at repo root." >&2
  exit 1
fi

# Strip all Aspose env vars
UNSET_ALL=(-u ASPOSE_TOTAL_LICENSE_B64 \
           -u ASPOSE_CELLS_LICENSE_B64 \
           -u ASPOSE_WORDS_LICENSE_B64 \
           -u ASPOSE_SLIDES_LICENSE_B64 \
           -u ASPOSE_PDF_LICENSE_B64 \
           -u ASPOSE_EMAIL_LICENSE_B64 \
           -u ASPOSE_ZIP_LICENSE_B64)

assert_contains() {
  local label="$1" output="$2" expected="$3"
  if echo "$output" | grep -q "$expected"; then
    echo "  PASS: $label"
    PASS=$((PASS + 1))
  else
    echo "  FAIL: $label — expected '$expected'"
    echo "    Got: $(echo "$output" | grep -iE 'Aspose\.|LibreOffice|conversion failed|Successfully' | head -3)"
    FAIL=$((FAIL + 1))
  fi
}

assert_not_contains() {
  local label="$1" output="$2" rejected="$3"
  if echo "$output" | grep -q "$rejected"; then
    echo "  FAIL: $label — should NOT contain '$rejected'"
    FAIL=$((FAIL + 1))
  else
    echo "  PASS: $label"
    PASS=$((PASS + 1))
  fi
}

B64=$(base64 < "$REPO_ROOT/$TOTAL_LIC")

echo "================================================================"
echo " Aspose License Discovery — Dogfood Tests"
echo "================================================================"
echo ""
echo "Repo root:  $REPO_ROOT"
echo "Work dir:   $WORKDIR"
echo "Total lic:  $REPO_ROOT/$TOTAL_LIC"
echo ""

# ═══════════════════════════════════════════════════════════════════
echo "--- Scenario 1: No license (kill switch) ---"
echo "    Expect: LibreOffice fallback for XLSX->PDF"
rm -f "$WORKDIR"/*.lic
OUT=$(cd "$WORKDIR" && env "${UNSET_ALL[@]}" XLCR_NO_ASPOSE_LICENSE=1 \
  xlcr convert -i "$XLSX" -o "$WORKDIR/s1.pdf" 2>&1) || true
assert_contains     "XLSX->PDF succeeds"  "$OUT" "Successfully converted"
assert_not_contains "No Aspose log"       "$OUT" "Aspose.Cells license applied"
echo ""

# ═══════════════════════════════════════════════════════════════════
echo "--- Scenario 2: Total license via ASPOSE_TOTAL_LICENSE_B64 ---"
echo "    Expect: Aspose.Cells for XLSX->PDF"
rm -f "$WORKDIR"/*.lic
OUT=$(cd "$WORKDIR" && env "${UNSET_ALL[@]}" XLCR_NO_CLASSPATH_LICENSE=1 \
  ASPOSE_TOTAL_LICENSE_B64="$B64" \
  xlcr convert -i "$XLSX" -o "$WORKDIR/s2.pdf" 2>&1) || true
assert_contains "XLSX->PDF uses Aspose.Cells" "$OUT" "Aspose.Cells license applied"
echo ""

# ═══════════════════════════════════════════════════════════════════
echo "--- Scenario 3: Total license file in CWD ---"
echo "    Expect: Aspose.Cells for XLSX->PDF"
rm -f "$WORKDIR"/*.lic
cp "$REPO_ROOT/$TOTAL_LIC" "$WORKDIR/$TOTAL_LIC"
OUT=$(cd "$WORKDIR" && env "${UNSET_ALL[@]}" XLCR_NO_CLASSPATH_LICENSE=1 \
  xlcr convert -i "$XLSX" -o "$WORKDIR/s3.pdf" 2>&1) || true
assert_contains "XLSX->PDF uses Aspose.Cells" "$OUT" "Aspose.Cells license applied"
rm -f "$WORKDIR"/*.lic
echo ""

# ═══════════════════════════════════════════════════════════════════
echo "--- Scenario 4: Bundled classpath license (default JAR) ---"
echo "    Expect: Aspose.Cells for XLSX->PDF (license bundled in JAR)"
rm -f "$WORKDIR"/*.lic
OUT=$(cd "$WORKDIR" && env "${UNSET_ALL[@]}" \
  xlcr convert -i "$XLSX" -o "$WORKDIR/s4.pdf" 2>&1) || true
assert_contains "XLSX->PDF uses bundled license" "$OUT" "Aspose.Cells license applied"
echo ""

# ═══════════════════════════════════════════════════════════════════
echo "--- Scenario 5: Cells-only license file ---"
echo "    Expect: XLSX->PDF via Aspose, PPTX->PDF via LibreOffice"
rm -f "$WORKDIR"/*.lic
cp "$REPO_ROOT/$TOTAL_LIC" "$WORKDIR/Aspose.Cells.Java.lic"
OUT=$(cd "$WORKDIR" && env "${UNSET_ALL[@]}" XLCR_NO_CLASSPATH_LICENSE=1 \
  xlcr convert -i "$XLSX" -o "$WORKDIR/s5a.pdf" 2>&1) || true
assert_contains "XLSX->PDF uses Aspose.Cells" "$OUT" "Aspose.Cells license applied"
OUT=$(cd "$WORKDIR" && env "${UNSET_ALL[@]}" XLCR_NO_CLASSPATH_LICENSE=1 \
  xlcr convert -i "$PPTX" -o "$WORKDIR/s5b.pdf" 2>&1) || true
assert_not_contains "PPTX->PDF no Aspose.Slides" "$OUT" "Aspose.Slides license applied"
assert_contains     "PPTX->PDF succeeds"          "$OUT" "Successfully converted"
rm -f "$WORKDIR"/*.lic
echo ""

# ═══════════════════════════════════════════════════════════════════
echo "--- Scenario 6: Cells + Slides license files ---"
echo "    Expect: Both XLSX and PPTX via Aspose"
rm -f "$WORKDIR"/*.lic
cp "$REPO_ROOT/$TOTAL_LIC" "$WORKDIR/Aspose.Cells.Java.lic"
cp "$REPO_ROOT/$TOTAL_LIC" "$WORKDIR/Aspose.Slides.Java.lic"
OUT=$(cd "$WORKDIR" && env "${UNSET_ALL[@]}" XLCR_NO_CLASSPATH_LICENSE=1 \
  xlcr convert -i "$XLSX" -o "$WORKDIR/s6a.pdf" 2>&1) || true
assert_contains "XLSX->PDF uses Aspose.Cells" "$OUT" "Aspose.Cells license applied"
OUT=$(cd "$WORKDIR" && env "${UNSET_ALL[@]}" XLCR_NO_CLASSPATH_LICENSE=1 \
  xlcr convert -i "$PPTX" -o "$WORKDIR/s6b.pdf" 2>&1) || true
assert_contains "PPTX->PDF uses Aspose.Slides" "$OUT" "Aspose.Slides license applied"
rm -f "$WORKDIR"/*.lic
echo ""

# ═══════════════════════════════════════════════════════════════════
echo "--- Scenario 7: Cells via ASPOSE_CELLS_LICENSE_B64 env var ---"
echo "    Expect: XLSX->PDF via Aspose"
rm -f "$WORKDIR"/*.lic
OUT=$(cd "$WORKDIR" && env "${UNSET_ALL[@]}" XLCR_NO_CLASSPATH_LICENSE=1 \
  ASPOSE_CELLS_LICENSE_B64="$B64" \
  xlcr convert -i "$XLSX" -o "$WORKDIR/s7.pdf" 2>&1) || true
assert_contains "XLSX->PDF uses Aspose.Cells" "$OUT" "Aspose.Cells license applied"
echo ""

# ═══════════════════════════════════════════════════════════════════
echo "================================================================"
echo " Results: $PASS passed, $FAIL failed"
echo "================================================================"

if [[ $FAIL -gt 0 ]]; then
  exit 1
fi
