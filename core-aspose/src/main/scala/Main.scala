package com.tjclp.xlcr

import utils.aspose.AsposeLicense
import utils.aspose.AsposeLicense.Product
import com.tjclp.xlcr.Pipeline
import com.tjclp.xlcr.bridges.aspose.AsposeBridgeRegistry
import org.slf4j.LoggerFactory
import scopt.OParser

import scala.util.Try

/**
 * Entry point for the Aspose‑based conversion pipeline.
 *
 * ── Improvements over the old implementation ────────────────────────────────────
 *   • Leverages the new `AsposeLicense` API (env‑var + auto‑discovery aware).
 *   • DRY, iterable logic for per‑product license loading (`Map[Product, Option[String]]`).
 *   • No ad‑hoc loggers; reuse the top‑level logger.
 *   • Early exit on CLI parse failure handled by Scopt itself (no manual `System.exit`).
 */
object Main {

  private val logger = LoggerFactory.getLogger(getClass)
  
  /**
   * Attempt to interpret a string as either a known MIME type or a known file extension.
   */
  private def parseMimeOrExtension(str: String): Option[types.MimeType] = {
    // First try direct MIME type parse
    types.MimeType.fromString(str) match {
      case someMime@Some(_) => someMime
      case None =>
        // Attempt to interpret it as an extension
        // remove any leading dot, e.g. ".xlsx" -> "xlsx"
        val ext = if (str.startsWith(".")) str.substring(1).toLowerCase else str.toLowerCase

        // See if there's a matching FileType
        val maybeFt = types.FileType.fromExtension(ext)
        maybeFt.map(_.getMimeType)
    }
  }

  // ---------- CLI definition -----------------------------------------------------
  private val builder = OParser.builder[AsposeConfig]
  private val parser  = {
    import builder._
    OParser.sequence(
      programName("xlcr-aspose"),
      head("xlcr-aspose", "1.1"),

      opt[String]('i', "input") .required().valueName("<file|dir>")
        .action((x, c) => c.copy(input  = x))
        .text("Path to input file or directory"),

      opt[String]('o', "output").required().valueName("<file|dir>")
        .action((x, c) => c.copy(output = x))
        .text("Path to output file or directory"),

      opt[Boolean]('d', "diff")
        .action((x, c) => c.copy(diffMode = x))
        .text("Enable diff/merge mode if supported"),

      opt[Unit]("split")
        .action((_, c) => c.copy(splitMode = true))
        .text("Enable split mode (file-to-directory split)"),

      opt[String]("strategy")
        .action((x, c) => c.copy(splitStrategy = Some(x)))
        .text("Split strategy (used with --split): page (PDF), sheet (Excel), slide (PowerPoint), " +
          "attachment (emails), embedded (archives), heading (Word), paragraph, row, column, sentence"),

      opt[String]("type")
        .action((x, c) => c.copy(outputType = Some(x)))
        .text("Override output MIME type/extension for split chunks - can be MIME type (application/pdf) " +
          "or extension (pdf). Used with --split only."),
          
      // PDF to image conversion options
      opt[String]("format")
        .action((x, c) => c.copy(outputFormat = Some(x)))
        .text("Output format for PDF page splitting: pdf (default), png, or jpg"),
        
      opt[Int]("max-width")
        .action((x, c) => c.copy(maxImageWidth = x))
        .text("Maximum width in pixels for image output (default: 2000)"),
        
      opt[Int]("max-height")
        .action((x, c) => c.copy(maxImageHeight = x))
        .text("Maximum height in pixels for image output (default: 2000)"),
        
      opt[Long]("max-size")
        .action((x, c) => c.copy(maxImageSizeBytes = x))
        .text("Maximum size in bytes for image output (default: 5MB)"),
        
      opt[Int]("dpi")
        .action((x, c) => c.copy(imageDpi = x))
        .text("DPI for PDF rendering (default: 300)"),
        
      opt[Double]("quality")
        .action((x, c) => c.copy(jpegQuality = x.toFloat))
        .text("JPEG quality (0.0-1.0, default: 0.85)"),
        
      // Recursive extraction options  
      opt[Unit]("recursive")
        .action((_, c) => c.copy(recursiveExtraction = true))
        .text("Enable recursive extraction of archives (ZIP within ZIP). Used with --split and embedded strategy."),
        
      opt[Int]("max-recursion-depth")
        .action((x, c) => c.copy(maxRecursionDepth = x))
        .text("Maximum recursion depth for nested archives (default: 5). Used with --recursive."),

      // Optional per‑product license paths (overrides env / auto) -----------------
      opt[String]("licenseTotal").valueName("<path>")
        .action((p, c) => c.copy(licenseTotal = Some(p)))
        .text("Aspose.Total license (covers all products)"),
      opt[String]("licenseWords") .valueName("<path>") .action((p, c) => c.copy(licenseWords  = Some(p)))
        .text("Aspose.Words license path"),
      opt[String]("licenseCells") .valueName("<path>") .action((p, c) => c.copy(licenseCells  = Some(p)))
        .text("Aspose.Cells license path"),
      opt[String]("licenseEmail") .valueName("<path>") .action((p, c) => c.copy(licenseEmail  = Some(p)))
        .text("Aspose.Email license path"),
      opt[String]("licenseSlides").valueName("<path>") .action((p, c) => c.copy(licenseSlides = Some(p)))
        .text("Aspose.Slides license path"),
      opt[String]("licenseZip").valueName("<path>") .action((p, c) => c.copy(licenseZip = Some(p)))
        .text("Aspose.Zip license path")
    )
  }

