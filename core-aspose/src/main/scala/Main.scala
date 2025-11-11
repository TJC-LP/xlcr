package com.tjclp.xlcr

import scala.util.Try

import scopt.OParser

import cli.{ AbstractMain, CommonCLI }
import utils.aspose.{ AsposeConfig, AsposeLicense }
import utils.aspose.AsposeLicense.Product

/**
 * Entry point for the Aspose-based conversion pipeline. Extends AbstractMain to leverage common CLI
 * parsing and execution logic while adding Aspose-specific license handling.
 */
object Main extends AbstractMain[AsposeConfig] {

  override protected def programName: String       = "xlcr-aspose"
  override protected def programVersion: String    = "0.1.0-RC14"
  override protected def emptyConfig: AsposeConfig = AsposeConfig()

  // Getter methods to extract fields from AsposeConfig
  override protected def getInput(config: AsposeConfig): String      = config.input
  override protected def getOutput(config: AsposeConfig): String     = config.output
  override protected def getDiffMode(config: AsposeConfig): Boolean  = config.diffMode
  override protected def getSplitMode(config: AsposeConfig): Boolean = config.splitMode
  override protected def getSplitStrategy(config: AsposeConfig): Option[String] =
    config.splitStrategy
  override protected def getOutputType(config: AsposeConfig): Option[String]  = config.outputType
  override protected def getMappings(config: AsposeConfig): Seq[String]       = config.mappings
  override protected def getFailureMode(config: AsposeConfig): Option[String] = config.failureMode
  override protected def getChunkRange(config: AsposeConfig): Option[String]  = config.chunkRange
  override protected def getThreads(config: AsposeConfig): Int                = config.threads
  override protected def getErrorMode(config: AsposeConfig): Option[String]   = config.errorMode
  override protected def getEnableProgress(config: AsposeConfig): Boolean = config.enableProgress
  override protected def getProgressIntervalMs(config: AsposeConfig): Long =
    config.progressIntervalMs
  override protected def getVerbose(config: AsposeConfig): Boolean = config.verbose
  override protected def getBackend(config: AsposeConfig): Option[String] = config.backend

