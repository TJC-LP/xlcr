# Progress Log: Aspose Native Parity Investigation

## Session Date
- 2026-02-18

## Goal
- Reach full native parity for Aspose-backed transforms/splits, with priority on `Split PPTX`.

## Current Native Status
- Native conversion test matrix (`/xlcr/scripts/test-conversions.sh --mode test --runner native`) remains:
  - `20` total
  - `16` passed
  - `4` failed

### Failing Native Cases
- `HTML -> PPTX`
- `PDF -> HTML`
- `PDF -> PPTX`
- `Split PPTX`

### Passing Native Cases (not exhaustive)
- `XLSX -> HTML` now passes.
- `PPTX -> PDF`, `PPTX -> HTML`, `Split PDF`, `Split XLSX`, `Split DOCX` pass.

## Verified Build/Metadata Wiring
- Confirmed native metadata is present in built image:
  - `/xlcr/xlcr/src/main/resources/META-INF/native-image/reachability-metadata.json`
- Confirmed native build uses metadata directory:
  - `-H:ConfigurationFileDirectories=/xlcr/xlcr/src/main/resources/META-INF/native-image`
- This removed uncertainty that new metadata was being ignored at build time.

## Code Changes Landed
- Commit: `98e03ed`
- Message: `Add Excel->HTML Aspose transforms and native metadata updates`
- Files:
  - `core-aspose/src/main/scala-3/com/tjclp/xlcr/v2/aspose/AsposeTransforms.scala`
  - `core-aspose/src/main/scala-3/com/tjclp/xlcr/v2/aspose/conversions.scala`
  - `core-aspose/src/main/scala-3/com/tjclp/xlcr/v2/aspose/splitters.scala`
  - `xlcr/src/main/resources/META-INF/native-image/reachability-metadata.json`

### Functional Additions
- Added Excel-family to HTML transform routes in `AsposeTransforms`:
  - `XLSX|XLS|XLSM|XLSB|ODS -> HTML`
- Added workbook-to-HTML conversion implementations in `conversions.scala`.

### Native-Stability-Oriented Changes
- Replaced several Aspose Slides stream save paths with temp-file save/readback:
  - `HTML -> PPTX/PPT`
  - `PDF -> PPTX/PPT`
  - PPT/PPTX split fragment save path
- Rationale: avoid `ByteArrayOutputStream`-based code path in native runtime.

## Experiments Run and Outcomes

1. Temp-file save path for Slides
- Outcome: no parity change (`16/20`, same 4 failures).

2. Native build with `-O0`
- Outcome: no parity change (`16/20`, same 4 failures).

3. Native build with `--gc=epsilon`
- Outcome: no parity change (`16/20`, same 4 failures).

4. Crash handling with `-XX:-InstallSegfaultHandler`
- Enabled real core dumps (`ulimit -c unlimited`).
- Without this flag: process exits with native handler (`exit 99`).
- With this flag: process exits with true SIGSEGV (`exit 139`) and produces `core` file.

## Crash Signatures

### PPTX-related failures
- `HTML -> PPTX`, `PDF -> PPTX`, `Split PPTX` all crash in:
  - `com.aspose.slides.Presentation.save`
- Observed frames include:
  - `com.tjclp.xlcr.v2.aspose.splitters$package$.savePresentationToBytes(splitters.scala:154)`
  - callsites in `splitters.scala` and `conversions.scala` (expected wrappers)

### PDF -> HTML failure
- Crashes in:
  - `com.aspose.pdf.Document.save`
- Observed from `asposePdfToHtml` call path in `conversions.scala`.

## Interpretation
- Current evidence suggests these are hard native runtime incompatibilities in Aspose save/render internals under Graal native image, not simple reflection metadata misses.
- Metadata expansion and call-shape changes did not alter failure set.

## Practical Options From Here
1. Run Graal version A/B test (`25.0.1` vs `25.0.2`) from commit `98e03ed`.
2. Build a debug runtime image with `gdb` and inspect core dumps for lower-level fault site details.
3. Implement targeted JVM fallback only for failing save paths (`Slides save`, `Pdf save`) to achieve functional parity while keeping native fast path for all passing transforms.
