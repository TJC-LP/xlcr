package com.tjclp.xlcr
package parsers.excel

import adapters.AdapterRegistry
import adapters.AdapterRegistry.implicits.*
import models.{Content, FileContent}
import types.MimeType
import types.MimeType.{ImageSvgXml, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet}

import org.slf4j.LoggerFactory

import java.nio.file.{Files, Path}
import scala.util.Try

object ExcelSvgParser extends ExcelParser {
  private val logger = LoggerFactory.getLogger(getClass)

  override def extractContent(input: Path, output: Option[Path]): Try[Content] = Try {
    val inputBytes = Files.readAllBytes(input)
    val fileContent = FileContent[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type](inputBytes, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)
    val result = AdapterRegistry.convert[
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
      ImageSvgXml.type
    ](fileContent)

    val bytes = result.data
    output.foreach(out => Files.write(out, bytes))

    Content(
      data = bytes,
      contentType = outputType.mimeType,
      metadata = Map("Converter" -> "ExcelSvgParser")
    )
  }

  override def outputType: MimeType = MimeType.ImageSvgXml

  override def supportedInputTypes: Set[MimeType] =
    Set(MimeType.ApplicationVndMsExcel, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)
}