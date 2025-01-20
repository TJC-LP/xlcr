package com.tjclp.xlcr
package adapters

import bridges.Bridge
import bridges.excel._
import models.excel.SheetsData
import types.MimeType
import types.MimeType.{ApplicationJson, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, ImageSvgXml, TextMarkdown}
import models.FileContent
import scala.reflect.ClassTag

object AdapterRegistry {
  object implicits {
    // Excel -> JSON
    implicit val excelToJson: Bridge[
      SheetsData,
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
      ApplicationJson.type
    ] =
      SheetsDataExcelBridge.chain(SheetsDataJsonBridge)

    // Excel -> Markdown
    implicit val excelToMarkdown: Bridge[
      SheetsData,
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
      TextMarkdown.type
    ] =
      SheetsDataExcelBridge.chain(SheetsDataMarkdownBridge)

    // Excel -> SVG
    implicit val excelToSvg: Bridge[
      SheetsData,
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
      ImageSvgXml.type
    ] =
      SheetsDataExcelBridge.chain(SheetsDataSvgBridge)

    // Markdown -> Excel
    implicit val markdownToExcel: Bridge[
      SheetsData,
      TextMarkdown.type,
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
    ] =
      SheetsDataMarkdownBridge.chain(SheetsDataExcelBridge)

    // JSON -> Excel
    implicit val jsonToExcel: Bridge[
      SheetsData,
      ApplicationJson.type,
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
    ] =
      SheetsDataJsonBridge.chain(SheetsDataExcelBridge)
  }

  /**
   * Utility method to run a bridging conversion.
   */
  def convert[I <: MimeType, O <: MimeType](input: FileContent[I])(using bridge: Bridge[SheetsData, I, O]): FileContent[O] =
    bridge.convert(input)
}