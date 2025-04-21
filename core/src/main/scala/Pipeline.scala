package com.tjclp.xlcr

import bridges.{Bridge, BridgeRegistry, MergeableBridge}
import models.FileContent
import types.MimeType
import utils.FileUtils

import org.slf4j.LoggerFactory

import java.nio.file.{Files, Paths, StandardCopyOption}
import scala.util.{Failure, Success, Try}

object Pipeline {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Process the input file -> output file conversion pipeline.
   *
   * @param inputPath  Input file path
   * @param outputPath Output file path
   * @param diffMode   Whether to perform diff operations
   */
  def run(inputPath: String, outputPath: String, diffMode: Boolean = false): Unit = {
    logger.info(s"Starting extraction process. Input: $inputPath, Output: $outputPath, DiffMode: $diffMode")

    val input = Paths.get(inputPath)
    val output = Paths.get(outputPath)

    if (!Files.exists(input)) {
      logger.error(s"Input file '$inputPath' does not exist.")
      throw new InputFileNotFoundException(inputPath)
    }

    // Detect input and output MIME types
    val inputMimeType = FileUtils.detectMimeType(input)
    val outputMimeType = Try(FileUtils.detectMimeTypeFromExtension(output, strict = true)) match {
      case Failure(ex: UnknownExtensionException) =>
        logger.error(ex.getMessage)
        throw ex
      case Failure(other) =>
        logger.error(s"Error determining output MIME: ${other.getMessage}", other)
        throw other
      case Success(m) => m
    }

    // Create a local copy of input
    val localCopy = Files.createTempFile("xlcr_localcopy", input.getFileName.toString)
    try {
      Files.copy(input, localCopy, StandardCopyOption.REPLACE_EXISTING)

      val inputBytes = Files.readAllBytes(localCopy)
      val fileContent = FileContent(inputBytes, inputMimeType)

      // Perform the conversion
      val resultTry = if (diffMode && canMergeInPlace(inputMimeType, outputMimeType)) {
        doDiffConversion(fileContent, output, outputMimeType)
      } else {
        doRegularConversion(fileContent, outputMimeType)
      }

      resultTry match {
        case Failure(exception) =>
          logger.error(s"Error extracting content: ${exception.getMessage}", exception)
          throw new ContentExtractionException(s"Error extracting content: ${exception.getMessage}", exception)

        case Success(fileContentOut) =>
          FileUtils.writeBytes(output, fileContentOut.data) match {
            case Success(_) =>
              logger.info(s"Content successfully extracted and saved to '$outputPath'.")
              logger.info(s"Content type: ${fileContentOut.mimeType.mimeType}")
            case Failure(ex) =>
              throw new OutputWriteException(s"Failed to write output to '$outputPath'", ex)
          }
      }
    } finally {
      Files.deleteIfExists(localCopy)
    }
  }

  /**
   * Check if we can perform an in-place merge between these mime types
   */
  private def canMergeInPlace(inputMime: MimeType, outputMime: MimeType): Boolean = {
    BridgeRegistry.supportsMerging(inputMime, outputMime)
  }

  /* =====================================================================
   * File‑to‑Directory split entry‑point
   * =================================================================== */

  /**
   * Split a single input document into multiple output files inside the
   * provided directory.
   *
   * @param inputPath  Path to the file that should be split.
   * @param outputDir  Target directory where individual chunks will be saved.
   * @param strategy   Optional user‑supplied split strategy (page, sheet, …).
   * @param outputType Optional MIME type override for the produced chunks.
   */
  def split(
             inputPath: String,
             outputDir: String,
             strategy: Option[utils.SplitStrategy] = None,
             outputType: Option[MimeType] = None
           ): Unit = {

    logger.info(s"Starting split process. Input file: $inputPath, Output dir: $outputDir, " +
      s"Strategy: ${strategy.map(_.toString).getOrElse("default")}, OverrideType: ${outputType.map(_.mimeType).getOrElse("auto")}")

    val inPath = Paths.get(inputPath)
    val outDir = Paths.get(outputDir)

    if (!Files.exists(inPath) || Files.isDirectory(inPath)) {
      val msg = s"Input path must be an existing file: $inputPath"
      logger.error(msg)
      throw new InputFileNotFoundException(msg)
    }

    if (!Files.exists(outDir)) Files.createDirectories(outDir)
    else if (!Files.isDirectory(outDir)) {
      val msg = s"Output path must be a directory for split operation: $outputDir"
      logger.error(msg)
      throw new IllegalArgumentException(msg)
    }

    // Read file content and detect mime
    val fileContent = models.FileContent.fromPath[MimeType](inPath)

    // Decide on strategy (user override or default)
    val effStrategy: utils.SplitStrategy = strategy.getOrElse(defaultStrategyForMime(fileContent.mimeType))

    val splitCfg = utils.SplitConfig(effStrategy)

    val chunks = utils.DocumentSplitter.split(fileContent, splitCfg)

    if (chunks.size <= 1) {
      logger.warn("Split operation produced only one chunk – file may not be splittable using the chosen strategy.")
    }

    chunks.foreach { chunk =>
      val chunkMime: MimeType = outputType.getOrElse(chunk.content.mimeType)

      val ext: String = findExtensionForMime(chunkMime).getOrElse("dat")

      val baseLabel = sanitizeLabel(chunk.label)
      val indexPadded = f"${chunk.index + 1}%03d"
      val fileName = s"${indexPadded}_${baseLabel}.$ext"

      val outPath = outDir.resolve(fileName)

      utils.FileUtils.writeBytes(outPath, chunk.content.data) match {
        case Success(_) => logger.info(s"Wrote chunk #${chunk.index} to $outPath")
        case Failure(ex) =>
          logger.error(s"Failed to write chunk #${chunk.index} to $outPath: ${ex.getMessage}")
      }
    }
  }

