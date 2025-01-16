package com.tjclp.xlcr
package bridges.excel

import bridges.Bridge
import models.FileContent
import models.excel.{CellData, SheetData, SheetsData}
import types.MimeType
import types.MimeType.TextMarkdown

import org.apache.poi.ss.usermodel._
import org.apache.poi.xssf.usermodel.XSSFWorkbook

import java.io.ByteArrayOutputStream
import scala.collection.mutable
import scala.util.Try

/**
 * MarkdownExcelInputBridge parses Markdown -> SheetsData.
 * This replaces MarkdownToExcelParser logic.
 */
object MarkdownExcelInputBridge extends Bridge[
  SheetsData,
  MimeType.TextMarkdown.type,
  MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
] {

  override def parse(input: FileContent[TextMarkdown.type]): SheetsData = {
    val markdown = new String(input.data, "UTF-8")
    val workbook = parseMarkdownToWorkbook(markdown)
    val (sheets, _) = workbookToSheetsData(workbook)
    workbook.close()
    SheetsData(sheets)
  }

  override def render(model: SheetsData): FileContent[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type] = {
    // We can re-use the logic from parseMarkdownToWorkbook in reverse,
    // but here we already have SheetsData, so we just create an Excel.
    val workbook = createWorkbookFromSheetsData(model.sheets)
    val bos = new ByteArrayOutputStream()
    workbook.write(bos)
    workbook.close()
    FileContent(bos.toByteArray, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)
  }

  /**
   * Parse a markdown string into an XSSFWorkbook.
   */
  private def parseMarkdownToWorkbook(md: String): XSSFWorkbook = {
    val lines = md.linesIterator.toList
    val workbook = new XSSFWorkbook()
    val sheetBlocks = splitSheets(lines)

    sheetBlocks.foreach { case (sheetName, contentLines) =>
      val sheet = workbook.createSheet(sheetName)
      parseTableIntoSheet(contentLines, sheet)
    }

    workbook
  }

  /**
   * Helper: convert workbook -> list of SheetData so we can unify in parse.
   */
  private def workbookToSheetsData(workbook: XSSFWorkbook): (List[SheetData], FormulaEvaluator) = {
    val evaluator = workbook.getCreationHelper.createFormulaEvaluator()
    val sheetList = (0 until workbook.getNumberOfSheets).map { idx =>
      SheetData.fromSheet(workbook.getSheetAt(idx), evaluator)
    }.toList
    (sheetList, evaluator)
  }

  /**
   * Splitting logic by headings (# SheetName).
   */
  private def splitSheets(lines: List[String]): List[(String, List[String])] = {
    val result = mutable.ListBuffer.empty[(String, List[String])]
    var currentSheetName = "Sheet1"
    var currentBlock = mutable.ListBuffer.empty[String]
    var firstSheet = true

    def pushBlock(): Unit = {
      if (currentBlock.nonEmpty) {
        result += ((currentSheetName, currentBlock.toList))
        currentBlock.clear()
      }
    }

    for (line <- lines) {
      if (line.trim.startsWith("# ")) {
        if (!firstSheet) pushBlock()
        else firstSheet = false
        currentSheetName = line.trim.stripPrefix("# ").trim
      } else {
        currentBlock += line
      }
    }

    if (currentBlock.nonEmpty) pushBlock()
    result.toList
  }

  /**
   * Parse a table into the sheet.
   */
  private def parseTableIntoSheet(lines: List[String], sheet: Sheet): Unit = {
    val dataLines = lines.filter(_.trim.startsWith("|"))
    if (dataLines.size < 2) return

    // First line => column headers
    val headerCols = parseMarkdownRow(dataLines.head)
    // skip the row/column header
    val colHeaders = headerCols.drop(1)

    val contentLines = dataLines.tail.filterNot(_.matches("""\|\s*-*:?\|\s*.*"""))

    for ((line, i) <- contentLines.zipWithIndex) {
      val cells = parseMarkdownRow(line)
      if (cells.nonEmpty) {
        val rowNumberString = cells.head.trim
        // rowNumber is 1-based
        val rowIndex = rowNumberString.toIntOption.getOrElse(i) - 1
        if (rowIndex >= 0) {
          val rowObj = sheet.createRow(rowIndex)
          for (colIndex <- colHeaders.indices) {
            val value = if (colIndex + 1 < cells.size) cells(colIndex + 1).trim else ""
            val cleanedValue = extractValue(value)
            val cellType = determineCellType(value, cleanedValue)
            val cellObj = rowObj.createCell(colIndex, cellType)
            cellType match {
              case CellType.NUMERIC =>
                cellObj.setCellValue(cleanedValue.toDoubleOption.getOrElse(0.0))
              case CellType.BOOLEAN =>
                cellObj.setCellValue(cleanedValue.toBooleanOption.getOrElse(false))
              case CellType.FORMULA =>
                cellObj.setCellFormula(cleanedValue)
              case CellType.BLANK =>
              // do nothing
              case _ =>
                cellObj.setCellValue(cleanedValue)
            }
          }
        }
      }
    }
  }

  private def parseMarkdownRow(line: String): List[String] = {
    val trimmed = line.trim.stripPrefix("|").stripSuffix("|").trim
    trimmed.split("\\|").map(_.trim).toList
  }

  /**
   * Extract actual value from e.g. "VALUE:``Hello``<br>TYPE:``STRING``"
   */
  private def extractValue(mdCell: String): String = {
    val valueRegex = """(?s).*?VALUE:``(.*?)``.*""".r
    mdCell match {
      case valueRegex(captured) => captured
      case _ => mdCell
    }
  }

  private def determineCellType(fullValue: String, cleanedValue: String): CellType = {
    val typeRegex = """(?s).*?TYPE:``(.*?)``.*""".r
    val explicitTypeOpt = fullValue match {
      case typeRegex(t) => Some(t)
      case _ => None
    }
    explicitTypeOpt match {
      case Some("NUMERIC") => CellType.NUMERIC
      case Some("BOOLEAN") => CellType.BOOLEAN
      case Some("FORMULA") => CellType.FORMULA
      case Some("BLANK")   => CellType.BLANK
      case Some(_)         => CellType.STRING
      case None =>
        if (cleanedValue.matches("""-?\d+(\.\d+)?""")) CellType.NUMERIC
        else if (cleanedValue.equalsIgnoreCase("true") || cleanedValue.equalsIgnoreCase("false")) CellType.BOOLEAN
        else if (cleanedValue.isEmpty) CellType.BLANK
        else CellType.STRING
    }
  }

  /**
   * Create a new workbook from a list of SheetData.
   */
  private def createWorkbookFromSheetsData(sheets: List[SheetData]): XSSFWorkbook = {
    val workbook = new XSSFWorkbook()
    sheets.foreach { sd =>
      val sheet = workbook.createSheet(sd.name)
      populateSheet(sheet, sd)
    }
    workbook
  }

  private def populateSheet(sheet: Sheet, sheetData: SheetData): Unit = {
    for (rowIndex <- 0 until sheetData.rowCount) {
      val rowObj = sheet.createRow(rowIndex)
      for (colIndex <- 0 until sheetData.columnCount) {
        val ref = cellAddress(colIndex, rowIndex + 1)
        val maybeCellData = sheetData.cells.find(_.address == ref)
        val cellObj = rowObj.createCell(colIndex, CellType.BLANK)
        maybeCellData.foreach { cd =>
          // we can do a simplified setCellValue ignoring style
          cd.cellType match {
            case "FORMULA" =>
              cellObj.setCellFormula(cd.formula.getOrElse(""))
            case "ERROR" =>
              cd.errorValue.foreach(cellObj.setCellErrorValue)
            case _ =>
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

  private def cellAddress(colIndex: Int, rowIndex: Int): String = {
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