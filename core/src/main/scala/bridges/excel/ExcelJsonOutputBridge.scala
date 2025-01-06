package com.tjclp.xlcr
package bridges.excel

import bridges.OutputBridge
import models.excel.{SheetData, SheetsData}
import types.MimeType

import com.tjclp.xlcr

/**
 * ExcelJsonOutputBridge converts a list of SheetData into JSON bytes.
 */
object ExcelJsonOutputBridge extends OutputBridge[
  SheetsData,
  MimeType.ApplicationJson.type
] {
  override def render(model: SheetsData): Array[Byte] = {
    // Use existing SheetData.toJsonMultiple
    val jsonString = SheetData.toJsonMultiple(model.sheets)
    jsonString.getBytes("UTF-8")
  }
}