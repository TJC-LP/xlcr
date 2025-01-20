package com.tjclp.xlcr
package parsers.tika

import bridges.tika.TikaPlainTextBridge
import models.{Content, FileContent}
import types.MimeType
import types.MimeType.TextPlain

import java.nio.file.{Files, Path}
import scala.util.{Failure, Success, Try}

/**
 * StandardTikaParser now uses TikaPlainTextBridge internally
 * to parse the input file to plain text.
 */
object StandardTikaParser extends TikaParser:
  override def outputType: MimeType = TextPlain

  override def extractContent(input: Path, output: Option[Path]): Try[Content] =
    // Validate file existence
    if !Files.exists(input) then
      return Failure(new IllegalArgumentException(s"Input file does not exist: $input"))

    // Read input bytes
    val bytesTry = Try(Files.readAllBytes(input))
    bytesTry.flatMap { data =>
      val fileContent = FileContent[MimeType](data, detectInputMime(input))
      val result =
        try TikaPlainTextBridge.convert(fileContent)
        catch {
          case ex: Throwable => return Failure(ex)
        }

      val content = Content(
        data = result.data,
        contentType = result.mimeType.mimeType,
        metadata = Map("Converter" -> "StandardTikaParser")
      )

      // Optionally write to output
      output.foreach { out =>
        Try(Files.write(out, content.data)) match
          case Failure(e) => return Failure(e)
          case _ => ()
      }

      Success(content)
    }

  /**
   * For demonstration, we guess the input MimeType from extension
   * or Tika detection. It's enough to pass MimeType to TikaBridge.
   */
  private def detectInputMime(input: Path): MimeType =
    val inMime = com.tjclp.xlcr.utils.FileUtils.detectMimeType(input)
    // Tika can handle nearly anything, so we just pass it along:
    inMime

  override def priority: Int = 1