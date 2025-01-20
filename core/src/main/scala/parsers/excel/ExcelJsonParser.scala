package com.tjclp.xlcr
package parsers.excel

import adapters.AdapterRegistry
import adapters.AdapterRegistry.implicits.*
import models.{Content, FileContent}
import types.MimeType
import types.MimeType.{ApplicationJson, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet}

import org.slf4j.LoggerFactory

import java.nio.file.{Files, Path}
import scala.util.Try

object ExcelJsonParser extends ExcelParser {
  private val logger = LoggerFactory.getLogger(getClass)

  override def extractContent(input: Path, output: Option[Path]): Try[Content] = Try {
    val inputBytes = Files.readAllBytes(input)
    val fileContent = FileContent[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type](inputBytes, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)
    val result = AdapterRegistry.convert[
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
      ApplicationJson.type
    ](fileContent)

    val bytes = result.data
    output.foreach(out => Files.write(out, bytes))

    Content(
      data = bytes,
      contentType = outputType.mimeType,
      metadata = Map("Converter" -> "ExcelJsonParser")
    )
  }

  override def outputType: MimeType = MimeType.ApplicationJson
}