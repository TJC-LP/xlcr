package com.tjclp.xlcr
package bridges.excel

import bridges.SymmetricBridge
import models.FileContent
import models.excel.{SheetData, SheetsData}
import types.MimeType
import types.MimeType.TextMarkdown

/**
 * ExcelMarkdownOutputBridge produces a Markdown representation
 * of SheetsData. This replaces ExcelMarkdownParser logic.
 */
object ExcelMarkdownOutputBridge extends SymmetricBridge[
  SheetsData,
  MimeType.TextMarkdown.type
] {

  override def render(model: SheetsData): FileContent[MimeType.TextMarkdown.type] = {
    val markdownString = model.sheets.map(sheetToMarkdown).mkString("\n\n---\n\n")
    FileContent(markdownString.getBytes("UTF-8"), TextMarkdown)
  }

  /**
   * Convert a single SheetData to markdown.
   */
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
      for (col <- columnHeaders.indices) {
        // Build an A1 reference
        val ref = s"${columnHeaders(col)}$row"
        val cell = sheetData.cells.find(_.address == ref)
        val cellContent = cell.map(formatCellContent).getOrElse("")
        sb.append(s" $cellContent |")
      }
      sb.append("\n")
    }

    sb.toString
  }

  private def generateColumnHeaders(count: Int): Seq[String] = {
    def toColumnName(n: Int): String = {
      if (n < 0) ""
      else {
        val quotient = n / 26
        val remainder = n % 26
        if (quotient == 0) (remainder + 'A').toChar.toString
        else toColumnName(quotient - 1) + (remainder + 'A').toChar
      }
    }
    (0 until count).map(toColumnName)
  }

  private def formatCellContent(cell: models.excel.CellData): String = {
    val value = escapeMarkdown(cell.formattedValue.getOrElse(cell.value.getOrElse("")))
    val details = List(
      Some(s"REF:``${cell.referenceA1}``"),
      Some(s"TYPE:``${cell.cellType}``"),
      cell.formula.map(f => s"FORMULA:``$f``"),
      cell.dataFormat.map(fmt => s"STYLE:``$fmt``")
    ).flatten.mkString("<br>")
    s"VALUE:``$value``<br>$details"
  }

  private def escapeMarkdown(s: String): String =
    s.replace("|", "\\|").replace("\n", "<br>")
}