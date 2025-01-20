package com.tjclp.xlcr
package parsers.excel

import adapters.AdapterRegistry
import adapters.AdapterRegistry.implicits.*
import models.{Content, FileContent}
import types.MimeType
import types.MimeType.{ApplicationJson, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet}

import java.nio.file.{Files, Path}
import scala.util.Try

object JsonToExcelParser extends ExcelParser {
  override def extractContent(input: Path, output: Option[Path]): Try[Content] = Try {
    val inputBytes = Files.readAllBytes(input)
    val fileContent = FileContent[ApplicationJson.type](inputBytes, ApplicationJson)
    val result = AdapterRegistry.convert[
      ApplicationJson.type,
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
    ](fileContent)

    val bytes = result.data
    output.foreach(out => Files.write(out, bytes))

    Content(
      data = bytes,
      contentType = outputType.mimeType,
      metadata = Map("Converter" -> "JsonToExcelParser")
    )
  }

  override def outputType: MimeType = ApplicationVndOpenXmlFormatsSpreadsheetmlSheet

  override def supportedInputTypes: Set[MimeType] = Set(
    MimeType.ApplicationJson
  )

  override def supportsDiffMode: Boolean = true
}