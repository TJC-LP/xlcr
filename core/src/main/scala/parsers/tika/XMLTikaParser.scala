package com.tjclp.xlcr
package parsers.tika

import models.Content
import types.MimeType

import org.apache.tika.sax.{ToXMLContentHandler, WriteOutContentHandler}
import org.xml.sax.ContentHandler

import java.nio.file.Path
import scala.util.Try

/**
 * An implementation of TikaParser that produces XML output
 * using ToXMLContentHandler. It inherits the common
 * Tika lifecycle and logic from the TikaParser trait.
 */
object XMLTikaParser extends TikaParser:
  private val WriteLimit = -1 // No write limit

  /**
   * Extract content from the given input path, producing an
   * XML-structured result. Output type is used here for
   * high-level matching and logging only.
   *
   * @param input The file path to parse
   * @return A Try[Content] containing the extracted data
   */
  override def extractContent(input: Path, output: Option[Path] = None): Try[Content] =
    extractWithTika(input, createContentHandler(), outputType)

  def outputType: MimeType = MimeType.ApplicationXml

  override protected def createContentHandler(): ContentHandler =
    val xmlHandler = new ToXMLContentHandler()
    new WriteOutContentHandler(xmlHandler, WriteLimit)