  // ---------- main ---------------------------------------------------------------
  def main(args: Array[String]): Unit =
    OParser.parse(parser, args, AsposeConfig()) match {
      case Some(cfg) =>
        // Initialize Aspose licenses and registries
        applyLicenses(cfg)
        AsposeBridgeRegistry.registerAll()
        utils.aspose.AsposeSplitterRegistry.registerAll()

        // Handle split mode vs conversion mode
        if (cfg.splitMode) {
          logger.info(s"Starting split operation – cfg: $cfg")
          
          import java.nio.file.{Files, Paths}
          val inputPath = Paths.get(cfg.input)
          val outputPath = Paths.get(cfg.output)
          
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
          val splitStrategyOpt = cfg.splitStrategy.flatMap(utils.SplitStrategy.fromString)
          val outputMimeOpt = cfg.outputType.flatMap(parseMimeOrExtension)
          
          // Run the split operation
          Try(Pipeline.split(
            inputPath = cfg.input,
            outputDir = cfg.output,
            strategy = splitStrategyOpt,
            outputType = outputMimeOpt,
            recursive = cfg.recursiveExtraction,
            maxRecursionDepth = cfg.maxRecursionDepth,
            outputFormat = cfg.outputFormat,
            maxImageWidth = cfg.maxImageWidth,
            maxImageHeight = cfg.maxImageHeight,
            maxImageSizeBytes = cfg.maxImageSizeBytes,
            imageDpi = cfg.imageDpi,
            jpegQuality = cfg.jpegQuality
          )).recover { case ex =>
            logger.error("Split operation failed", ex)
            sys.exit(1)
          }
        } else {
          // Standard conversion mode
          logger.info(s"Starting conversion – cfg: $cfg")
          
          Try(Pipeline.run(cfg.input, cfg.output, cfg.diffMode))
            .recover { case ex =>
              logger.error("Pipeline failed", ex)
              sys.exit(1)
            }
        }
      case None => // Scopt already displayed help / error
    }

  // ---------- licensing ----------------------------------------------------------
  private def applyLicenses(cfg: AsposeConfig): Unit = {

    cfg.licenseTotal match {
      case Some(totalPath) =>
        logger.info(s"Loading Aspose.Total license → $totalPath")
        AsposeLicense.loadTotal(totalPath)
      case None =>
        val paths: Map[Product, Option[String]] = Map(
          AsposeLicense.Product.Words  -> cfg.licenseWords,
          AsposeLicense.Product.Cells  -> cfg.licenseCells,
          AsposeLicense.Product.Email  -> cfg.licenseEmail,
          AsposeLicense.Product.Slides -> cfg.licenseSlides,
          AsposeLicense.Product.Zip    -> cfg.licenseZip
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
  }
}
