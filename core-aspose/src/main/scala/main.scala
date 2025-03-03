package com.tjclp.xlcr

import utils.aspose.AsposeLicense
import bridges.aspose.AsposeBridgeRegistry

import org.slf4j.LoggerFactory
import scopt.OParser

@main
def main(args: String*): Unit =
  val logger = LoggerFactory.getLogger(getClass)
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
        .text("Path to Aspose.Words license file (optional, auto-detected if not specified)"),

      opt[String]("licenseCells")
        .valueName("<path>")
        .action((path, c) => c.copy(licenseCells = Some(path)))
        .text("Path to Aspose.Cells license file (optional, auto-detected if not specified)"),

      opt[String]("licenseEmail")
        .valueName("<path>")
        .action((path, c) => c.copy(licenseEmail = Some(path)))
        .text("Path to Aspose.Email license file (optional, auto-detected if not specified)"),

      opt[String]("licenseSlides")
        .valueName("<path>")
        .action((path, c) => c.copy(licenseSlides = Some(path)))
        .text("Path to Aspose.Slides license file (optional, auto-detected if not specified)"),

      opt[String]("licenseTotal")
        .valueName("<path>")
        .action((path, c) => c.copy(licenseTotal = Some(path)))
        .text("Path to Aspose Total license (covers all products, optional, auto-detected if not specified)")
    )
  }

  OParser.parse(parser, args.toArray, AsposeConfig()) match
    case Some(config) =>
      logger.info(s"Starting Aspose-based conversion. Config: $config")

      // Apply licenses if specified
      applyAsposeLicenses(config)
      AsposeBridgeRegistry.registerAll()

      // Run the pipeline
      try
        Pipeline.run(config.input, config.output, config.diffMode)
      catch
        case ex: Exception =>
          logger.error("Error in Aspose-based pipeline", ex)
          System.exit(1)

    case None =>
      // Invalid arguments or help triggered
      System.exit(1)

/** Apply Aspose licenses according to the config. */
private def applyAsposeLicenses(cfg: AsposeConfig): Unit =
  val logger = LoggerFactory.getLogger(getClass)
  
  // First try explicit paths from command line args
  val explicitPathsProvided = cfg.licenseTotal.isDefined || 
                             cfg.licenseWords.isDefined || 
                             cfg.licenseCells.isDefined || 
                             cfg.licenseEmail.isDefined || 
                             cfg.licenseSlides.isDefined
  
  if (explicitPathsProvided) {
    // If a total license is specified, load that for all products
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
  } else {
    // No explicit paths provided, try auto-detection
    logger.info("No license paths specified, attempting auto-detection...")
    AsposeLicense.initializeIfNeeded()
  }