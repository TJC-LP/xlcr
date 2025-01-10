package com.tjclp.xlcr
package bridges.excel

import bridges.SymmetricBridge
import models.FileContent
import models.excel.{SheetData, SheetsData}
import types.MimeType

import com.tjclp.xlcr

/**
 * ExcelJsonOutputBridge converts a list of SheetData into JSON bytes.
 */
object ExcelJsonOutputBridge extends SymmetricBridge[
  SheetsData,
  MimeType.ApplicationJson.type
] {
  override def render(model: SheetsData): FileContent[MimeType.ApplicationJson.type] = {
    // Use existing SheetData.toJsonMultiple
    val jsonString = SheetData.toJsonMultiple(model.sheets)
    FileContent(jsonString.getBytes("UTF-8"), MimeType.ApplicationJson)
  }
}