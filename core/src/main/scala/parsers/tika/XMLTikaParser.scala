package com.tjclp.xlcr
package parsers.tika

import models.Content
import bridges.tika.TikaXmlBridge
import models.FileContent
import types.MimeType
import types.MimeType.ApplicationXml

import java.nio.file.{Files, Path}
import scala.util.{Try, Failure, Success}

/**
 * XMLTikaParser now uses TikaXmlBridge internally
 * to parse the input file to an XML representation from Tika.
 */
object XMLTikaParser extends TikaParser:
  override def outputType: MimeType = ApplicationXml

  override def extractContent(input: Path, output: Option[Path]): Try[Content] =
    // Validate file existence
    if !Files.exists(input) then
      return Failure(new IllegalArgumentException(s"Input file does not exist: $input"))

    val bytesTry = Try(Files.readAllBytes(input))
    bytesTry.flatMap { data =>
      val fileContent = FileContent[MimeType](data, detectInputMime(input))
      val result =
        try TikaXmlBridge.convert(fileContent)
        catch {
          case ex: Throwable => return Failure(ex)
        }

      val content = Content(
        data = result.data,
        contentType = result.mimeType.mimeType,
        metadata = Map("Converter" -> "XMLTikaParser")
      )

      // Optionally write to output
      output.foreach { out =>
        Try(Files.write(out, content.data)) match
          case Failure(e) => return Failure(e)
          case _ => ()
      }

      Success(content)
    }

  private def detectInputMime(input: Path): MimeType =
    val inMime = com.tjclp.xlcr.utils.FileUtils.detectMimeType(input)
    inMime

  override def priority: Int = 1