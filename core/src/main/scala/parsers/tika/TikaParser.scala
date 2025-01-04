package com.tjclp.xlcr
package parsers.tika

import models.FileContent
import models.tika.TikaModel
import parsers.Parser
import types.MimeType

import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.{AutoDetectParser, ParseContext}
import org.xml.sax.ContentHandler

import java.io.ByteArrayInputStream
import scala.util.{Try, Using}

/**
 * TikaParser is a base trait for parsing content using Apache Tika.
 *
 * I = Input MimeType
 * O = Output MimeType
 */
trait TikaParser[I <: MimeType, O <: MimeType] extends Parser[I, TikaModel[O]]:

  /**
   * Parse the input FileContent using Tika, returning a TikaModel of type O.
   *
   * @param input The input FileContent
   * @return TikaModel representing extracted text and metadata
   * @throws ParserError on failure
   */
  def parse(input: FileContent[I]): TikaModel[O] =
    Try {
      val parser = new AutoDetectParser()
      val metadata = new Metadata()
      val context = new ParseContext()

      Using.resource(new ByteArrayInputStream(input.data)) { stream =>
        parser.parse(stream, contentHandler, metadata, context)
        TikaModel[O](
          text = contentHandler.toString,
          metadata = parseMetadata(metadata)
        )
      }
    }.recover {
      case ex: Exception =>
        throw TikaParseError(
          s"Failed to parse ${input.mimeType}: ${ex.getMessage}",
          Some(ex)
        )
    }.get

  /**
   * Converts Tika Metadata into a Map[String, String]
   */
  protected def parseMetadata(meta: Metadata): Map[String, String] =
    meta.names().map(name => name -> meta.get(name)).toMap

  /**
   * The content handler to be used for parsing. Usually created in the concrete parser.
   */
  protected def contentHandler: ContentHandler