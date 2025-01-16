package com.tjclp.xlcr
package adapters

import bridges.Bridge
import bridges.excel.*
import models.excel.SheetsData
import types.MimeType
import types.MimeType.{ApplicationJson, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, ImageSvgXml, TextMarkdown}
import models.FileContent
import scala.reflect.ClassTag

object AdapterRegistry {
  object implicits {

    // Existing Excel -> JSON bridging
    implicit val excelToJson: Bridge[
      SheetsData,
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
      ApplicationJson.type
    ] = ExcelBridge.chain[ApplicationJson.type](ApplicationJsonOutputBridge)

    // We rename the "ExcelJsonOutputBridge" in code to "ApplicationJsonOutputBridge" for clarity:
    // so let's keep it the same as we had: "ExcelJsonOutputBridge"
    // We'll keep the existing name to avoid confusion.
    private lazy val ApplicationJsonOutputBridge = ExcelJsonOutputBridge

    // Additional bridging for Excel -> Markdown
    implicit val excelToMarkdown: Bridge[
      SheetsData,
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
      TextMarkdown.type
    ] = ExcelBridge.chain[TextMarkdown.type](ExcelMarkdownOutputBridge)

    // Additional bridging for Excel -> SVG
    implicit val excelToSvg: Bridge[
      SheetsData,
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
      ImageSvgXml.type
    ] = ExcelBridge.chain[ImageSvgXml.type](ExcelSvgOutputBridge)

    // Markdown -> Excel bridging
    implicit val markdownToExcel: Bridge[
      SheetsData,
      TextMarkdown.type,
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
    ] = MarkdownExcelInputBridge

    // JSON -> Excel bridging
    implicit val jsonToExcel: Bridge[
      SheetsData,
      ApplicationJson.type,
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
    ] = JsonExcelInputBridge
  }

  def convert[I <: MimeType, O <: MimeType](input: FileContent[I])(using bridge: Bridge[SheetsData, I, O]): FileContent[O] =
    bridge.convert(input)
}