package com.tjclp.xlcr
package parsers.tika

import org.apache.tika.sax.{BodyContentHandler, ToXMLContentHandler, WriteOutContentHandler}
import org.xml.sax.ContentHandler

/**
 * A helper object to create specialized Tika content handlers for various tasks.
 */
object TikaContentHandler {

  /**
   * Create a content handler suitable for text extraction.
   *
   * @param maxLength Maximum length of text content (default -1 = unlimited)
   * @return ContentHandler
   */
  def text(maxLength: Int = -1): ContentHandler =
    new BodyContentHandler(maxLength)

  /**
   * Create a content handler for extracting XML content.
   */
  def xml(maxLength: Int = -1): ContentHandler =
    new WriteOutContentHandler(new ToXMLContentHandler(), maxLength)
}