  /**
   * Builds all CLI options including Aspose-specific ones
   */
  override protected def buildAllOptions: OParser[_, AsposeConfig] = {
    val builder = OParser.builder[AsposeConfig]
    import builder._

    OParser.sequence(
      // Common options
      opt[String]('i', "input")
        .required()
        .valueName("<fileOrDir>")
        .action((x, c) => c.copy(input = x))
        .text("Path to input file or directory"),
      opt[String]('o', "output")
        .required()
        .valueName("<fileOrDir>")
        .action((x, c) => c.copy(output = x))
        .text("Path to output file or directory"),
      opt[Boolean]('d', "diff")
        .action((x, c) => c.copy(diffMode = x))
        .text("Enable diff/merge mode if supported"),
      opt[Unit]("split")
        .action((_, c) => c.copy(splitMode = true))
        .text("Enable split mode (file-to-directory split)"),
      opt[String]("strategy")
        .valueName("<value>")
        .action((x, c) => c.copy(splitStrategy = Some(x)))
        .text(
          "Split strategy (used with --split): page (PDF), sheet (Excel), slide (PowerPoint), attachment (emails), embedded (archives), heading (Word), paragraph, row, column, sentence"
        ),
      opt[String]("type")
        .valueName("<mimeType>")
        .action((x, c) => c.copy(outputType = Some(x)))
        .text(
          "Override output MIME type/extension for split chunks - can be MIME type (application/pdf) or extension (pdf). Used with --split only."
        ),
      opt[String]("format")
        .valueName("<value>")
        .action((x, c) => c.copy(outputFormat = Some(x)))
        .text("Output format for PDF page splitting: pdf (default), png, or jpg"),
      opt[Int]("max-width")
        .valueName("<value>")
        .action((x, c) => c.copy(maxImageWidth = x))
        .text("Maximum width in pixels for image output (default: 2000)"),
      opt[Int]("max-height")
        .valueName("<value>")
        .action((x, c) => c.copy(maxImageHeight = x))
        .text("Maximum height in pixels for image output (default: 2000)"),
      opt[Long]("max-size")
        .valueName("<value>")
        .action((x, c) => c.copy(maxImageSizeBytes = x))
        .text("Maximum size in bytes for image output (default: 5MB)"),
      opt[Int]("dpi")
        .valueName("<value>")
        .action((x, c) => c.copy(imageDpi = x))
        .text("DPI for PDF rendering (default: 300)"),
      opt[Double]("quality")
        .valueName("<value>")
        .action((x, c) => c.copy(jpegQuality = x.toFloat))
        .text("JPEG quality (0.0-1.0, default: 0.85)"),
      opt[Unit]("recursive")
        .action((_, c) => c.copy(recursiveExtraction = true))
        .text(
          "Enable recursive extraction of archives (ZIP within ZIP). Used with --split and embedded strategy."
        ),
      opt[Int]("max-recursion-depth")
        .valueName("<value>")
        .action((x, c) => c.copy(maxRecursionDepth = x))
        .text("Maximum recursion depth for nested archives (default: 5). Used with --recursive."),
      opt[String]("failure-mode")
        .valueName("<mode>")
        .action((x, c) => c.copy(failureMode = Some(x)))
        .text(
          "Failure mode when a document cannot be split using the chosen strategy (throw, preserve, drop, tag). Default: preserve"
        ),
      opt[String]("chunk-range")
        .valueName("<range>")
        .action((x, c) => c.copy(chunkRange = Some(x)))
        .text("Extract only specific chunks (e.g. 0-4, 50, 95-). Zero-based indexing."),
      opt[Unit]("strip-masters")
        .action((_, c) => c.copy(stripMasters = true))
        .text(
          "Remove master slides/templates during PowerPoint conversions for cleaner output and template swapping workflows"
        ),

      // Directory-to-directory conversion options
      opt[Seq[String]]("mapping")
        .valueName("mimeOrExt1=mimeOrExt2,...")
        .action((xs, c) => c.copy(mappings = xs))
        .text(
          "Either MIME or extension to MIME/extension mapping, e.g. 'pdf=xml' or 'application/vnd.ms-excel=application/json'"
        ),

      // Parallel processing options
      opt[Int]("threads")
        .valueName("<N>")
        .action((x, c) => c.copy(threads = x))
        .text(
          s"Number of parallel threads for directory processing (default: 1 for serial, max: ${Runtime.getRuntime.availableProcessors()})"
        ),
      opt[String]("error-mode")
        .valueName("<mode>")
        .action((x, c) => c.copy(errorMode = Some(x)))
        .text(
          "Error handling mode for batch processing: fail-fast (stop on first error), continue (default, log and continue), skip (skip failed files)"
        ),
      opt[Unit]("no-progress")
        .action((_, c) => c.copy(enableProgress = false))
        .text("Disable progress reporting for batch operations"),
      opt[Long]("progress-interval")
        .valueName("<milliseconds>")
        .action((x, c) => c.copy(progressIntervalMs = x))
        .text("Progress update interval in milliseconds (default: 2000)"),
      opt[Unit]("verbose")
        .action((_, c) => c.copy(verbose = true))
        .text("Enable verbose logging for individual file operations"),
      // Backend selection
      opt[String]("backend")
        .valueName("<name>")
        .action((x, c) => c.copy(backend = Some(x)))
        .validate(x =>
          if (Seq("aspose", "libreoffice", "core", "tika").contains(x.toLowerCase))
            success
          else
            failure(s"Invalid backend '$x'. Must be one of: aspose, libreoffice, core, tika")
        )
        .text("Explicitly select backend: aspose (HIGH priority), libreoffice (DEFAULT), core (DEFAULT), tika (LOW)"),

      // Aspose-specific license options
      opt[String]("licenseTotal")
        .valueName("<path>")
        .action((p, c) => c.copy(licenseTotal = Some(p)))
        .text("Aspose.Total license (covers all products)"),
      opt[String]("licenseWords")
        .valueName("<path>")
        .action((p, c) => c.copy(licenseWords = Some(p)))
        .text("Aspose.Words license path"),
      opt[String]("licenseCells")
        .valueName("<path>")
        .action((p, c) => c.copy(licenseCells = Some(p)))
        .text("Aspose.Cells license path"),
      opt[String]("licenseEmail")
        .valueName("<path>")
        .action((p, c) => c.copy(licenseEmail = Some(p)))
        .text("Aspose.Email license path"),
      opt[String]("licenseSlides")
        .valueName("<path>")
        .action((p, c) => c.copy(licenseSlides = Some(p)))
        .text("Aspose.Slides license path"),
      opt[String]("licenseZip")
        .valueName("<path>")
        .action((p, c) => c.copy(licenseZip = Some(p)))
        .text("Aspose.Zip license path")
    )
  }

  /**
   * Initialize Aspose licenses and bridge context before processing
   */
  override protected def initialize(config: AsposeConfig): Unit = {
    applyLicenses(config)

    // Set bridge context for this thread
    utils.aspose.BridgeContext.set(
      utils.aspose.BridgeContextData(
        stripMasters = config.stripMasters
      )
    )
  }

  /**
   * Apply Aspose licenses from configuration
   */
  private def applyLicenses(cfg: AsposeConfig): Unit =
    cfg.licenseTotal match {
      case Some(totalPath) =>
        logger.info(s"Loading Aspose.Total license → $totalPath")
        AsposeLicense.loadTotal(totalPath)
      case None =>
        val paths: Map[Product, Option[String]] = Map(
          Product.Words  -> cfg.licenseWords,
          Product.Cells  -> cfg.licenseCells,
          Product.Email  -> cfg.licenseEmail,
          Product.Slides -> cfg.licenseSlides,
          Product.Zip    -> cfg.licenseZip
        )

        val anyExplicit = paths.exists(_._2.isDefined)

        if (anyExplicit) {
          paths.foreach {
            case (prod, Some(path)) =>
              logger.info(s"Loading Aspose.${prod.name} license → $path")
              AsposeLicense.loadProduct(prod, path)
            case _ => // nothing supplied
          }
        } else {
          logger.info("No explicit license paths provided – using env vars / auto‑discovery …")
          AsposeLicense.initializeIfNeeded()
        }
    }

