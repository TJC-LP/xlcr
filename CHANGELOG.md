# Changelog

All notable changes to XLCR will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2026-02-23

### Added
- Per-product lazy Aspose licensing — `AsposeLicenseV2.require[Cells]` etc. with Scala 3 `transparent inline` zero-overhead dispatch; unlicensed products fall back to LibreOffice/Core automatically (PR #54)
- License-aware capability checks — `canConvertLicensed`/`canSplitLicensed` gate `--backend-info` and capability queries on runtime license status per product (PR #54)
- Kill switches — `XLCR_NO_ASPOSE=1` (exclude from build), `XLCR_NO_ASPOSE_LICENSE=1` (disable all license resolution), `XLCR_NO_CLASSPATH_LICENSE=1` (skip JAR-bundled licenses) (PR #54)
- Per-product env vars — `ASPOSE_WORDS_LICENSE_B64`, `ASPOSE_CELLS_LICENSE_B64`, etc. for individual product licenses (PR #54)
- License discovery test suite — `scripts/test-licensing.sh` with 8 scenarios / 12 assertions (PR #54)
- Native image support via GraalVM CE 25.0.2 with reachability metadata and multi-stage Dockerfile (PR #48)
- Legacy↔modern office format conversions — DOC↔DOCX, XLS↔XLSX, PPT↔PPTX (PR #49)
- Conversion options system with DRY helpers and backend-aware validation (PRs #50, #51)
- ZIO-blocks `Scope` for compile-time safe Aspose resource management (PR #52)
- Cross-sheet formula evaluation to static values on XLSX split (PR #48)
- Excel→HTML Aspose transforms (PR #48)

### Changed
- Replaced fragile compile-time Aspose detection with explicit `XLCR_NO_ASPOSE=1` opt-out — Aspose always included by default, runtime license checks handle the unlicensed case (PR #54)
- Upgraded Tika 3.2.1→3.2.3, POI 5.5.0→5.5.1, Spark 4.0.0→4.1.1 (PR #53)
- Scoped resource cleanup across Aspose bridges for better memory efficiency (PRs #52, #53)

### Deprecated
- `AsposeLicense` (v1) — use `AsposeLicenseV2` instead; v1 marked `@deprecated` with kill switch support for backward compat (PR #54)

### Fixed
- `initOnce` double-checked locking in `AsposeLicenseV2.initProduct` — concurrent callers now block until init completes instead of silently skipping (PR #54)
- `--strip-masters` disposal management improved (PR #50)

## [0.1.3] - 2026-02-12

### Changed
- Upgraded `aspose-pdf` from 25.6 to 26.1 (dropped `jdk17` classifier)
- Updated Aspose license (expiry 2025-09-17 → 2026-09-17)
- CI now automatically builds server JAR and creates GitHub Release on tag push

### Fixed
- PDF page splitting now calls `optimizeResources()` to deduplicate fonts/streams — reduces per-page output from ~7MB to 37-355KB (98%+ reduction)
- Fixed image-to-PDF test fixtures for stricter Aspose PDF 26.1 parsing
- Resolved infinite recursion in ZipBuilder Chunk overloads (cherry-picked from v0.1.1)

## [0.1.0] - 2026-02-10

First stable release. XLCR provides document conversion and splitting across PDF, Office, HTML, and OpenDocument formats with pluggable backends (Aspose, LibreOffice, Apache POI/Tika).

### Highlights

- **Compile-time transform discovery** - Scala 3 macros for zero-overhead backend dispatch
- **HTTP server** - REST API for document conversion and splitting via ZIO HTTP
- **Three-tier backend system** - Aspose (commercial) > LibreOffice (open-source) > Core (POI/Tika)
- **Cross-published** for Scala 3.3.4 and 2.13.17

### Added

- Unified CLI (`xlcr`) with automatic backend fallback and `--backend` selection
- HTTP server module with `/convert`, `/split`, `/info`, `/capabilities`, and `/health` endpoints
- LibreOffice backend (`core-libreoffice`) for open-source DOC/XLS/PPT/ODS to PDF conversion
- PDF to HTML conversion (Aspose) with flowing layout and embedded resources
- Bidirectional HTML to PowerPoint conversion (Aspose)
- PDF to PowerPoint conversion (direct and two-stage via HTML)
- `--strip-masters` flag for clean PowerPoint conversions without template/branding
- Parallel directory-to-directory processing with progress tracking
- Encrypted/restricted PDF handling (automatic restriction removal)
- Configurable failure modes for document splitters
- Universal chunk range support across all splitters
- Word document splitting with router pattern (heading, paragraph strategies)
- PowerPoint slide dimension preservation during splitting
- PDF page splitter memory optimization (OptimizedMemoryStream)
- Spark UDF configurable timeouts
- Text splitter performance optimizations
- `make install` / `make install-user` for easy CLI installation

### Changed

- **Build system**: Migrated from sbt to Mill
- **Architecture**: Compile-time transform discovery via Scala 3 macros (replacing runtime ServiceLoader registry)
- **Resource management**: All I/O converted to `scala.util.Using` pattern (eliminated resource leaks)
- **Priority system**: Aspose bridges set to HIGH priority, LibreOffice to DEFAULT

### Removed

- SpreadsheetLLM module (Excel compression for LLMs) - use the standalone [`xl`](https://github.com/TJC-LP/xl) library instead
- Opinionated Excel/PowerPoint JSON and Markdown models from core - use the standalone [`xl`](https://github.com/TJC-LP/xl) library instead

### Fixed

- Aspose PowerPoint null pointer errors during conversion
- I/O resource leaks across all modules
- Email attachment splitter handling of non-Multipart content
- PowerPoint slide dimension loss during splitting
- Word heading splitter overlapping content and empty headings

[0.2.0]: https://github.com/TJC-LP/xlcr/releases/tag/v0.2.0
[0.1.3]: https://github.com/TJC-LP/xlcr/releases/tag/v0.1.3
[0.1.1]: https://github.com/TJC-LP/xlcr/releases/tag/v0.1.1
[0.1.0]: https://github.com/TJC-LP/xlcr/releases/tag/v0.1.0
