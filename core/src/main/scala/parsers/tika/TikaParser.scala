package com.tjclp.xlcr
package parsers.tika

import models.Content
import parsers.Parser
import types.MimeType

import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.{AutoDetectParser, ParseContext}
import org.slf4j.LoggerFactory
import org.xml.sax.ContentHandler

import java.nio.file.{Files, Path}
import scala.util.control.NonFatal
import scala.util.{Try, Using}

/**
 * A trait that extends the base Parser interface to provide
 * Apache Tika-specific initialization and parsing logic.
 * Implementations must provide their own ContentHandler,
 * but can rely on the common extractWithTika method for
 * resource management, error handling, and metadata extraction.
 */
trait TikaParser extends Parser:

  private val logger = LoggerFactory.getLogger(getClass)

  // Add the output type that will be provided by implementing classes
  def outputType: MimeType

  // Implement the extractContent method required by Parser
  def extractContent(input: Path): Try[Content] =
    val handler = createContentHandler()
    extractWithTika(input, handler, outputType)

  /**
   * A protected helper method that orchestrates the Tika parsing
   * lifecycle with error handling, resource management, and
   * metadata extraction.
   *
   * @param input      The input file path.
   * @param handler    The Tika ContentHandler to use for parsing.
   * @param outputType The output extraction type
   * @return A Try[Content] containing extracted data and metadata.
   */
  protected def extractWithTika(input: Path, handler: ContentHandler, outputType: MimeType): Try[Content] =
    Try:
      if !Files.exists(input) then
        throw new IllegalArgumentException(s"Input file does not exist: $input")

      val parser = new AutoDetectParser()
      val metadata = new Metadata()
      val parseContext = new ParseContext()

      // Attempt resource-safe parsing
      Using.resource(Files.newInputStream(input)) { stream =>
        // Perform parsing
        parser.parse(stream, handler, metadata, parseContext)
      }

      // Extract the raw byte array from the ContentHandler if applicable
      // (for example, text-based handlers store content as a string)
      // For plain text or XML we typically just convert the handler's
      // contents to bytes. Subclasses can parse the extracted string
      // from the handler as needed.
      val contentString = handler.toString
      val contentBytes = contentString.getBytes("UTF-8")

      // Gather metadata. Tika uses name-value pairs.
      val metaMap: Map[String, String] =
        metadata.names().map(name => name -> metadata.get(name)).toMap


      logger.debug(s"Extracted content type: $outputType")
      logger.debug(s"Extracted metadata: $metaMap")

      // Wrap in XLCR's Content case class
      Content(
        data = contentBytes,
        contentType = outputType.mimeType,
        metadata = metaMap
      )
    .recover:
      case NonFatal(e) =>
        logger.error(s"Failed to parse file with Tika: ${e.getMessage}", e)
        throw e

  def supportedInputTypes: Set[MimeType] = Set(
    MimeType.ApplicationMsWord,
    MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument,
    MimeType.ApplicationVndMsPowerpoint,
    MimeType.ApplicationVndMsExcel,
    MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
    MimeType.ApplicationPdf,
    MimeType.MessageRfc822,
    MimeType.TextPlain,
    MimeType.TextHtml,
    MimeType.ApplicationXml,
    MimeType.ApplicationJson
  )

  // Abstract method to be implemented by concrete classes
  protected def createContentHandler(): ContentHandler