  /**
   * Override executeConversion to propagate BridgeContext to worker threads in parallel mode
   */
  override protected def executeConversion(config: AsposeConfig): Unit = {
    import java.nio.file.{ Files, Paths }

    val inputPath  = getInput(config)
    val outputPath = getOutput(config)
    val input      = Paths.get(inputPath)
    val output     = Paths.get(outputPath)

    // Check if this is a directory-to-directory conversion
    if (Files.isDirectory(input) && (Files.isDirectory(output) || !Files.exists(output))) {
      val parsedMappings = CommonCLI.parseMimeMappings(getMappings(config))

      // Parse error mode
      val errorModeOpt = getErrorMode(config).flatMap(processing.ErrorMode.fromString)

      // Capture BridgeContext from main thread to propagate to worker threads
      // This ensures per-thread options like --strip-masters are honored in parallel mode
      val bridgeContextData = utils.aspose.BridgeContext.get()
      val contextWrapper: (
        () => ParallelDirectoryPipeline.ProcessingResult
      ) => ParallelDirectoryPipeline.ProcessingResult =
        (block: () => ParallelDirectoryPipeline.ProcessingResult) =>
          utils.aspose.BridgeContext.withContext(bridgeContextData) {
            block()
          }

      DirectoryPipeline.runDirectoryToDirectory(
        inputDir = inputPath,
        outputDir = outputPath,
        mimeMappings = parsedMappings,
        diffMode = getDiffMode(config),
        threads = getThreads(config),
        errorMode = errorModeOpt,
        enableProgress = getEnableProgress(config),
        progressIntervalMs = getProgressIntervalMs(config),
        verbose = getVerbose(config),
        contextWrapper = Some(contextWrapper)
      )
    } else {
      // Single file conversion - use parent implementation
      super.executeConversion(config)
    }
  }

  /**
   * Override executeSplit to handle Aspose-specific parameters
   */
  override protected def executeSplit(config: AsposeConfig): Unit = {
    import java.nio.file.{ Files, Paths }
    import splitters.SplitStrategy

    val inputPath  = Paths.get(config.input)
    val outputPath = Paths.get(config.output)

    // Verify input is a file
    if (Files.isDirectory(inputPath)) {
      logger.error("Split mode expects --input to be a file, not a directory.")
      sys.exit(1)
    }

    // Ensure output directory exists or can be created
    if (!Files.exists(outputPath)) {
      try Files.createDirectories(outputPath)
      catch {
        case ex: Exception =>
          logger.error(s"Failed to create output directory: ${ex.getMessage}")
          sys.exit(1)
      }
    } else if (!Files.isDirectory(outputPath)) {
      logger.error("--output must be a directory when using --split mode.")
      sys.exit(1)
    }

    // Parse strategy and output type
    val splitStrategyOpt = config.splitStrategy.flatMap(SplitStrategy.fromString)

    // Parse failure mode
    val failureModeOpt = config.failureMode.flatMap { mode =>
      mode.toLowerCase match {
        case "throw"    => Some(splitters.SplitFailureMode.ThrowException)
        case "preserve" => Some(splitters.SplitFailureMode.PreserveAsChunk)
        case "drop"     => Some(splitters.SplitFailureMode.DropDocument)
        case "tag"      => Some(splitters.SplitFailureMode.TagAndPreserve)
        case _ =>
          logger.warn(s"Unknown failure mode: $mode. Using default (preserve).")
          None
      }
    }

    // Parse chunk range
    val chunkRangeOpt = config.chunkRange.flatMap(CommonCLI.parseChunkRange)

    // Handle output type - combine --type and --format options
    val outputMimeOpt = CommonCLI.parseOutputMimeType(
      config.outputType,
      config.outputFormat
    )

    // Run the split operation
    Try(
      Pipeline.split(
        inputPath = config.input,
        outputDir = config.output,
        strategy = splitStrategyOpt,
        outputType = outputMimeOpt,
        recursive = config.recursiveExtraction,
        maxRecursionDepth = config.maxRecursionDepth,
        maxImageWidth = config.maxImageWidth,
        maxImageHeight = config.maxImageHeight,
        maxImageSizeBytes = config.maxImageSizeBytes,
        imageDpi = config.imageDpi,
        jpegQuality = config.jpegQuality,
        failureMode = failureModeOpt,
        failureContext = config.failureContext,
        chunkRange = chunkRangeOpt
      )
    ).recover { case ex =>
      logger.error(s"Split operation failed: ${ex.getMessage}")
      ex.printStackTrace()
      sys.exit(1)
    }
  }
}
