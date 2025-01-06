package com.tjclp.xlcr
package parsers.excel

import adapters.AdapterRegistry
import adapters.AdapterRegistry.implicits._
import models.Content
import types.MimeType
import utils.FileUtils

import org.slf4j.LoggerFactory

import java.nio.file.{Files, Path}
import scala.util.Try

/**
 * ExcelJsonParser delegates to bridging approach (AdapterRegistry)
 * specifically for Excel -> JSON.
 */
object ExcelJsonParser extends ExcelParser {
  private val logger = LoggerFactory.getLogger(getClass)

  override def extractContent(input: Path, output: Option[Path]): Try[Content] = Try {
    val inputBytes = Files.readAllBytes(input)
    val fromMime = FileUtils.detectMimeType(input)
    val toMime = MimeType.ApplicationJson

    // For now, we only handle xlsx -> json via the bridging approach
    // If fromMime doesn't match, we'll bail out
    fromMime match {
      case MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet =>
        // Attempt the bridging approach
        val conversionResult =
          AdapterRegistry.convert[
            MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
            MimeType.ApplicationJson.type
          ](
            inputBytes,
            MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
            MimeType.ApplicationJson
          )

        conversionResult match {
          case Right(convertedBytes) =>
            output.foreach(out => Files.write(out, convertedBytes))
            Content(
              data = convertedBytes,
              contentType = toMime.mimeType,
              metadata = Map("Delegated" -> "ExcelJsonParserBridge")
            )
          case Left(errorMsg) =>
            throw new RuntimeException(errorMsg)
        }

      case other =>
        throw new RuntimeException(
          s"ExcelJsonParser only supports .xlsx input, but got $other"
        )
    }
  }

  override def outputType: MimeType = MimeType.ApplicationJson
}