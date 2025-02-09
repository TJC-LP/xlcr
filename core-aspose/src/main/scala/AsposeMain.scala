package com.tjclp.xlcr

import utils.aspose.AsposeLicense
import bridges.aspose.AsposeBridgeRegistry

import org.slf4j.LoggerFactory
import scopt.OParser

/**
 * AsposeMain provides a standalone CLI for the core-aspose module,
 * allowing license specification for each Aspose product or a total license.
 * Usage example:
 * sbt "core-aspose/run --input input.docx --output output.pdf --licenseWords /path/to/Aspose.Words.lic"
 */
object AsposeMain:

  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit =
    val builder = OParser.builder[AsposeConfig]
    val parser = {
      import builder.*
      OParser.sequence(
        programName("xlcr-aspose"),
        head("xlcr-aspose", "1.0"),

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

        // License arguments:
        opt[String]("licenseWords")
          .valueName("<path>")
          .action((path, c) => c.copy(licenseWords = Some(path)))
          .text("Path to Aspose.Words license file"),

        opt[String]("licenseCells")
          .valueName("<path>")
          .action((path, c) => c.copy(licenseCells = Some(path)))
          .text("Path to Aspose.Cells license file"),

        opt[String]("licenseEmail")
          .valueName("<path>")
          .action((path, c) => c.copy(licenseEmail = Some(path)))
          .text("Path to Aspose.Email license file"),

        opt[String]("licenseSlides")
          .valueName("<path>")
          .action((path, c) => c.copy(licenseSlides = Some(path)))
          .text("Path to Aspose.Slides license file"),

        opt[String]("licenseTotal")
          .valueName("<path>")
          .action((path, c) => c.copy(licenseTotal = Some(path)))
          .text("Path to Aspose Total license (covers all products)")
      )
    }

    OParser.parse(parser, args, AsposeConfig()) match
      case Some(config) =>
        logger.info(s"Starting Aspose-based conversion. Config: $config")

        // Attempt to apply licenses if specified
        applyAsposeLicenses(config)
        AsposeBridgeRegistry.registerAll()

        // We can also initialize bridging logic like BridgeRegistryUpdateExample, if desired:
        // bridges.aspose.BridgeRegistryUpdateExample.init()

        // Then we can call the same pipeline approach from "core" to do the conversion.
        try
          Pipeline.run(config.input, config.output, config.diffMode)
        catch
          case ex: Exception =>
            logger.error("Error in Aspose-based pipeline", ex)
            System.exit(1)

      case None =>
        // Invalid arguments or help triggered
        System.exit(1)

  /**
   * Apply Aspose licenses according to the config.
   * If 'licenseTotal' is provided, we assume it covers all products.
   * Otherwise, load each product license individually if present.
   */
  private def applyAsposeLicenses(cfg: AsposeConfig): Unit =
    // If a total license is specified, attempt to load that for all products
    cfg.licenseTotal match
      case Some(licPath) =>
        logger.info(s"Loading Aspose.Total license from: $licPath")
        AsposeLicense.loadLicenseForAll(licPath)
      case None =>
        // Otherwise load each license individually if present
        cfg.licenseWords.foreach { lw =>
          logger.info(s"Loading Aspose.Words license from: $lw")
          AsposeLicense.loadWordsLicense(lw)
        }
        cfg.licenseCells.foreach { lc =>
          logger.info(s"Loading Aspose.Cells license from: $lc")
          AsposeLicense.loadCellsLicense(lc)
        }
        cfg.licenseEmail.foreach { le =>
          logger.info(s"Loading Aspose.Email license from: $le")
          AsposeLicense.loadEmailLicense(le)
        }
        cfg.licenseSlides.foreach { ls =>
          logger.info(s"Loading Aspose.Slides license from: $ls")
          AsposeLicense.loadSlidesLicense(ls)
        }
