package com.tjclp.xlcr
package renderers.excel

import models.FileContent
import models.excel.{FontData, SheetData, SheetsData}
import renderers.Renderer
import types.MimeType.ImageSvgXml

import scala.collection.mutable

/**
 * ExcelSvgOutputBridge produces an SVG representation of SheetsData.
 * This replaces ExcelSvgParser logic.
 */
class SheetsDataSvgRenderer extends SheetsDataRenderer[ImageSvgXml.type] {

  override def render(model: SheetsData): FileContent[ImageSvgXml.type] = {
    val (uniqueFonts, svgBody, totalWidth, totalHeight) = buildSvgLayout(model.sheets)
    val finalSvg = buildCompleteSvg(uniqueFonts, svgBody, totalWidth, totalHeight)
    FileContent(finalSvg.getBytes("UTF-8"), ImageSvgXml)
  }

  private def buildSvgLayout(sheets: List[SheetData]): (Set[FontData], String, Int, Int) = {
    val rowHeight = 20
    val colWidth = 100
    val spacing = 50

    var currentY = 0
    var maxWidth = 0
    val uniqueFonts = mutable.Set.empty[FontData]
    val sb = new StringBuilder

    for (sheetData <- sheets) {
      val localWidth = sheetData.columnCount * colWidth
      if (localWidth > maxWidth) maxWidth = localWidth

      sb.append(s"  <!-- Sheet: ${sheetData.name} -->\n")

      for (row <- 0 until sheetData.rowCount) {
        for (col <- 0 until sheetData.columnCount) {
          val xPos = col * colWidth
          val yPos = currentY + row * rowHeight
          val cellOpt = sheetData.cells.find(c => c.rowIndex == row && c.columnIndex == col)

          // Default style
          var fillColor = "#FFFFFF"
          var strokeColor = "#CCCCCC"
          val strokeWidth = "1"
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
              if (f.rgbColor.isDefined) fontColor = f.rgbColor.get
              if (f.size.isDefined) fontSize = f.size.get.toString
              fontWeight = if (f.bold) "bold" else "normal"
              fontStyle = if (f.italic) "italic" else "normal"
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

          maybeFontData match {
            case Some(fd) =>
              val fontIdx = uniqueFonts.toList.indexOf(fd)
              sb.append(
                s"""  <text x="$textX" y="$textY" class="font-$fontIdx" fill="$fontColor" font-size="${fd.size.getOrElse(12)}px" font-weight="${if (fd.bold) "bold" else "normal"}" font-style="${if (fd.italic) "italic" else "normal"}" text-decoration="${decorationStyles(fd)}">${escapeXml(textValue)}</text>\n"""
              )
            case None =>
              if (textValue.nonEmpty) {
                sb.append(
                  s"""  <text x="$textX" y="$textY" fill="$fontColor" font-size="${fontSize}px" font-weight="$fontWeight" font-style="$fontStyle">${escapeXml(textValue)}</text>\n"""
                )
              }
          }
        }
      }
      currentY += sheetData.rowCount * rowHeight + spacing
    }

    (uniqueFonts.toSet, sb.toString, maxWidth, currentY)
  }

  private def decorationStyles(font: FontData): String = {
    val decorations = mutable.ListBuffer.empty[String]
    if (font.underline.exists(_ > 0)) decorations += "underline"
    if (font.strikeout) decorations += "line-through"
    decorations.mkString(" ")
  }

  private def escapeXml(str: String): String = {
    str
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")
  }

  private def buildCompleteSvg(fonts: Set[FontData], body: String, totalWidth: Int, totalHeight: Int): String = {
    val sb = new StringBuilder
    sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="no"?>""").append("\n")
    sb.append(s"<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" width=\"$totalWidth\" height=\"$totalHeight\">\n")
    sb.append(generateFontStyles(fonts))
    sb.append(body)
    sb.append("</svg>\n")
    sb.toString
  }

  private def generateFontStyles(fonts: Set[FontData]): String = {
    val sb = new StringBuilder
    sb.append("  <defs>\n")
    sb.append("    <style type=\"text/css\"><![CDATA[\n")
    fonts.zipWithIndex.foreach { case (font, idx) =>
      sb.append(
        s"""      .font-$idx {
        font-family: '${font.name}', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      }"""
      )
      sb.append("\n")
    }
    sb.append("    ]]></style>\n")
    sb.append("  </defs>\n")
    sb.toString
  }
}