package com.tjclp.xlcr
package bridges.excel

import bridges.MergeableSymmetricBridge
import models.excel.SheetsData
import parsers.excel.SheetsDataExcelParser
import renderers.excel.SheetsDataExcelRenderer
import types.MimeType

/** ExcelBridge can parse XLSX bytes into a List[SheetData] and render them back to XLSX.
  */
object SheetsDataExcelBridge
    extends MergeableSymmetricBridge[
      SheetsData,
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
    ] {
  override protected def parser = new SheetsDataExcelParser()

  override protected def renderer = new SheetsDataExcelRenderer()
}
