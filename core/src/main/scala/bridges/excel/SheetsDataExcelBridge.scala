package com.tjclp.xlcr
package bridges.excel

import bridges.{MergeableSymmetricBridge, SymmetricBridge}
import models.FileContent
import models.excel.{SheetData, SheetsData}
import types.MimeType

import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.util.Using

/**
 * ExcelBridge can parse XLSX bytes into a List[SheetData] and render them back to XLSX.
 */
object SheetsDataExcelBridge extends MergeableSymmetricBridge[
  SheetsData,
  MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
] {
  override def parseInput(input: FileContent[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type]): SheetsData = {
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

  override def render(model: SheetsData): FileContent[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type] = {
    // Create a workbook from the SheetsData
    val workbook = createWorkbookFromSheetsData(model.sheets)
    val bos = new ByteArrayOutputStream()
    workbook.write(bos)
    workbook.close()
    FileContent(bos.toByteArray, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)
  }

  private def createWorkbookFromSheetsData(sheets: List[SheetData]): XSSFWorkbook = {
    val workbook = new XSSFWorkbook()
    sheets.foreach { sd =>
      val sheet = workbook.createSheet(sd.name)
      for (r <- 0 until sd.rowCount) {
        val row = sheet.createRow(r)
        for (c <- 0 until sd.columnCount) {
          val ref = cellAddress(c, r + 1)
          val cellObj = row.createCell(c)
          sd.cells.find(_.address == ref).foreach { cd =>
            if (cd.formula.nonEmpty && cd.cellType == "FORMULA") {
              cellObj.setCellFormula(cd.formula.get)
            } else if (cd.errorValue.isDefined && cd.cellType == "ERROR") {
              cellObj.setCellErrorValue(cd.errorValue.get)
            } else {
              if (cd.value.exists(_.matches("""-?\d+(\.\d+)?"""))) {
                cellObj.setCellValue(cd.value.get.toDouble)
              } else if (cd.value.exists(v => v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false"))) {
                cellObj.setCellValue(cd.value.get.toBoolean)
              } else {
                cellObj.setCellValue(cd.value.getOrElse(""))
              }
            }
          }
        }
      }
    }
    workbook
  }

  private def cellAddress(colIndex: Int, rowIndex: Int): String = {
    @scala.annotation.tailrec
    def toLetters(n: Int, acc: String = ""): String = {
      if (n < 0) acc
      else {
        val remainder = n % 26
        val char = (remainder + 'A').toChar
        toLetters(n / 26 - 1, char.toString + acc)
      }
    }
    s"${toLetters(colIndex)}$rowIndex"
  }
}