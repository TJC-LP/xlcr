package com.tjclp.xlcr
package parsers.excel

import compat.Using
import models.FileContent
import models.excel.{SheetData, SheetsData}
import parsers.ParserConfig
import types.MimeType

import org.apache.poi.ss.usermodel.WorkbookFactory

import java.io.ByteArrayInputStream
import scala.util.Try

/** SheetsDataExcelParser parses Excel files (XLSX) into SheetsData.
  */
class SheetsDataExcelParser
    extends SheetsDataParser[
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
    ] {

  /** Configuration-aware version that uses the config parameter.
    * This is required for the ExcelParserConfig that we created.
    */
  override def parse(
      input: FileContent[
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
      ],
      config: Option[ParserConfig] = None
  ): SheetsData = {
    Try {
      Using.resource(new ByteArrayInputStream(input.data)) { bais =>
        val workbook = WorkbookFactory.create(bais)
        val evaluator = workbook.getCreationHelper.createFormulaEvaluator()

        // Apply config if provided and it's an ExcelParserConfig
        config.collect { case c: ExcelParserConfig =>
          // Apply any configuration options
          if (c.evaluateFormulas) {
            evaluator.evaluateAll()
          }
        }

        val sheets = for (idx <- 0 until workbook.getNumberOfSheets) yield {
          val sheet = workbook.getSheetAt(idx)
          SheetData.fromSheet(sheet, evaluator)
        }
        workbook.close()
        SheetsData(sheets.toList)
      }
    }.recover { case ex: Exception =>
      throw ParserError(
        s"Failed to parse Excel to SheetsData: ${ex.getMessage}",
        Some(ex)
      )
    }.get
  }
}
