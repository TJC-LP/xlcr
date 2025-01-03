package com.tjclp.xlcr

import models.Content
import parsers.ParserMatcher
import utils.FileUtils

import org.slf4j.LoggerFactory

import java.nio.file.{Files, Paths, StandardCopyOption}
import scala.util.{Failure, Success}

object Pipeline:
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Process the input file -> output file conversion pipeline.
   *
   * @param inputPath Input file path
   * @param outputPath Output file path
   * @param diffMode Whether to perform diff against existing output
   * @return Extracted content
   */
  def run(inputPath: String, outputPath: String, diffMode: Boolean = false): Content =
    logger.info(s"Starting extraction process. Input: $inputPath, Output: $outputPath, DiffMode: $diffMode")

    val input = Paths.get(inputPath)
    val output = Paths.get(outputPath)

    if !Files.exists(input) then
      logger.error(s"Input file '$inputPath' does not exist.")
      throw InputFileNotFoundException(inputPath)

    // Detect input and output MIME types
    val inputMimeType = FileUtils.detectMimeType(input)
    val outputMimeType = FileUtils.detectMimeTypeFromExtension(output, strict = true)

    // Find appropriate parser
    val parser = ParserMatcher.findParser(inputMimeType, outputMimeType).getOrElse {
      logger.error(s"No parser found for input type $inputMimeType and output type $outputMimeType")
      throw ParserNotFoundException(inputMimeType.toString, outputMimeType.toString)
    }

    // Validate diff mode is supported if requested
    if diffMode && !parser.supportsDiffMode then
      logger.warn(s"Diff mode requested but not supported by parser ${parser.getClass.getSimpleName}")

    // Create a local copy to avoid concurrency issues
    val localCopy = Files.createTempFile("xlcr_localcopy", input.getFileName.toString)
    try
      // Copy input to local temp
      Files.copy(input, localCopy, StandardCopyOption.REPLACE_EXISTING)

      // Extract and save content with optional diff mode
      val outputArg = if diffMode && parser.supportsDiffMode then Some(output) else None
      parser.extractContent(localCopy, outputArg) match
        case Success(content) =>
          try
            Files.write(output, content.data)
            logger.info(s"Content successfully extracted and saved to '$outputPath'.")
            logger.info(s"Content type: ${content.contentType}")
            logger.debug("Metadata:")
            content.metadata.foreach((key, value) => logger.debug(s"  $key: $value"))
            logger.info("Extraction process completed successfully.")
            content
          catch
            case e: Exception =>
              logger.error(s"Error writing to output file: ${e.getMessage}", e)
              throw OutputWriteException(s"Error writing to output file: ${e.getMessage}", e)

        case Failure(exception) =>
          logger.error(s"Error extracting content: ${exception.getMessage}", exception)
          throw ContentExtractionException(s"Error extracting content: ${exception.getMessage}", exception)

    finally
      Files.deleteIfExists(localCopy)