package com.tjclp.xlcr

/**
 * Configuration for the SpreadsheetLLM module, which provides parameters for controlling the
 * compression process.
 *
 * @param input
 *   Input file or directory path
 * @param output
 *   Output file or directory path
 * @param diffMode
 *   Whether to perform diff mode (merge into existing files if supported)
 * @param anchorThreshold
 *   Number of neighbor rows/columns to keep around structural anchors
 * @param disableAnchorExtraction
 *   Disable anchor-based pruning, keeping full sheet content
 * @param disableFormatAggregation
 *   Disable format-based aggregation, keeping all values as-is
 * @param preserveOriginalCoordinates
 *   Whether to preserve original Excel coordinates (default: true)
 * @param enableTableDetection
 *   Whether to enable multi-table detection in sheets
 * @param enableEnhancedFormulas
 *   Whether to include enhanced formula relationships in output
 * @param minGapSize
 *   Minimum gap size to consider for table detection (in rows/columns)
 * @param threads
 *   Number of threads to use for parallel processing
 * @param verbose
 *   Enable detailed logging output
 * @param debugDataDetection
 *   Enable extra debug logging for date and number detection
 * @param eliminateOverlaps
 *   Whether to eliminate overlapping tables in detection
 * @param maxTableSize
 *   Maximum size of a table in cells (area) for preventing oversized tables
 * @param minTableDensity
 *   Minimum density (ratio of non-empty cells) for valid tables
 * @param emptyToleranceHorizontal
 *   Empty cell tolerance horizontally (default lowered from 3)
 * @param emptyToleranceVertical
 *   Empty cell tolerance vertically (default lowered from 3)
 * @param enableAnchorCheckInBFS
 *   Whether to use anchor locations during BFS to prevent over-expansion
 * @param enableAdvancedHeaderDetection
 *   Whether to enable advanced multi-level header detection
 * @param enableFormulaCorrelation
 *   Whether to enable formula relationship analysis for table detection
 * @param enableCohesionDetection
 *   Whether to enable cohesion region detection
 * @param enableSplitDetection
 *   Whether to enable detection of tables split by empty rows/columns
 */
case class SpreadsheetLLMConfig(
  input: String = "",
  output: String = "",
  diffMode: Boolean = false,
  anchorThreshold: Int = 1,
  disableAnchorExtraction: Boolean = false,
  disableFormatAggregation: Boolean = false,
  preserveOriginalCoordinates: Boolean = true,
  enableTableDetection: Boolean = true,
  enableEnhancedFormulas: Boolean = true,
  minGapSize: Int = 1,
  threads: Int = Runtime.getRuntime.availableProcessors(),
  verbose: Boolean = false,
  debugDataDetection: Boolean = false,
  eliminateOverlaps: Boolean = true,
  // New parameters for enhanced table detection
  maxTableSize: Int = 200,
  minTableDensity: Double = 0.15,
  emptyToleranceHorizontal: Int = 1,
  emptyToleranceVertical: Int = 1,
  enableAnchorCheckInBFS: Boolean = true,
  enableAdvancedHeaderDetection: Boolean = true,
  enableFormulaCorrelation: Boolean = true,
  enableCohesionDetection: Boolean = true,
  enableSplitDetection: Boolean = true
)
