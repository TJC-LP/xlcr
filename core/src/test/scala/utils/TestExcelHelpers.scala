package com.tjclp.xlcr
package utils

import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellReference
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory

import java.io.FileOutputStream
import java.nio.file.Path
import scala.util.{Using, boundary}
import scala.util.boundary.break

object TestExcelHelpers {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Creates a simple Excel file with basic test data
   */
  def createBasicExcel(path: Path): Unit = {
    val workbook = new XSSFWorkbook()
    val sheet = workbook.createSheet("Sheet1")
    val row = sheet.createRow(0)
    val cell = row.createCell(0)
    cell.setCellValue("Test Cell")

    Using.resource(new FileOutputStream(path.toFile)) { fos =>
      workbook.write(fos)
    }
    workbook.close()
  }

  private def formatCellValue(cell: Cell): String = if (cell == null) "null" else {
    cell.getCellType match {
      case CellType.NUMERIC => s"NUMERIC(${cell.getNumericCellValue})"
      case CellType.STRING => s"STRING(${cell.getStringCellValue})"
      case CellType.BOOLEAN => s"BOOLEAN(${cell.getBooleanCellValue})"
      case CellType.FORMULA => s"FORMULA(${cell.getCellFormula})"
      case CellType.ERROR => s"ERROR(${cell.getErrorCellValue})"
      case CellType.BLANK => "BLANK"
      case _ => "UNKNOWN"
    }
  }

  /**
   * Creates an Excel file with multiple data types
   */
  def createMultiDataTypeExcel(path: Path): Unit = {
    val workbook = new XSSFWorkbook()
    val sheet = workbook.createSheet("DataTypes")
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

    Using.resource(new FileOutputStream(path.toFile)) { fos =>
      workbook.write(fos)
    }
    workbook.close()
  }

  /**
   * Creates an Excel file with formatting examples
   */
  def createStyledExcel(path: Path): Unit = {
    val workbook = new XSSFWorkbook()
    val sheet = workbook.createSheet("Formatting")
    val row = sheet.createRow(0)

    // Bold Cell
    val cell1 = row.createCell(0)
    cell1.setCellValue("Bold")
    val boldStyle = workbook.createCellStyle()
    val boldFont = workbook.createFont()
    boldFont.setBold(true)
    boldStyle.setFont(boldFont)
    cell1.setCellStyle(boldStyle)

    // Colored Cell
    val cell2 = row.createCell(1)
    cell2.setCellValue("Background")
    val colorStyle = workbook.createCellStyle()
    colorStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
    colorStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex)
    cell2.setCellStyle(colorStyle)

    // Italic Cell
    val cell3 = row.createCell(2)
    cell3.setCellValue("Italic")
    val italicStyle = workbook.createCellStyle()
    val italicFont = workbook.createFont()
    italicFont.setItalic(true)
    italicStyle.setFont(italicFont)
    cell3.setCellStyle(italicStyle)

    Using.resource(new FileOutputStream(path.toFile)) { fos =>
      workbook.write(fos)
    }
    workbook.close()
  }

  /**
   * Creates an Excel file with formulas
   */
  def createFormulaExcel(path: Path): Unit = {
    val workbook = new XSSFWorkbook()
    val sheet = workbook.createSheet("Formulas")

    // Create data cells
    val row1 = sheet.createRow(0)
    row1.createCell(0).setCellValue(10)
    row1.createCell(1).setCellValue(20)

    // Create formula cells
    val row2 = sheet.createRow(1)

    // Sum formula
    row2.createCell(0).setCellFormula("SUM(A1:B1)")

    // Average formula
    row2.createCell(1).setCellFormula("AVERAGE(A1:B1)")

    Using.resource(new FileOutputStream(path.toFile)) { fos =>
      workbook.write(fos)
    }
    workbook.close()
  }


  /**
   * Compare two Excel files for structural equality
   */
  def compareExcelFiles(file1: Path, file2: Path): Boolean = {
    val wb1 = WorkbookFactory.create(file1.toFile)
    val wb2 = WorkbookFactory.create(file2.toFile)
  
    try {
      boundary {
        // Check number of sheets
        if wb1.getNumberOfSheets != wb2.getNumberOfSheets then
          logger.error(s"Sheet count mismatch: ${wb1.getNumberOfSheets} vs ${wb2.getNumberOfSheets}")
          break(false)
  
        // Check each sheet
        for sheetIndex <- 0 until wb1.getNumberOfSheets do
          val sheet1 = wb1.getSheetAt(sheetIndex)
          val sheet2 = wb2.getSheetAt(sheetIndex)
  
          // Compare sheet structure
          if sheet1.getLastRowNum != sheet2.getLastRowNum then
            logger.error(s"Row count mismatch in sheet $sheetIndex: ${sheet1.getLastRowNum} vs ${sheet2.getLastRowNum}")
            break(false)
  
          // Compare sheet names
          if sheet1.getSheetName != sheet2.getSheetName then
            logger.error(s"Sheet name mismatch: ${sheet1.getSheetName} vs ${sheet2.getSheetName}")
            break(false)
  
          // Compare row content
          for rowIndex <- 0 to sheet1.getLastRowNum do
            val row1 = sheet1.getRow(rowIndex)
            val row2 = sheet2.getRow(rowIndex)
  
            if (row1 == null && row2 != null) || (row1 != null && row2 == null) then
              logger.error(s"Row existence mismatch at index $rowIndex in sheet ${sheet1.getSheetName}")
              break(false)
  
            if row1 != null && row2 != null then
              if row1.getLastCellNum != row2.getLastCellNum then
                logger.error(s"Cell count mismatch in row $rowIndex: ${row1.getLastCellNum} vs ${row2.getLastCellNum}")
                break(false)
  
              // Compare cell content
              for cellIndex <- 0 until row1.getLastCellNum do
                val cell1 = row1.getCell(cellIndex)
                val cell2 = row2.getCell(cellIndex)
                if !compareCells(cell1, cell2) then
                  val addr = s"${sheet1.getSheetName}!${CellReference.convertNumToColString(cellIndex)}${rowIndex + 1}"
                  logger.error(s"Cell content mismatch at $addr")
                  logger.error(s"Cell1: ${formatCellValue(cell1)}")
                  logger.error(s"Cell2: ${formatCellValue(cell2)}")
                  break(false)
  
        // If we get here, files are considered equal
        true
      }
    } finally {
      wb1.close()
      wb2.close()
    }
  }

  private def compareCells(cell1: Cell, cell2: Cell): Boolean =
    if (cell1 == null && cell2 == null) then true
    else if (cell1 == null || cell2 == null) then false
    else
      cell1.getCellType == cell2.getCellType && {
        cell1.getCellType match
          case CellType.NUMERIC => cell1.getNumericCellValue == cell2.getNumericCellValue
          case CellType.STRING => cell1.getStringCellValue == cell2.getStringCellValue
          case CellType.BOOLEAN => cell1.getBooleanCellValue == cell2.getBooleanCellValue
          case CellType.FORMULA => cell1.getCellFormula == cell2.getCellFormula
          case _ => true // Consider other types equal or handle specifically
      }
}