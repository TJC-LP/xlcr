package com.tjclp.xlcr

import bridges.{Bridge, BridgeRegistry, MergeableBridge}
import models.FileContent
import types.MimeType
import utils.FileUtils

import org.slf4j.LoggerFactory

import java.nio.file.{Files, Paths, StandardCopyOption}
import scala.util.{Failure, Success, Try}

object Pipeline:
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Process the input file -> output file conversion pipeline.
   *
   * @param inputPath  Input file path
   * @param outputPath Output file path
   * @param diffMode   Whether to perform diff operations
   */
  def run(inputPath: String, outputPath: String, diffMode: Boolean = false): Unit =
    logger.info(s"Starting extraction process. Input: $inputPath, Output: $outputPath, DiffMode: $diffMode")

    val input = Paths.get(inputPath)
    val output = Paths.get(outputPath)

    if !Files.exists(input) then
      logger.error(s"Input file '$inputPath' does not exist.")
      throw InputFileNotFoundException(inputPath)

    // Detect input and output MIME types
    val inputMimeType = FileUtils.detectMimeType(input)
    val outputMimeType = Try(FileUtils.detectMimeTypeFromExtension(output, strict = true)) match
      case Failure(ex: UnknownExtensionException) =>
        logger.error(ex.getMessage)
        throw ex
      case Failure(other) =>
        logger.error(s"Error determining output MIME: ${other.getMessage}", other)
        throw other
      case Success(m) => m

    // Create a local copy of input
    val localCopy = Files.createTempFile("xlcr_localcopy", input.getFileName.toString)
    try
      Files.copy(input, localCopy, StandardCopyOption.REPLACE_EXISTING)

      val inputBytes = Files.readAllBytes(localCopy)
      val fileContent = FileContent(inputBytes, inputMimeType)

      // Perform the conversion
      val resultTry = if diffMode && canMergeInPlace(inputMimeType, outputMimeType) then
        doDiffConversion(fileContent, output, outputMimeType)
      else
        doRegularConversion(fileContent, outputMimeType)

      resultTry match
        case Failure(exception) =>
          logger.error(s"Error extracting content: ${exception.getMessage}", exception)
          throw ContentExtractionException(s"Error extracting content: ${exception.getMessage}", exception)

        case Success(fileContentOut) =>
          Files.write(output, fileContentOut.data)
          logger.info(s"Content successfully extracted and saved to '$outputPath'.")
          logger.info(s"Content type: ${fileContentOut.mimeType.mimeType}")
    finally
      Files.deleteIfExists(localCopy)

  /**
   * Check if we can perform an in-place merge between these mime types
   */
  private def canMergeInPlace(inputMime: MimeType, outputMime: MimeType): Boolean =
    BridgeRegistry.supportsMerging(inputMime, outputMime)

  /**
   * Perform a normal single-step conversion via convertDynamic.
   */
  private def doRegularConversion(
                                   input: FileContent[MimeType],
                                   outMime: MimeType
                                 ): Try[FileContent[MimeType]] =
    Try {
      BridgeRegistry.findBridge(input.mimeType, outMime) match
        case Some(bridge: Bridge[_, i, o]) =>
          bridge.convert(input.asInstanceOf[FileContent[i]])
            .asInstanceOf[FileContent[MimeType]]
        case None =>
          throw UnsupportedConversionException(input.mimeType.mimeType, outMime.mimeType)
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
    if !Files.exists(existingPath) then
      throw InputFileNotFoundException(existingPath.toString)

    val existingContent = FileContent.fromPath[MimeType](existingPath)

    BridgeRegistry.findMergeableBridge(incoming.mimeType, outputMime) match
      case Some(bridge: MergeableBridge[_, i, o]) =>
        bridge.merge(
          incoming.asInstanceOf[FileContent[i]],
          existingContent.asInstanceOf[FileContent[o]]
        ).asInstanceOf[FileContent[MimeType]]
      case None =>
        throw UnsupportedConversionException(incoming.mimeType.mimeType, outputMime.mimeType)
  }