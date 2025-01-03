package com.tjclp.xlcr
package parsers.tika

import models.Content
import types.MimeType

import org.apache.tika.sax.BodyContentHandler

import java.nio.file.Path
import scala.util.Try

/**
 * An implementation of TikaParser that uses BodyContentHandler
 * to produce plain text content. It inherits the common Tika
 * lifecycle and logic from the TikaParser trait.
 */
object StandardTikaParser extends TikaParser:
  /**
   * Extract content from the given input path, producing a
   * text/plain result by default. Output type is used here
   * mainly for high-level matching and logging.
   *
   * @param input The file path to parse
   * @return A Try[Content] containing the extracted data
   */
  override def extractContent(input: Path, output: Option[Path] = None): Try[Content] =
    val handler = createContentHandler()
    extractWithTika(input, handler, outputType)

  def outputType: MimeType = MimeType.TextPlain

  override protected def createContentHandler(): BodyContentHandler =
    new BodyContentHandler()