package com.tjclp.xlcr

/**
 * Configuration for the SpreadsheetLLM module, which provides parameters
 * for controlling the compression process.
 *
 * @param input Input file or directory path
 * @param output Output file or directory path
 * @param diffMode Whether to perform diff mode (merge into existing files if supported)
 * @param anchorThreshold Number of neighbor rows/columns to keep around structural anchors
 * @param disableAnchorExtraction Disable anchor-based pruning, keeping full sheet content
 * @param disableFormatAggregation Disable format-based aggregation, keeping all values as-is
 * @param enableCoordinateCorrection Whether to enable the coordinate correction for "off by 2" issue
 * @param coordinateCorrectionValue The specific value to add for coordinate correction (default: 2)
 * @param threads Number of threads to use for parallel processing
 * @param verbose Enable detailed logging output
 */
case class SpreadsheetLLMConfig(
  input: String = "",
  output: String = "",
  diffMode: Boolean = false,
  anchorThreshold: Int = 1,
  disableAnchorExtraction: Boolean = false,
  disableFormatAggregation: Boolean = false,
  enableCoordinateCorrection: Boolean = true,
  coordinateCorrectionValue: Int = 2,
  threads: Int = Runtime.getRuntime.availableProcessors(),
  verbose: Boolean = false
)