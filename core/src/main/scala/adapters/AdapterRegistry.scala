package com.tjclp.xlcr
package adapters

import bridges.Bridge
import bridges.excel.{ExcelBridge, ExcelJsonOutputBridge}
import models.excel.SheetsData
import types.MimeType

import scala.reflect.ClassTag

object AdapterRegistry {
  object implicits {
    // Excel -> JSON conversion
    implicit val excelToJson: Bridge[
      SheetsData,
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
      MimeType.ApplicationJson.type
    ] = ExcelBridge.chain[MimeType.ApplicationJson.type](ExcelJsonOutputBridge)
  }
}