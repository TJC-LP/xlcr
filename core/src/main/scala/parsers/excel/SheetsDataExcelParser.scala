package com.tjclp.xlcr
package parsers.excel

import models.FileContent
import models.excel.{SheetData, SheetsData}
import types.MimeType

import org.apache.poi.ss.usermodel.WorkbookFactory

import java.io.ByteArrayInputStream
import scala.util.Try
import com.tjclp.xlcr.compat.Using

/**
 * SheetsDataExcelParser parses Excel files (XLSX) into SheetsData.
 */
class SheetsDataExcelParser extends SheetsDataParser[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type] {
  override def parse(input: FileContent[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type]): SheetsData = {
    Try {
      Using.resource(new ByteArrayInputStream(input.data)) { bais =>
        val workbook = WorkbookFactory.create(bais)
        val evaluator = workbook.getCreationHelper.createFormulaEvaluator()

        val sheets = for (idx <- 0 until workbook.getNumberOfSheets) yield {
          val sheet = workbook.getSheetAt(idx)
          SheetData.fromSheet(sheet, evaluator)
        }
        workbook.close()
        SheetsData(sheets.toList)
      }
    }.recover {
      case ex: Exception =>
        throw ParserError(s"Failed to parse Excel to SheetsData: ${ex.getMessage}", Some(ex))
    }.get
  }
}
