# XLCR Documentation

This directory contains documentation for the XLCR (eXtensible Language Computation Runtime) project.

## Core Documentation

### Architecture and Design
- [Splitter Failure Modes](splitter-failure-modes.md) - Configurable error handling for document splitters
- [Splitter Failure Modes Implementation](design/splitter-failure-modes-implementation.md) - Detailed implementation design
- [Table Detection Enhancements](table-detection-enhancements.md) - Advanced table detection algorithms

### SpreadsheetLLM Module
- [SpreadsheetLLM Overview](spreadsheetllm.md) - Main documentation for the SpreadsheetLLM module
- [SpreadsheetLLM Paper](spreadsheetllm-paper.md) - Academic paper summary
- [SheetCompressor](sheetcompressor.md) - Core compression algorithm documentation
- [Structural Anchor (Original)](structural-anchor-orig.md) - Original Microsoft implementation notes
- [Scala Structural Anchor](scala-structural-anchor.md) - Scala implementation details
- [Aggregator](aggregator.md) - Data aggregation strategies

### Examples
- [Splitter Failure Modes Example](examples/splitter-failure-modes-example.scala) - Practical usage examples

### Development
- [Improvements](improvements.md) - Planned improvements and feature requests

## Quick Links

### For Users
- [Main README](../README.md) - Getting started and basic usage
- [Examples](examples/) - Code examples and usage patterns

### For Developers
- [Design Documents](design/) - Architecture and design decisions
- [CLAUDE.md](../CLAUDE.md) - AI assistant instructions and codebase overview

## Documentation Standards

When adding new documentation:

1. **Markdown Format**: Use GitHub-flavored markdown
2. **Code Examples**: Include practical, runnable examples
3. **Cross-References**: Link to related documents
4. **Versioning**: Note which version features were introduced
5. **Categories**: Place documents in appropriate subdirectories

## Contributing

To contribute documentation:

1. Follow the existing format and structure
2. Include code examples where applicable
3. Update this index when adding new documents
4. Ensure links are relative and working
5. Run spell check before submitting