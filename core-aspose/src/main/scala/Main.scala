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
        logger.info(s"Starting conversion – cfg: $cfg")

        applyLicenses(cfg)
        AsposeBridgeRegistry.registerAll()
        utils.aspose.AsposeSplitterRegistry.registerAll()

        Try(Pipeline.run(cfg.input, cfg.output, cfg.diffMode))
          .recover { case ex =>
            logger.error("Pipeline failed", ex)
            sys.exit(1)
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
