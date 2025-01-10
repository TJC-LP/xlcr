package com.tjclp.xlcr
package bridges.excel

import bridges.SymmetricBridge
import models.FileContent
import models.excel.{SheetData, SheetsData}
import types.MimeType

import org.apache.poi.ss.usermodel.WorkbookFactory

import java.io.ByteArrayInputStream
import scala.util.Using

/**
 * ExcelInputBridge can parse XLSX bytes into a List[SheetData].
 */
object ExcelBridge extends SymmetricBridge[
  SheetsData,
  MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
] {
  override def parse(input: FileContent[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type]): SheetsData = {
    Using.resource(new ByteArrayInputStream(input.data)) { is =>
      val workbook = WorkbookFactory.create(is)
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