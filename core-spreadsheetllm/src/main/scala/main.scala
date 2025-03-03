package com.tjclp.xlcr

import org.slf4j.LoggerFactory
import scopt.OParser

/**
 * Main entry point for the SpreadsheetLLM CLI application.
 * This provides a command-line interface for running the SpreadsheetLLM compression
 * on Excel files without requiring the full XLCR pipeline.
 */
@main
def main(args: String*): Unit =
  val logger = LoggerFactory.getLogger(getClass)
  
  // Define command-line argument parser
  val builder = OParser.builder[SpreadsheetLLMConfig]
  val parser = {
    import builder._
    OParser.sequence(
      programName("xlcr-spreadsheetllm"),
      head("xlcr-spreadsheetllm", "1.0"),
      
      // Required input/output parameters
      opt[String]('i', "input")
        .required()
        .valueName("<fileOrDir>")
        .action((x, c) => c.copy(input = x))
        .text("Path to input Excel file"),
        
      opt[String]('o', "output")
        .required()
        .valueName("<fileOrDir>")
        .action((x, c) => c.copy(output = x))
        .text("Path to output JSON file"),
      
      // Compression control options
      opt[Int]("anchor-threshold")
        .valueName("<n>")
        .action((x, c) => c.copy(anchorThreshold = x))
        .text("Number of neighboring rows/cols to keep around anchors (default: 1)"),
        
      opt[Unit]("no-anchor")
        .action((_, c) => c.copy(disableAnchorExtraction = true))
        .text("Disable anchor-based pruning, keeping full sheet content"),
        
      opt[Unit]("no-format")
        .action((_, c) => c.copy(disableFormatAggregation = true))
        .text("Disable format-based aggregation, keeping all values as-is"),
        
      // Coordinate preservation option
      opt[Unit]("no-preserve-coordinates")
        .action((_, c) => c.copy(preserveOriginalCoordinates = false))
        .text("Disable preservation of original Excel coordinates"),
      
      // Table detection options
      opt[Unit]("no-table-detection")
        .action((_, c) => c.copy(enableTableDetection = false))
        .text("Disable multi-table detection in sheets"),
        
      opt[Int]("min-gap-size")
        .valueName("<n>")
        .action((x, c) => c.copy(minGapSize = x))
        .text("Minimum gap size for table detection (default: 3)"),

      opt[Unit]("no-enhanced-formulas")
        .action((_, c) => c.copy(enableEnhancedFormulas = false))
        .text("Disable enhanced formula relationship detection"),
      
      // Performance options
      opt[Int]("threads")
        .valueName("<n>")
        .action((x, c) => c.copy(threads = x))
        .text(s"Number of threads to use for parallel processing (default: ${Runtime.getRuntime.availableProcessors()})"),
        
      // Other options
      opt[Unit]("verbose")
        .action((_, c) => c.copy(verbose = true))
        .text("Enable verbose logging output"),
        
      opt[Unit]('d', "diff")
        .action((_, c) => c.copy(diffMode = true))
        .text("Enable diff/merge mode if supported"),
    )
  }
  
  // Parse the command-line arguments
  OParser.parse(parser, args.toArray, SpreadsheetLLMConfig()) match
    case Some(config) =>
      logger.info(s"Starting SpreadsheetLLM compression: $config")
      
      // Run the pipeline using the bridges
      import bridges.spreadsheetllm.SpreadsheetLLMBridgeRegistry
      // Initialize bridge registry with the configuration
      SpreadsheetLLMBridgeRegistry.registerAll(config)
      
      // Run the pipeline
      try
        Pipeline.run(config.input, config.output, config.diffMode)
      catch
        case ex: Exception =>
          logger.error("Error in SpreadsheetLLM pipeline", ex)
          System.exit(1)
      
    case None =>
      // Arguments parsing failed or help was requested
      System.exit(1)