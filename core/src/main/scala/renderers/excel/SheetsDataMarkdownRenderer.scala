package com.tjclp.xlcr
package renderers.excel

import models.FileContent
import models.excel.{SheetData, SheetsData}
import types.MimeType.TextMarkdown

import java.nio.charset.StandardCharsets

class SheetsDataMarkdownRenderer
    extends SheetsDataSimpleRenderer[TextMarkdown.type] {
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
    val value = escapeMarkdown(
      cellData.formattedValue.getOrElse(cellData.value.getOrElse(""))
    )
    val otherDetails = List(
      Some(s"REF:``${cellData.referenceA1}``"),
      Some(s"TYPE:``${cellData.cellType}``"),
      cellData.formula.map(f => s"FORMULA:``$f``"),
      cellData.dataFormat.map(fmt => s"STYLE:``$fmt``")
    ).flatten.mkString("<br>")
    s"VALUE:``$value``<br>$otherDetails"
  }

  private def escapeMarkdown(s: String): String = {
    s.replace("|", "\\|").replace("\n", "<br>")
  }

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
}
