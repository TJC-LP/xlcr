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

/**
 * ExcelSvgParser loads an Excel workbook, converts it to SheetData,
 * and then generates an SVG representation.
 */
object ExcelSvgParser extends ExcelParser:

  private val logger = LoggerFactory.getLogger(getClass)

  override def extractContent(input: Path, output: Option[Path] = None): Try[Content] =
    Try {
      val inputBytes = Files.readAllBytes(input)
      val fromMime: ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type = ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
      val toMime = ImageSvgXml

      // For now, we only handle xlsx -> svg via the bridging approach
      val fileContent = FileContent[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type](inputBytes, fromMime)
      val result = AdapterRegistry.convert[
        ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type, ImageSvgXml.type
      ](fileContent)

      val bytes = result.data
      output.foreach(out => Files.write(out, bytes))
      Content(
        data = bytes,
        contentType = toMime.mimeType,
        metadata = Map("Delegated" -> "ExcelSvgOutputBridge")
      )
    }

  override def outputType: MimeType = MimeType.ImageSvgXml

  override def supportedInputTypes: Set[MimeType] =
    Set(MimeType.ApplicationVndMsExcel, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)