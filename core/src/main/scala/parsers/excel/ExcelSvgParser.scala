package com.tjclp.xlcr
package parsers.excel

import models.Content
import models.excel.{FontData, SheetData}
import types.MimeType

import org.apache.poi.ss.usermodel.WorkbookFactory
import org.slf4j.LoggerFactory

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import scala.collection.mutable
import scala.util.Try

/**
 * ExcelSvgParser loads an Excel workbook, converts it to SheetData,
 * and then generates an SVG representation.
 *
 * We separate layout computations from final SVG string building.
 */
object ExcelSvgParser extends ExcelParser:

  private val logger = LoggerFactory.getLogger(getClass)

  override def extractContent(input: Path, output: Option[Path] = None): Try[Content] =
    Try {
      // 1) Read workbook
      val workbook = WorkbookFactory.create(input.toFile)
      val evaluator = workbook.getCreationHelper.createFormulaEvaluator()

      // 2) Convert each sheet to a SheetData
      val sheets = for idx <- 0 until workbook.getNumberOfSheets yield {
        val sheet = workbook.getSheetAt(idx)
        SheetData.fromSheet(sheet, evaluator)
      }

      // 3) Build an internal data structure of layout info for all sheets
      val (uniqueFonts, svgBody, totalWidth, totalHeight) = buildSvgLayout(sheets.toList)

      // 4) Construct full SVG string
      val finalSvg = buildCompleteSvg(uniqueFonts, svgBody, totalWidth, totalHeight)
      workbook.close()

      // 5) Return as Content
      Content(
        data = finalSvg.getBytes(StandardCharsets.UTF_8),
        contentType = outputType.mimeType,
        metadata = Map("Description" -> "Enhanced Excel to SVG conversion with style/color/font preservation")
      )
    }

  override def outputType: MimeType = MimeType.ImageSvgXml

  /** Build a layout for all sheets, returning the accumulated fonts, the SVG body, and total dimensions. */
  private def buildSvgLayout(sheets: List[SheetData]): (Set[FontData], String, Int, Int) =
    val rowHeight = 20
    val colWidth = 100
    val spacing = 50

    var currentY = 0
    var maxWidth = 0
    val uniqueFonts = mutable.Set.empty[FontData]

    val sb = new StringBuilder

    for sheetData <- sheets do
      val localWidth = sheetData.columnCount * colWidth
      if localWidth > maxWidth then maxWidth = localWidth

      sb.append(s"  <!-- Sheet: ${sheetData.name} -->\n")

      // Render cells
      for row <- 0 until sheetData.rowCount do
        for col <- 0 until sheetData.columnCount do
          val xPos = col * colWidth
          val yPos = currentY + row * rowHeight
          val cellOpt = sheetData.cells.find(c => c.rowIndex == row && c.columnIndex == col)

          // default styling
          var fillColor = "#FFFFFF"
          var strokeColor = "#CCCCCC"
          var strokeWidth = "1"
          var textValue = ""
          var fontColor = "#000000"
          var fontSize = "12"
          var fontWeight = "normal"
          var fontStyle = "normal"
          var maybeFontData: Option[FontData] = None

          cellOpt.foreach { cd =>
            textValue = cd.formattedValue.getOrElse(cd.value.getOrElse(""))
            // style
            cd.style.foreach { st =>
              fillColor = st.foregroundColor.getOrElse(fillColor)
              strokeColor = "#808080"
            }
            // font
            cd.font.foreach { f =>
              uniqueFonts += f
              if f.rgbColor.isDefined then fontColor = f.rgbColor.get
              if f.size.isDefined then fontSize = f.size.get.toString
              fontWeight = if f.bold then "bold" else "normal"
              fontStyle = if f.italic then "italic" else "normal"
              maybeFontData = Some(f)
            }
          }

          // <rect>
          sb.append(
            s"""  <rect x="$xPos" y="$yPos" width="$colWidth" height="$rowHeight" fill="$fillColor" stroke="$strokeColor" stroke-width="$strokeWidth" />\n"""
          )

          // <text>
          val textX = xPos + 5
          val textY = yPos + (rowHeight * 0.7).toInt
          maybeFontData match
            case Some(fd) =>
              val fontIdx = uniqueFonts.toList.indexOf(fd)
              sb.append(s"""  <text x="$textX" y="$textY" class="font-$fontIdx" fill="$fontColor" font-size="${fd.size.getOrElse(12)}px" font-weight="${if fd.bold then "bold" else "normal"}" font-style="${if fd.italic then "italic" else "normal"}" text-decoration="${decorationStyles(fd)}">${escapeXml(textValue)}</text>\n""")
            case None =>
              if textValue.nonEmpty then
                sb.append(s"""  <text x="$textX" y="$textY" fill="$fontColor" font-size="${fontSize}px" font-weight="$fontWeight" font-style="$fontStyle">${escapeXml(textValue)}</text>\n""")

      currentY += sheetData.rowCount * rowHeight + spacing

    (uniqueFonts.toSet, sb.toString, maxWidth, currentY)

  /** If underline > 0, we add 'underline', if strikeout => line-through. */
  private def decorationStyles(font: FontData): String =
    val decorations = mutable.ListBuffer.empty[String]
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

  /** Build final SVG, injecting the <defs> style info and the main body. */
  private def buildCompleteSvg(fonts: Set[FontData], body: String, totalWidth: Int, totalHeight: Int): String =
    // *** Add the XML declaration up front, no leading spaces. ***
    val sb = new StringBuilder
    sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="no"?>""").append("\n")
    sb.append(s"<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" width=\"$totalWidth\" height=\"$totalHeight\">\n")
    sb.append(generateFontStyles(fonts))
    sb.append(body)
    sb.append("</svg>\n")
    sb.toString

  /** Produce CSS classes for each unique font family. */
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

  override def supportedInputTypes: Set[MimeType] =
    Set(MimeType.ApplicationVndMsExcel, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)