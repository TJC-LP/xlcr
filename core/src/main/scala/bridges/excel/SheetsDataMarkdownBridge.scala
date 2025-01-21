package com.tjclp.xlcr
package bridges.excel

import bridges.{MergeableSymmetricBridge, SymmetricBridge}
import models.FileContent
import models.excel.{SheetData, SheetsData}
import types.MimeType
import types.MimeType.TextMarkdown

import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook

import java.nio.charset.StandardCharsets
import scala.collection.mutable

/**
 * SheetsDataMarkdownBridge is a symmetric bridge that converts
 * SheetsData to/from Markdown. This merges the logic from the
 * older MarkdownExcelInputBridge and ExcelMarkdownOutputBridge.
 */
object SheetsDataMarkdownBridge extends MergeableSymmetricBridge[
  SheetsData,
  TextMarkdown.type
] {
  // --------------------------------------------------------------------------
  // Render: SheetsData -> Markdown
  // --------------------------------------------------------------------------
  override def render(model: SheetsData): FileContent[TextMarkdown.type] = {
    val markdown = model.sheets.map(sheetToMarkdown).mkString("\n\n---\n\n")
    FileContent(markdown.getBytes(StandardCharsets.UTF_8), TextMarkdown)
  }

  private def sheetToMarkdown(sheetData: SheetData): String = {
    val sb = new StringBuilder
    sb.append(s"# ${sheetData.name}\n\n")

    val columnHeaders = generateColumnHeaders(sheetData.columnCount)

    // Table header
    sb.append("| <sub>row</sub><sup>column</sup> |")
    columnHeaders.foreach(col => sb.append(s" $col |"))
    sb.append("\n")

    // Table separator
    sb.append("|--:|")
    columnHeaders.foreach(_ => sb.append(":--|"))
    sb.append("\n")

    // Table content
    for (row <- 1 to sheetData.rowCount) {
      sb.append(f"| $row%3d |")
      for (colIndex <- columnHeaders.indices) {
        val ref = s"${columnHeaders(colIndex)}$row"
        val cell = sheetData.cells.find(_.address == ref)
        val cellContent = cell.map(formatCellContent).getOrElse("")
        sb.append(s" $cellContent |")
      }
      sb.append("\n")
    }

    sb.toString
  }

  private def formatCellContent(cellData: models.excel.CellData): String = {
    val value = escapeMarkdown(cellData.formattedValue.getOrElse(cellData.value.getOrElse("")))
    val otherDetails = List(
      Some(s"REF:``${cellData.referenceA1}``"),
      Some(s"TYPE:``${cellData.cellType}``"),
      cellData.formula.map(f => s"FORMULA:``$f``"),
      cellData.dataFormat.map(fmt => s"STYLE:``$fmt``")
    ).flatten.mkString("<br>")
    s"VALUE:``$value``<br>$otherDetails"
  }

  private def escapeMarkdown(s: String): String =
    s.replace("|", "\\|").replace("\n", "<br>")

  private def generateColumnHeaders(columnCount: Int): Seq[String] = {
    def toColumnName(n: Int): String = {
      if (n < 0) ""
      else {
        val quotient = n / 26
        val remainder = n % 26
        if (quotient == 0) (remainder + 'A').toChar.toString
        else toColumnName(quotient - 1) + (remainder + 'A').toChar
      }
    }

    (0 until columnCount).map(toColumnName)
  }

  // --------------------------------------------------------------------------
  // Parse: Markdown -> SheetsData
  // --------------------------------------------------------------------------
  override def parseInput(input: FileContent[TextMarkdown.type]): SheetsData = {
    val markdownString = new String(input.data, StandardCharsets.UTF_8)
    val workbook = parseMarkdownToWorkbook(markdownString)
    val (sheets, _) = workbookToSheetsData(workbook)
    workbook.close()
    SheetsData(sheets)
  }

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

  private def parseTableIntoSheet(lines: List[String], sheet: Sheet): Unit = {
    val dataLines = lines.filter(_.trim.startsWith("|"))
    if (dataLines.size < 2) return

    // first line => column headers
    val headerCols = parseMarkdownRow(dataLines.head)
    // skip the row/column label (col 0)
    val colHeaders = headerCols.drop(1)

    // skip any table separators
    val contentLines = dataLines.tail.filterNot(_.matches("""\|\s*-*:?\|\s*.*"""))

    for ((line, i) <- contentLines.zipWithIndex) {
      val cells = parseMarkdownRow(line)
      if (cells.nonEmpty) {
        val rowNumberString = cells.head.trim
        // row is 1-based
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
      case Some("BLANK") => CellType.BLANK
      case Some(_) => CellType.STRING
      case None =>
        if (cleanedValue.matches("""-?\d+(\.\d+)?""")) CellType.NUMERIC
        else if (cleanedValue.equalsIgnoreCase("true") || cleanedValue.equalsIgnoreCase("false")) CellType.BOOLEAN
        else if (cleanedValue.isEmpty) CellType.BLANK
        else CellType.STRING
    }
  }

  private def workbookToSheetsData(workbook: XSSFWorkbook): (List[SheetData], FormulaEvaluator) = {
    val evaluator = workbook.getCreationHelper.createFormulaEvaluator()
    val sheets = (0 until workbook.getNumberOfSheets).map { idx =>
      val sheet = workbook.getSheetAt(idx)
      SheetData.fromSheet(sheet, evaluator)
    }.toList
    (sheets, evaluator)
  }
}