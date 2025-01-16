package com.tjclp.xlcr
package bridges.excel

import bridges.Bridge
import models.FileContent
import models.excel.{SheetData, SheetsData}
import types.MimeType
import types.MimeType.ApplicationJson
import io.circe.parser
import io.circe.syntax._
import io.circe.Error

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import scala.util.{Try, Failure, Success}

/**
 * JsonExcelInputBridge parses JSON -> SheetsData and also renders SheetsData -> JSON.
 * This replaces JsonToExcelParser logic on input side,
 * and partially duplicates ExcelJsonOutputBridge logic for the output side.
 */
object JsonExcelInputBridge extends Bridge[
  SheetsData,
  MimeType.ApplicationJson.type,
  MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
] {

  override def parse(input: FileContent[ApplicationJson.type]): SheetsData = {
    val jsonString = new String(input.data, StandardCharsets.UTF_8)
    SheetData.fromJsonMultiple(jsonString) match {
      case Left(err) =>
        throw new RuntimeException(s"Failed to parse SheetsData from JSON: ${err.getMessage}")
      case Right(sheets) =>
        SheetsData(sheets)
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