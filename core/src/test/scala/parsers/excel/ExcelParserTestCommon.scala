package com.tjclp.xlcr
package parsers.excel

import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook

import java.io.FileOutputStream
import java.nio.file.Path
import scala.util.Using

/**
 * Common utilities for Excel parser tests to reduce code duplication
 * and provide consistent test data creation.
 */
object ExcelParserTestCommon {

  def createComplexWorkbook(path: Path): Unit = {
    val workbook = new XSSFWorkbook()

    // Sheet with various data types
    val sheet1 = workbook.createSheet("DataTypes")
    createDataTypeExamples(workbook, sheet1)

    // Sheet with formatting
    val sheet2 = workbook.createSheet("Formatting")
    createFormattingExamples(workbook, sheet2)

    // Sheet with formulas
    val sheet3 = workbook.createSheet("Formulas")
    createFormulaExamples(workbook, sheet3)

    Using.resource(new FileOutputStream(path.toFile)) { fos =>
      workbook.write(fos)
    }
    workbook.close()
  }

  private def createDataTypeExamples(workbook: XSSFWorkbook, sheet: Sheet): Unit = {
    val row = sheet.createRow(0)

    // String
    row.createCell(0).setCellValue("Text")

    // Number
    row.createCell(1).setCellValue(42.5)

    // Boolean
    row.createCell(2).setCellValue(true)

    // Date
    val dateCell = row.createCell(3)
    dateCell.setCellValue(new java.util.Date())
    val dateStyle = workbook.createCellStyle()
    dateStyle.setDataFormat(workbook.getCreationHelper.createDataFormat().getFormat("yyyy-mm-dd"))
    dateCell.setCellStyle(dateStyle)

    // Error
    row.createCell(4).setCellErrorValue(FormulaError.DIV0.getCode)
  }

  private def createFormattingExamples(workbook: XSSFWorkbook, sheet: Sheet): Unit = {
    val row = sheet.createRow(0)

    // Bold text
    val cell1 = row.createCell(0)
    cell1.setCellValue("Bold")
    val boldStyle = workbook.createCellStyle()
    val boldFont = workbook.createFont()
    boldFont.setBold(true)
    boldStyle.setFont(boldFont)
    cell1.setCellStyle(boldStyle)

    // Colored background
    val cell2 = row.createCell(1)
    cell2.setCellValue("Background")
    val colorStyle = workbook.createCellStyle()
    colorStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
    colorStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex)
    cell2.setCellStyle(colorStyle)

    // Borders
    val cell3 = row.createCell(2)
    cell3.setCellValue("Bordered")
    val borderStyle = workbook.createCellStyle()
    borderStyle.setBorderTop(BorderStyle.THIN)
    borderStyle.setBorderBottom(BorderStyle.THIN)
    borderStyle.setBorderLeft(BorderStyle.THIN)
    borderStyle.setBorderRight(BorderStyle.THIN)
    cell3.setCellStyle(borderStyle)
  }

  private def createFormulaExamples(workbook: XSSFWorkbook, sheet: Sheet): Unit = {
    val row1 = sheet.createRow(0)
    row1.createCell(0).setCellValue(10)
    row1.createCell(1).setCellValue(20)

    val row2 = sheet.createRow(1)

    // Simple sum
    val sumCell = row2.createCell(0)
    sumCell.setCellFormula("SUM(A1:B1)")

    // Average
    val avgCell = row2.createCell(1)
    avgCell.setCellFormula("AVERAGE(A1:B1)")

    // Complex formula
    val complexCell = row2.createCell(2)
    complexCell.setCellFormula("IF(A1>B1,A1,B1)")
  }
}