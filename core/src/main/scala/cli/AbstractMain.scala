package com.tjclp.xlcr
package cli

import java.nio.file.{ Files, Paths }

import scala.util.Try

import org.slf4j.LoggerFactory
import scopt.OParser

import splitters.{ SplitFailureMode, SplitStrategy }

/**
 * Abstract base class for Main entry points across different XLCR modules. Provides common CLI
 * parsing logic and execution flow while allowing module-specific customization through abstract
 * methods.
 *
 * @tparam C
 *   The configuration type used by the module
 */
abstract class AbstractMain[C] {

  protected val logger = LoggerFactory.getLogger(getClass)

  /**
   * The program name displayed in CLI help
   */
  protected def programName: String

  /**
   * The program version displayed in CLI help
   */
  protected def programVersion: String

  /**
   * Creates an empty configuration instance
   */
  protected def emptyConfig: C

  // Abstract methods to extract common fields from the configuration
  protected def getInput(config: C): String
  protected def getOutput(config: C): String
  protected def getDiffMode(config: C): Boolean
  protected def getSplitMode(config: C): Boolean
  protected def getSplitStrategy(config: C): Option[String]
  protected def getOutputType(config: C): Option[String]
  protected def getMappings(config: C): Seq[String]
  protected def getFailureMode(config: C): Option[String]
  protected def getChunkRange(config: C): Option[String]

  /**
   * Builds the complete parser by combining base options with module-specific options
   */
  protected def buildParser: OParser[_, C] = {
    val builder = OParser.builder[C]

    OParser.sequence(
      builder.programName(programName),
      builder.head(programName, programVersion),
      buildAllOptions
    )
  }

  /**
   * Builds all CLI options - override this to customize the full option set
   */
  protected def buildAllOptions: OParser[_, C]

  /**
   * Performs module-specific initialization (e.g., loading licenses)
   */
  protected def initialize(config: C): Unit = {}

  /**
   * Determines if the configuration represents a split operation
   */
  protected def isSplitMode(config: C): Boolean =
    getSplitMode(config) || getSplitStrategy(config).isDefined

  /**
   * Executes the split operation
   */
  protected def executeSplit(config: C): Unit = {
    val splitStrategyOpt = getSplitStrategy(config).flatMap(SplitStrategy.fromString)
    val failureModeOpt   = parseFailureMode(getFailureMode(config))
    val chunkRangeOpt    = getChunkRange(config).flatMap(CommonCLI.parseChunkRange)
    val outputMimeOpt    = CommonCLI.parseOutputMimeType(getOutputType(config), None)

    Try(
      Pipeline.split(
        inputPath = getInput(config),
        outputDir = getOutput(config),
        strategy = splitStrategyOpt,
        outputType = outputMimeOpt,
        failureMode = failureModeOpt,
        chunkRange = chunkRangeOpt
      )
    ).recover { case ex =>
      logger.error(s"Split operation failed: ${ex.getMessage}")
      ex.printStackTrace()
      sys.exit(1)
    }
  }

  /**
   * Executes the conversion operation (file to file or directory to directory)
   */
  protected def executeConversion(config: C): Unit = {
    val inputPath  = getInput(config)
    val outputPath = getOutput(config)
    val input      = Paths.get(inputPath)
    val output     = Paths.get(outputPath)

    // Check if this is a directory-to-directory conversion
    if (Files.isDirectory(input) && (Files.isDirectory(output) || !Files.exists(output))) {
      val parsedMappings = CommonCLI.parseMimeMappings(getMappings(config))
      DirectoryPipeline.runDirectoryToDirectory(
        inputDir = inputPath,
        outputDir = outputPath,
        mimeMappings = parsedMappings,
        diffMode = getDiffMode(config)
      )
    } else {
      // Single file conversion
      Try(
        Pipeline.run(
          inputPath = inputPath,
          outputPath = outputPath,
          diffMode = getDiffMode(config)
        )
      ).recover { case ex =>
        logger.error(s"Conversion failed: ${ex.getMessage}")
        ex.printStackTrace()
        sys.exit(1)
      }
    }
  }

  /**
   * Parses failure mode string to enum
   */
  private def parseFailureMode(mode: Option[String]): Option[SplitFailureMode] =
    mode.flatMap { m =>
      m.toLowerCase match {
        case "throw"    => Some(SplitFailureMode.ThrowException)
        case "preserve" => Some(SplitFailureMode.PreserveAsChunk)
        case "drop"     => Some(SplitFailureMode.DropDocument)
        case "tag"      => Some(SplitFailureMode.TagAndPreserve)
        case _ =>
          logger.warn(s"Unknown failure mode: $m. Using default (preserve).")
          None
      }
    }

  /**
   * Main entry point
   */
  def main(args: Array[String]): Unit = {
    val parser = buildParser

    OParser.parse(parser, args, emptyConfig) match {
      case Some(config) =>
        // Perform module-specific initialization
        initialize(config)

        // Execute the appropriate operation
        if (isSplitMode(config)) {
          validateSplitMode(config)
          executeSplit(config)
        } else if (getDiffMode(config)) {
          executeDiff(config)
        } else {
          executeConversion(config)
        }

      case None =>
        // Parser already printed help/error
        sys.exit(1)
    }
  }

  /**
   * Validates split mode configuration
   */
  private def validateSplitMode(config: C): Unit = {
    val inputPath  = Paths.get(getInput(config))
    val outputPath = Paths.get(getOutput(config))

    // Verify input is a file
    if (!Files.isRegularFile(inputPath)) {
      logger.error("Split mode expects input to be a file, not a directory.")
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
      logger.error("Output must be a directory when splitting files.")
      sys.exit(1)
    }
  }

  /**
   * Executes diff operation - override in modules that support it
   */
  protected def executeDiff(config: C): Unit = {
    logger.error("Diff mode is not supported in this module.")
    sys.exit(1)
  }
}
