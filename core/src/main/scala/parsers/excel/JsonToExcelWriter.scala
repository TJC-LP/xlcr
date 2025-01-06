package com.tjclp.xlcr
package parsers.excel

import models.excel.{CellData, SheetData}

import org.apache.poi.ss.usermodel.{BorderStyle, CellType, FillPatternType, WorkbookFactory}
import org.apache.poi.xssf.usermodel.{XSSFColor, XSSFFont, XSSFWorkbook}

import java.io.FileOutputStream
import java.nio.file.{Files, Path}
import scala.annotation.tailrec
import scala.util.{Failure, Success, Try, Using}

/**
 * Converts JSON representing one or more SheetData objects into an Excel (.xlsx) file.
 * We now split logic into smaller methods for clarity and maintainability.
 */
object JsonToExcelWriter:

  /** Entry point to parse JSON -> Excel. Or merges diff if requested. */
  def jsonToExcelFile(inputJsonPath: Path, outputExcelPath: Path, diffMode: Boolean = false): Try[Unit] =
    if !Files.exists(inputJsonPath) then
      Failure(IllegalArgumentException(s"JSON file does not exist: $inputJsonPath"))
    else
      val jsonContent = readJsonContent(inputJsonPath)
      jsonContent.flatMap { json =>
        SheetData.fromJsonMultiple(json) match
          case Left(error) =>
            Failure(new RuntimeException(s"Failed to parse SheetData from JSON: ${error.getMessage}"))
          case Right(newSheetsData) =>
            if !diffMode then
              // no merging, just create excel
              val workbookTry = createWorkbookFromSheetData(newSheetsData)
              workbookTry.flatMap(wb => writeWorkbook(wb, outputExcelPath))
            else
              // diff mode
              mergeDiffAndWrite(newSheetsData, outputExcelPath)
      }

  /** Merge new sheets with existing workbook, if that workbook is parseable. */
  private def mergeDiffAndWrite(newSheetsData: List[SheetData], outputExcelPath: Path): Try[Unit] =
    if !Files.exists(outputExcelPath) then
      // no existing file, just create a new one
      createWorkbookFromSheetData(newSheetsData).flatMap(wb => writeWorkbook(wb, outputExcelPath))
    else
      val existingTry = readExistingSheets(outputExcelPath)
      existingTry match
        case Failure(ex) =>
          Failure(new RuntimeException(s"Failed to read existing Excel in diff mode: ${ex.getMessage}", ex))
        case Success(existingSheetsData) =>
          val mergedSheets = mergeSheets(existingSheetsData, newSheetsData)
          val wbTry = createWorkbookFromSheetData(mergedSheets)
          wbTry.flatMap(wb => writeWorkbook(wb, outputExcelPath))

  /** Actually read the existing workbook's SheetData. */
  private def readExistingSheets(xlsxPath: Path): Try[List[SheetData]] = Try {
    val wb = WorkbookFactory.create(xlsxPath.toFile)
    val evaluator = wb.getCreationHelper.createFormulaEvaluator()
    val sheets = (0 until wb.getNumberOfSheets).map { idx =>
      val sheet = wb.getSheetAt(idx)
      SheetData.fromSheet(sheet, evaluator)
    }.toList
    wb.close()
    sheets
  }

  /** Merges old sheets with new sheets by name. */
  private def mergeSheets(oldSheets: List[SheetData], newSheets: List[SheetData]): List[SheetData] =
    val existingMap = oldSheets.map(s => s.name -> s).toMap
    val mergedMap = newSheets.foldLeft(existingMap) { (acc, newSheet) =>
      acc.get(newSheet.name) match
        case Some(existingSheet) => acc.updated(newSheet.name, existingSheet.mergeDiff(newSheet))
        case None => acc.updated(newSheet.name, newSheet)
    }
    mergedMap.values.toList

  /** Builds a workbook from a list of SheetData objects. */
  private def createWorkbookFromSheetData(sheetsData: List[SheetData]): Try[XSSFWorkbook] = Try {
    val workbook = XSSFWorkbook()
    sheetsData.foreach { sd =>
      val sheet = workbook.createSheet(sd.name)
      populateSheetCells(sheet, sd)
    }
    workbook
  }

  /**
   * Populates each row/column in the sheet, then applies cell styles from cell data.
   */
  private def populateSheetCells(sheet: org.apache.poi.ss.usermodel.Sheet, sd: SheetData): Unit =
    for rowIndex <- 0 until sd.rowCount do
      val row = sheet.createRow(rowIndex)
      for colIndex <- 0 until sd.columnCount do
        val cellRef = cellAddress(colIndex, rowIndex + 1)
        val poiCell = row.createCell(colIndex, CellType.BLANK)
        val maybeCellData = sd.cells.find(_.address == cellRef)

        maybeCellData.foreach { cellData =>
          setCellValue(poiCell, cellData)
          applyCellDataStyles(sheet.getWorkbook.asInstanceOf[XSSFWorkbook], poiCell, cellData)
        }
    // optionally auto-size columns
    for colIndex <- 0 until sd.columnCount do
      sheet.autoSizeColumn(colIndex)

  /** Sets the raw cell value (NUMERIC, STRING, etc.). */
  private def setCellValue(poiCell: org.apache.poi.ss.usermodel.Cell, cellData: CellData): Unit =
    cellData.cellType match
      case "FORMULA" =>
        poiCell.removeFormula()
        poiCell.setCellFormula(cellData.formula.getOrElse(""))
      case "ERROR" =>
        cellData.errorValue match
          case Some(errCode) =>
            poiCell.setCellType(CellType.ERROR)
            poiCell.setCellErrorValue(errCode)
          case None =>
            poiCell.setBlank()
      case other =>
        if cellData.value.exists(isNumeric) then
          poiCell.setCellValue(cellData.value.get.toDouble)
        else if cellData.value.exists(isBoolean) then
          poiCell.setCellValue(cellData.value.get.toBoolean)
        else
          poiCell.setCellValue(cellData.value.getOrElse(""))

  /**
   * Applies style and font data from the JSON to the poiCell.
   */
  private def applyCellDataStyles(workbook: XSSFWorkbook, poiCell: org.apache.poi.ss.usermodel.Cell, cellData: CellData): Unit =
    val cellStyle = workbook.createCellStyle()

    // data format
    cellData.dataFormat.foreach { fmt =>
      val dataFormat = workbook.createDataFormat()
      val formatIndex = dataFormat.getFormat(fmt)
      cellStyle.setDataFormat(formatIndex)
    }

    // style
    cellData.style.foreach { st =>
      st.foregroundColor.foreach { fg =>
        val xssfColor = parseXSSFColor(fg)
        if xssfColor != null then
          cellStyle.setFillForegroundColor(xssfColor)
          st.pattern.foreach { pat =>
            if pat == "SOLID_FOREGROUND" then
              cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
          }
      }
      cellStyle.setRotation(st.rotation.toShort)
      cellStyle.setIndention(st.indention.toShort)

      def parseBorderStyle(b: Option[String]): Option[BorderStyle] =
        b.flatMap { bs => scala.util.Try(BorderStyle.valueOf(bs)).toOption }

      parseBorderStyle(st.borderTop).foreach(cellStyle.setBorderTop)
      parseBorderStyle(st.borderRight).foreach(cellStyle.setBorderRight)
      parseBorderStyle(st.borderBottom).foreach(cellStyle.setBorderBottom)
      parseBorderStyle(st.borderLeft).foreach(cellStyle.setBorderLeft)
    }

    // font
    cellData.font.foreach { f =>
      val fontObj = workbook.createFont()
      fontObj.setBold(f.bold)
      fontObj.setItalic(f.italic)
      f.size.foreach(x => fontObj.setFontHeightInPoints(x.toShort))
      f.underline.foreach(fontObj.setUnderline)
      fontObj.setStrikeout(f.strikeout)
      f.rgbColor.foreach { rgb =>
        val xssfColor = parseXSSFColor(rgb)
        if xssfColor != null && fontObj.isInstanceOf[XSSFFont] then
          val xssfFont = fontObj.asInstanceOf[XSSFFont]
          xssfFont.setColor(xssfColor)
      }
      fontObj.setFontName(f.name)
      cellStyle.setFont(fontObj)
    }

    poiCell.setCellStyle(cellStyle)

  /** Writes workbook to the output file path. */
  private def writeWorkbook(workbook: XSSFWorkbook, outputExcelPath: Path): Try[Unit] = Try {
    Using.resource(new FileOutputStream(outputExcelPath.toFile)) { fos =>
      workbook.write(fos)
    }
    workbook.close()
  }

  /**
   * Reads all bytes from the provided path as a String.
   */
  private def readJsonContent(path: Path): Try[String] =
    Try {
      new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8)
    }

  /**
   * Construct A1 style references for columns (e.g. A, B, AA...) given colIndex and row.
   */
  private def cellAddress(colIndex: Int, rowIndex: Int): String =
    @tailrec
    def toLetters(n: Int, acc: String = ""): String =
      if n < 0 then acc
      else
        val remainder = n % 26
        val char = (remainder + 'A').toChar
        toLetters(n / 26 - 1, char.toString + acc)

    s"${toLetters(colIndex)}$rowIndex"

  private def isNumeric(str: String): Boolean =
    str.matches("""-?\d+(\.\d+)?""")

  private def isBoolean(str: String): Boolean =
    str.equalsIgnoreCase("true") || str.equalsIgnoreCase("false")

  /**
   * Parses a hex color string (like #RRGGBB or #RRGGBBAA) into an XSSFColor.
   */
  private def parseXSSFColor(hex: String): XSSFColor =
    val clean = if hex.startsWith("#") then hex.substring(1) else hex
    val length = clean.length
    if length == 6 || length == 8 then
      try
        val r = Integer.parseInt(clean.substring(0, 2), 16).toByte
        val g = Integer.parseInt(clean.substring(2, 4), 16).toByte
        val b = Integer.parseInt(clean.substring(4, 6), 16).toByte
        if length == 8 then
          val a = Integer.parseInt(clean.substring(6, 8), 16).toByte
          new XSSFColor(java.util.Arrays.copyOf(Array(r, g, b, a), 4), null)
        else
          new XSSFColor(java.util.Arrays.copyOf(Array(r, g, b), 3), null)
      catch
        case _: Throwable => null
    else null