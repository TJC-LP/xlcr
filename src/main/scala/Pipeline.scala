package com.tjclp.xlcr

import parser.FileParser
import types.OutputFormat

import java.nio.file.{Files, Paths}
import scala.util.{Failure, Success}
import org.slf4j.LoggerFactory

object Pipeline:
  private val logger = LoggerFactory.getLogger(getClass)

  def run(inputPath: String, outputPath: String): Unit =
    logger.info(s"Starting extraction process. Input: $inputPath, Output: $outputPath")

    val input = Paths.get(inputPath)
    val output = Paths.get(outputPath)

    if !Files.exists(input) then
      logger.error(s"Input file '$inputPath' does not exist.")
      sys.exit(1)

    val outputFormat = detectOutputFormat(outputPath)
    logger.info(s"Detected output format: $outputFormat")

    val enableXMLOutput = outputFormat == OutputFormat.XML

    FileParser.extractContent(input, enableXMLOutput) match
      case Success(tikaContent) =>
        try
          Files.write(output, tikaContent.content.getBytes)
          logger.info(s"Content successfully extracted and saved to '$outputPath'.")
          logger.info(s"Content type: ${tikaContent.contentType}")
          logger.debug("Metadata:")
          tikaContent.metadata.foreach { case (key, value) =>
            logger.debug(s"  $key: $value")
          }
        catch
          case e: Exception =>
            logger.error(s"Error writing to output file: ${e.getMessage}", e)
            sys.exit(1)

      case Failure(exception) =>
        logger.error(s"Error extracting content: ${exception.getMessage}", exception)
        sys.exit(1)

    logger.info("Extraction process completed successfully.")

  private def detectOutputFormat(outputPath: String): OutputFormat =
    val lowercasePath = outputPath.toLowerCase
    if lowercasePath.endsWith(".xml") then OutputFormat.XML
    else if lowercasePath.endsWith(".txt") then OutputFormat.TXT
    else
      logger.warn(s"Unrecognized output format for '$outputPath'. Defaulting to TXT.")
      OutputFormat.TXT
