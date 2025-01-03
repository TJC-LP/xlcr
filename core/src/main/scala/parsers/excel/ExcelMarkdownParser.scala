package com.tjclp.xlcr
package parsers.excel

import models.{CellData, Content, SheetData}
import types.MimeType
import types.MimeType.TextMarkdown

import org.apache.poi.ss.usermodel.WorkbookFactory

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import scala.jdk.CollectionConverters.*
import scala.util.Try

object ExcelMarkdownParser extends ExcelParser:
  def outputType: MimeType = TextMarkdown

  def extractContent(input: Path, output: Option[Path] = None): Try[Content] = Try:
    val markdownContent = parseToMarkdown(input)
    Content(
      data = markdownContent.getBytes(StandardCharsets.UTF_8),
      contentType = TextMarkdown.mimeType,
      metadata = Map("Format" -> "Markdown")
    )

  private def parseToMarkdown(input: Path): String =
    val workbook = WorkbookFactory.create(input.toFile)
    try
      val evaluator = workbook.getCreationHelper.createFormulaEvaluator()
      workbook.asScala.map { sheet =>
        sheetToMarkdown(SheetData.fromSheet(sheet, evaluator))
      }.mkString("\n\n---\n\n")
    finally
      workbook.close()
  end parseToMarkdown

  private def sheetToMarkdown(sheetData: SheetData): String =
    val sb = StringBuilder()
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
    for row <- 1 to sheetData.rowCount do
      sb.append(f"| $row%3d |")
      for col <- columnHeaders do
        val cellRef = s"$col$row"
        val maybeCellData = sheetData.cells.find(_.address == cellRef)
        val cellContent = maybeCellData match
          case Some(cd) => formatCellContent(cd)
          case None => ""
        sb.append(s" $cellContent |")
      sb.append("\n")

    sb.toString()
  end sheetToMarkdown

  private def generateColumnHeaders(columnCount: Int): Seq[String] =
    def toColumnName(n: Int): String =
      if n < 0 then ""
      else
        val quotient = n / 26
        val remainder = n % 26
        if quotient == 0 then
          ('A' + remainder).toChar.toString
        else
          toColumnName(quotient - 1) + ('A' + remainder).toChar
    end toColumnName

    (0 until columnCount).map(toColumnName)
  end generateColumnHeaders

  private def formatCellContent(cellData: CellData): String =
    val value = escapeMarkdown(cellData.formattedValue.getOrElse(cellData.value.getOrElse("")))
    val otherDetails = List(
      Some(s"REF:``${cellData.referenceA1}``"),
      Some(s"TYPE:``${cellData.cellType}``"),
      cellData.formula.map(f => s"FORMULA:``$f``"),
      cellData.dataFormat.map(s => s"STYLE:``$s``"),
      cellTypeDetails(cellData)
    ).flatten.mkString("<br>")
    s"VALUE:``$value``<br>$otherDetails"

  private def cellTypeDetails(cellData: CellData): Option[String] =
    cellData.cellType match
      case "NUMERIC" =>
        cellData.value.flatMap { v =>
          try Some(s"DOUBLE:``${v.toDouble}``")
          catch case _: NumberFormatException => None
        }
      case "DATE" =>
        cellData.value.map(v => s"DATE:``$v``")
      case _ => None

  private def escapeMarkdown(s: String): String =
    s.replace("|", "\\|").replace("\n", "<br>")
