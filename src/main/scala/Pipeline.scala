package com.tjclp.xlcr

import models.Content
import parsers.ParserMatcher
import utils.FileUtils

import org.slf4j.LoggerFactory

import java.nio.file.{Files, Paths}
import scala.util.{Failure, Success}

object Pipeline:
  private val logger = LoggerFactory.getLogger(getClass)

  def run(inputPath: String, outputPath: String): Unit =
    logger.info(s"Starting extraction process. Input: $inputPath, Output: $outputPath")

    val input = Paths.get(inputPath)
    val output = Paths.get(outputPath)

    if !Files.exists(input) then
      logger.error(s"Input file '$inputPath' does not exist.")
      sys.exit(1)

    // Detect input and output MIME types
    val inputMimeType = FileUtils.detectMimeType(input)
    val outputMimeType = FileUtils.detectMimeType(output)

    // Find appropriate parser
    ParserMatcher.findParser(inputMimeType, outputMimeType) match
      case Some(parser) =>
        parser.extractContent(input) match
          case Success(content) =>
            try
              Files.write(output, content.data)
              logger.info(s"Content successfully extracted and saved to '$outputPath'.")
              logger.info(s"Content type: ${content.contentType}")
              logger.debug("Metadata:")
              content.metadata.foreach:
                case (key, value) =>
                  logger.debug(s"  $key: $value")
            catch
              case e: Exception =>
                logger.error(s"Error writing to output file: ${e.getMessage}", e)
                sys.exit(1)

          case Failure(exception) =>
            logger.error(s"Error extracting content: ${exception.getMessage}", exception)
            sys.exit(1)

      case None =>
        logger.error(s"No parser found for input type $inputMimeType and output type $outputMimeType")
        sys.exit(1)

    logger.info("Extraction process completed successfully.")
