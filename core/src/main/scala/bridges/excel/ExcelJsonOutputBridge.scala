package com.tjclp.xlcr
package bridges.excel

import bridges.OutputBridge
import models.Model
import models.excel.{SheetData, SheetsData}
import types.MimeType

import com.tjclp.xlcr

/**
 * ExcelJsonOutputBridge converts a list of SheetData into JSON bytes.
 */
object ExcelJsonOutputBridge extends OutputBridge[
  Model[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type],
  MimeType.ApplicationJson.type
] {
  override def outputMimeType: MimeType.ApplicationJson.type = MimeType.ApplicationJson
  override def modelType: SheetsData.type = SheetsData

  override def render(model: Model[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type]): Array[Byte] = {
    // Cast to SheetsData since we know this is the concrete type we're working with
    val sheetsData = model.asInstanceOf[SheetsData]
    // Use existing SheetData.toJsonMultiple
    val jsonString = SheetData.toJsonMultiple(sheetsData.sheets)
    jsonString.getBytes("UTF-8")
  }
}