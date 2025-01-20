package com.tjclp.xlcr
package parsers.tika

import parsers.Parser
import types.MimeType

import org.slf4j.LoggerFactory

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

