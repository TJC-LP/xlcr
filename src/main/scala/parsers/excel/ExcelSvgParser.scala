package com.tjclp.xlcr
package parsers.excel

import models.{CellStyle, Content, FontData, SheetData}
import types.MimeType

import org.apache.poi.ss.usermodel.WorkbookFactory
import org.slf4j.LoggerFactory

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import scala.util.Try

/**
 * ExcelSvgParser loads an Excel workbook, converts it to SheetData,
 * and then generates an SVG representation. This enhanced version
 * attempts to preserve font, style, color, and borders for each cell.
 */
object ExcelSvgParser extends ExcelParser:

  private val logger = LoggerFactory.getLogger(getClass)

  override def extractContent(input: Path): Try[Content] = Try {
    val workbook = WorkbookFactory.create(input.toFile)
    val evaluator = workbook.getCreationHelper.createFormulaEvaluator()

    // We'll define some layout constants:
    // - rowHeight and colWidth can be adjusted or calculated dynamically from POI data if needed.
    val rowHeight = 20
    val colWidth = 100
    val spacing = 50 // space added after each sheet

    // We'll keep track of the max X and Y offsets so we can define the overall SVG width and height
    var totalWidth = 0
    var currentY = 0

    val sb = new StringBuilder
    sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="no"?>""").append("\n")

    // Track unique fonts during processing
    var uniqueFonts = Set.empty[FontData]

    // We'll accumulate all sheets' rendering in sheetBuffer, track maximum width, etc.
    val sheetBuffer = new StringBuilder

    val numberOfSheets = workbook.getNumberOfSheets
    var maxWidthThisSheet = 0

    for idx <- 0 until numberOfSheets do
      val sheet = workbook.getSheetAt(idx)
      val sheetData = SheetData.fromSheet(sheet, evaluator)

      // We compute the width for the sheet (sheetData.columnCount * colWidth)
      val localWidth = sheetData.columnCount * colWidth
      if localWidth > maxWidthThisSheet then
        maxWidthThisSheet = localWidth

      sheetBuffer.append(s"  <!-- Sheet: ${sheetData.name} -->\n")

      // For each row and column, draw a cell-level rectangle and text.
      for row <- 0 until sheetData.rowCount do
        for col <- 0 until sheetData.columnCount do
          val xPos = col * colWidth
          val yPos = currentY + row * rowHeight

          // We try to find the matching CellData for this row/column
          val cellOpt = sheetData.cells.find(c => c.rowIndex == row && c.columnIndex == col)

          // Declare variables outside the foreach so they're accessible later
          var fillColor = "#FFFFFF" // White cell background by default
          var strokeColor = "#CCCCCC" // A light gray border
          var strokeWidth = "1"
          var textValue = ""
          var fontColor = "#000000"
          var fontSize = "12"
          var fontWeight = "normal"
          var fontStyle = "normal"
          var currentFontData: Option[FontData] = None

          cellOpt.foreach { cellData =>
            // Extract text to display
            textValue = cellData.formattedValue.getOrElse(cellData.value.getOrElse(""))

            // Retrieve style and font data if present
            val styleData: Option[CellStyle] = cellData.style
            val fontData: Option[FontData] = cellData.font
            currentFontData = fontData

            // If a background color is provided, use that
            // We'll pick 'foregroundColor' or 'backgroundColor' if it exists in CellStyle
            styleData.foreach { st =>
              fillColor = st.foregroundColor.getOrElse(fillColor)
              strokeColor = "#808080" // could pick something else or derive from style
              // Could also handle st.border... logic here if we want varying stroke styles
            }

            // Font color, bold, italic, etc.
            fontData.foreach { f =>
              uniqueFonts += f // Add to our collection of unique fonts
              if f.rgbColor.isDefined then fontColor = f.rgbColor.get
              if f.size.isDefined then fontSize = f.size.get.toString
              fontWeight = if f.bold then "bold" else "normal"
              fontStyle = if f.italic then "italic" else "normal"
            }
          }

          // Generate the <rect> for the cell
          sheetBuffer.append(s"""  <rect x="$xPos" y="$yPos" width="$colWidth" height="$rowHeight" fill="$fillColor" stroke="$strokeColor" stroke-width="$strokeWidth" />\n""")

          // Generate the <text> for the cell with complete styling
          val textX = xPos + 5
          val textY = yPos + (rowHeight * 0.7).toInt // baseline shift within the cell

          // Only if we have some font data do we proceed
          currentFontData match
            case Some(f) =>
              val fontIdx = uniqueFonts.toList.indexOf(f)
              val fontClass = s"font-$fontIdx"
              sheetBuffer.append(
                s"""  <text x="$textX" y="$textY"
                    class="$fontClass"
                    fill="$fontColor"
                    font-size="${f.size.getOrElse(12)}px"
                    font-weight="${if f.bold then "bold" else "normal"}"
                    font-style="${if f.italic then "italic" else "normal"}"
                    text-decoration="${decorationStyles(f)}">${escapeXml(textValue)}</text>\n"""
              )
            case None =>
              // We can choose to still print text if desired, or do nothing
              if textValue.nonEmpty then
                sheetBuffer.append(
                  s"""  <text x="$textX" y="$textY"
                      fill="$fontColor"
                      font-size="${fontSize}px"
                      font-weight="$fontWeight"
                      font-style="$fontStyle">${escapeXml(textValue)}</text>\n"""
                )

      // After finishing all rows in the current sheet, increment currentY by rowCount * rowHeight + spacing
      currentY += sheetData.rowCount * rowHeight + spacing

    // totalWidth is the maximum width among the sheets
    totalWidth = maxWidthThisSheet
    val totalHeight = currentY

    // Build the final SVG
    sb.append(s"""<svg xmlns="http://www.w3.org/2000/svg" version="1.1" width="$totalWidth" height="$totalHeight">\n""")
    sb.append(generateFontStyles(uniqueFonts))
    sb.append(sheetBuffer)
    sb.append("</svg>\n")

    val svgData = sb.toString().getBytes(StandardCharsets.UTF_8)
    workbook.close()

    Content(
      data = svgData,
      contentType = outputType.mimeType,
      metadata = Map("Description" -> "Enhanced Excel to SVG conversion with style/color/font preservation")
    )
  }

  override def outputType: MimeType = MimeType.ImageSvgXml

  private def generateFontStyles(fonts: Set[FontData]): String =
    val sb = new StringBuilder
    sb.append("  <defs>\n")
    sb.append("    <style type=\"text/css\"><![CDATA[\n")
    fonts.zipWithIndex.foreach { case (font, idx) =>
      sb.append(
        s"""      .font-$idx {
        font-family: '${font.name}', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      }""")
      sb.append("\n")
    }
    sb.append("    ]]></style>\n")
    sb.append("  </defs>\n")
    sb.toString

  private def decorationStyles(font: FontData): String =
    val decorations = scala.collection.mutable.ListBuffer.empty[String]
    if font.underline.exists(_ > 0) then decorations += "underline"
    if font.strikeout then decorations += "line-through"
    decorations.mkString(" ")

  private def escapeXml(str: String): String =
    str
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")

  override def supportedInputTypes: Set[MimeType] =
    Set(MimeType.ApplicationVndMsExcel, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)