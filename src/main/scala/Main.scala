package com.tjclp.xlcr

import scala.util.Try

import scopt.OParser

import cli.{ AbstractMain, CommonCLI }
import utils.aspose.{ AsposeConfig, AsposeLicense }
import utils.aspose.AsposeLicense.Product

/**
 * Unified entry point for XLCR conversion pipeline.
 *
 * This CLI automatically aggregates all available backends:
 * - Aspose (HIGH priority) - Commercial, full-featured
 * - LibreOffice (DEFAULT priority) - Open-source fallback
 * - Core (DEFAULT priority) - Basic POI/PDFBox/Tika conversions
 *
 * Backend selection is automatic based on priority system.
 * The SPI (Service Provider Interface) system discovers all available bridges at runtime.
 */
object Main extends AbstractMain[AsposeConfig] {

  override protected def programName: String       = "xlcr"
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
   * Builds all CLI options including Aspose-specific ones.
   * These options are available even when using LibreOffice backend.
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
          "Split strategy (used with --split): page (PDF), sheet (Excel), slide (PowerPoint), " +
          "attachment (emails), embedded (archives), heading (Word), paragraph, row, column, sentence"
        ),
      opt[String]("type")
        .valueName("<mimeType>")
        .action((x, c) => c.copy(outputType = Some(x)))
        .text(
          "Override output MIME type/extension for split chunks - can be MIME type (application/pdf) " +
          "or extension (pdf). Used with --split only."
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
        .text("Enable recursive extraction of archives (ZIP within ZIP). Used with --split and embedded strategy."),
      opt[Int]("max-recursion-depth")
        .valueName("<value>")
        .action((x, c) => c.copy(maxRecursionDepth = x))
        .text("Maximum recursion depth for nested archives (default: 5). Used with --recursive."),
      opt[String]("failure-mode")
        .valueName("<mode>")
        .action((x, c) => c.copy(failureMode = Some(x)))
        .text("Failure mode when a document cannot be split using the chosen strategy (throw, preserve, drop, tag). Default: preserve"),
      opt[String]("chunk-range")
        .valueName("<range>")
        .action((x, c) => c.copy(chunkRange = Some(x)))
        .text("Extract only specific chunks (e.g. 0-4, 50, 95-). Zero-based indexing."),
      opt[Unit]("strip-masters")
        .action((_, c) => c.copy(stripMasters = true))
        .text("Remove master slides/templates during PowerPoint conversions for cleaner output and template swapping workflows"),
      opt[Seq[String]]("mapping")
        .valueName("mimeOrExt1=mimeOrExt2,...")
        .action((x, c) => c.copy(mappings = x))
        .text("Either MIME or extension to MIME/extension mapping, e.g. 'pdf=xml' or 'application/vnd.ms-excel=application/json'"),
      opt[Int]("threads")
        .valueName("<N>")
        .action((x, c) => c.copy(threads = x))
        .text("Number of parallel threads for directory processing (default: 1 for serial, max: 16)"),
      opt[String]("error-mode")
        .valueName("<mode>")
        .action((x, c) => c.copy(errorMode = Some(x)))
        .text("Error handling mode for batch processing: fail-fast (stop on first error), continue (default, log and continue), skip (skip failed files)"),
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
      // Aspose-specific license options (gracefully ignored if Aspose not used)
      opt[String]("licenseTotal")
        .valueName("<path>")
        .action((x, c) => c.copy(licenseTotal = Some(x)))
        .text("Aspose.Total license (covers all products)"),
      opt[String]("licenseWords")
        .valueName("<path>")
        .action((x, c) => c.copy(licenseWords = Some(x)))
        .text("Aspose.Words license path"),
      opt[String]("licenseCells")
        .valueName("<path>")
        .action((x, c) => c.copy(licenseCells = Some(x)))
        .text("Aspose.Cells license path"),
      opt[String]("licenseEmail")
        .valueName("<path>")
        .action((x, c) => c.copy(licenseEmail = Some(x)))
        .text("Aspose.Email license path"),
      opt[String]("licenseSlides")
        .valueName("<path>")
        .action((x, c) => c.copy(licenseSlides = Some(x)))
        .text("Aspose.Slides license path"),
      opt[String]("licenseZip")
        .valueName("<path>")
        .action((x, c) => c.copy(licenseZip = Some(x)))
        .text("Aspose.Zip license path")
    )
  }

  /**
   * Initialize all subsystems.
   * Handles Aspose license loading if available.
   */
  override protected def initialize(config: AsposeConfig): Unit = {
    // Initialize Aspose licenses if specified
    if (
      config.licenseTotal.isDefined ||
      config.licenseWords.isDefined ||
      config.licenseCells.isDefined ||
      config.licenseEmail.isDefined ||
      config.licenseSlides.isDefined ||
      config.licenseZip.isDefined
    ) {
      println("Explicit license paths provided – loading Aspose licenses …")

      config.licenseTotal.foreach(AsposeLicense.loadTotal)
      config.licenseWords.foreach(p => AsposeLicense.loadProduct(Product.Words, p))
      config.licenseCells.foreach(p => AsposeLicense.loadProduct(Product.Cells, p))
      config.licenseEmail.foreach(p => AsposeLicense.loadProduct(Product.Email, p))
      config.licenseSlides.foreach(p => AsposeLicense.loadProduct(Product.Slides, p))
      config.licenseZip.foreach(p => AsposeLicense.loadProduct(Product.Zip, p))
    } else {
      println("No explicit license paths provided – using env vars / auto‑discovery …")
      AsposeLicense.initializeIfNeeded()
    }
  }
}