  /** Default split strategy if the user hasn't specified one. */
  private def defaultStrategyForMime(mime: MimeType): utils.SplitStrategy = mime match {
    case MimeType.ApplicationPdf => utils.SplitStrategy.Page

    // Excel formats
    case MimeType.ApplicationVndMsExcel | MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet =>
      utils.SplitStrategy.Sheet

    // PowerPoint formats
    case MimeType.ApplicationVndMsPowerpoint | MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation =>
      utils.SplitStrategy.Slide

    // Archive / containers default to embedded entries
    case MimeType.ApplicationZip | MimeType.ApplicationGzip | MimeType.ApplicationSevenz |
         MimeType.ApplicationTar | MimeType.ApplicationBzip2 | MimeType.ApplicationXz =>
      utils.SplitStrategy.Embedded

    // Emails default to attachments
    case MimeType.MessageRfc822 | MimeType.ApplicationVndMsOutlook => utils.SplitStrategy.Attachment

    case _ => utils.SplitStrategy.Page // generic fallback
  }

  /** Map a MIME type to a known file extension, if possible. */
  private def findExtensionForMime(mime: MimeType): Option[String] = {
    types.FileType.values.find(_.getMimeType == mime).map(_.getExtension.extension)
  }

  /** Sanitize chunk label to obtain a safe filename component. */
  private def sanitizeLabel(label: String): String = {
    val replaced = label.replaceAll("[\\\\/:*?\"<>|]", "_") // Windows‑invalid chars
    // collapse whitespace
    replaced.trim.replaceAll("\\s+", "_")
  }

  /**
   * Perform a normal single-step conversion via convertDynamic.
   */
  private def doRegularConversion(
                                   input: FileContent[MimeType],
                                   outMime: MimeType
                                 ): Try[FileContent[MimeType]] = {
    Try {
      BridgeRegistry.findBridge(input.mimeType, outMime) match {
        case Some(bridge: Bridge[_, i, o]) =>
          bridge.convert(input.asInstanceOf[FileContent[i]])
            .asInstanceOf[FileContent[MimeType]]
        case None =>
          throw new UnsupportedConversionException(input.mimeType.mimeType, outMime.mimeType)
      }
    }
  }

  /**
   * Perform a diff-based conversion by merging source into existing output.
   * We assume the model is Mergeable, such as SheetsData in JSON -> Excel scenario.
   */
  private def doDiffConversion(
                                incoming: FileContent[MimeType],
                                existingPath: java.nio.file.Path,
                                outputMime: MimeType
                              ): Try[FileContent[MimeType]] = Try {
    if (!Files.exists(existingPath)) {
      throw new InputFileNotFoundException(existingPath.toString)
    }

    val existingContent = FileContent.fromPath[MimeType](existingPath)

    BridgeRegistry.findMergeableBridge(incoming.mimeType, outputMime) match {
      case Some(bridge: MergeableBridge[_, i, o]) =>
        bridge.convertWithDiff(
          incoming.asInstanceOf[FileContent[i]],
          existingContent.asInstanceOf[FileContent[o]]
        ).asInstanceOf[FileContent[MimeType]]
      case None =>
        throw new UnsupportedConversionException(incoming.mimeType.mimeType, outputMime.mimeType)
    }
  }
}
