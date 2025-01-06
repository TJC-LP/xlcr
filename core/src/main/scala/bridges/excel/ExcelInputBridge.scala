package com.tjclp.xlcr
package bridges.excel

import bridges.InputBridge
import models.excel.{SheetData, SheetsData}
import types.MimeType

import org.apache.poi.ss.usermodel.WorkbookFactory

import java.io.ByteArrayInputStream
import scala.util.Using

/**
 * ExcelInputBridge can parse XLSX bytes into a List[SheetData].
 */
object ExcelInputBridge extends InputBridge[
  MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
  SheetsData
] {
  override def parse(inputBytes: Array[Byte]): SheetsData = {
    Using.resource(new ByteArrayInputStream(inputBytes)) { bais =>
      val workbook = WorkbookFactory.create(bais)
      val evaluator = workbook.getCreationHelper.createFormulaEvaluator()

      // For each sheet, build a SheetData
      val sheets = for (idx <- 0 until workbook.getNumberOfSheets) yield {
        val sheet = workbook.getSheetAt(idx)
        SheetData.fromSheet(sheet, evaluator)
      }

      workbook.close()
      SheetsData(sheets.toList)
    }
  }
}