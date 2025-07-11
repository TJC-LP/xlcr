package com.tjclp.xlcr
package renderers.excel

import java.io.ByteArrayOutputStream

import scala.collection.compat._
import scala.util.Try

import org.apache.poi.ss.usermodel._
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel._

import scala.util.Using
import models.FileContent
import models.excel._
import renderers.RendererConfig
import types.MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet

/**
 * Renders SheetsData back to Excel format (XLSX)
 */
class SheetsDataExcelRenderer
    extends SheetsDataRenderer[
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
    ] {

  /** Configuration-aware version that uses the config parameter */
  override def render(
    model: SheetsData,
    config: Option[RendererConfig] = None
  ): FileContent[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type] =
    Try {
      val workbook = new XSSFWorkbook()

      // Extract renderer config if available
      val rendererConfig = config
        .collect { case c: ExcelRendererConfig =>
          c
        }
        .getOrElse(ExcelRendererConfig())

      // Create sheets and populate data
      model.sheets.foreach { sheetData =>
        val sheet = workbook.createSheet(sheetData.name)
        renderSheet(sheet, sheetData, workbook, rendererConfig)
      }

      // Write workbook to bytes
      val output = new ByteArrayOutputStream()
      Using.resource(output) { out =>
        // Set calculation mode based on config
        workbook.setForceFormulaRecalculation(rendererConfig.calculateOnSave)

        workbook.write(out)
        workbook.close()
        FileContent[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type](
          out.toByteArray,
          ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
        )
      }
    }.recover { case ex =>
      throw new RendererError(
        s"Failed to render SheetsData to Excel: ${ex.getMessage}",
        Some(ex)
      )
    }.get

  private def renderSheet(
    sheet: XSSFSheet,
    sheetData: SheetData,
    workbook: XSSFWorkbook,
    config: ExcelRendererConfig = ExcelRendererConfig()
  ): Unit = {
    // Track existing styles/fonts to avoid duplicates
    val styleCache = collection.mutable.Map[CellDataStyle, XSSFCellStyle]()
    val fontCache  = collection.mutable.Map[FontData, XSSFFont]()

    // Create cells and populate data
    sheetData.cells.foreach { cellData =>
      val rowIndex = cellData.rowIndex
      val colIndex = cellData.columnIndex

      // Get or create row
      val row =
        Option(sheet.getRow(rowIndex)).getOrElse(sheet.createRow(rowIndex))

      // Create cell and set value/formula
      val cell = row.createCell(colIndex)
      setCellContent(cell, cellData, config.enableFormulas)

      // Apply styling if present
      applyCellStyle(cell, cellData, workbook, styleCache, fontCache)
    }

    // Apply merged regions
    sheetData.mergedRegions.foreach { regionRef =>
      val cellRangeAddress = CellRangeAddress.valueOf(regionRef)
      sheet.addMergedRegion(cellRangeAddress)
    }

    // Set column widths based on config
    sheet.setDefaultColumnWidth(config.defaultColumnWidth)

    // Auto size columns if requested
    if (config.autoSizeColumns) {
      // Find the maximum column index used in this sheet
      val maxColIndex =
        sheetData.cells.map(_.columnIndex).maxOption.getOrElse(0)

      // Auto-size each column
      for (i <- 0 to maxColIndex)
        try
          sheet.autoSizeColumn(i)
        catch {
          case _: Exception =>
          // Ignore errors in auto-sizing, which can happen if a column is empty
        }
    }

    // Freeze the first row if requested
    if (config.freezeFirstRow && sheetData.rowCount > 1) {
      sheet.createFreezePane(0, 1)
    }
  }

  private def setCellContent(
    cell: XSSFCell,
    cellData: CellData,
    enableFormulas: Boolean = true
  ): Unit =
    cellData.cellType match {
      case "NUMERIC" =>
        cellData.value.foreach { v =>
          cell.setCellValue(v.toDouble)
        }

      case "BOOLEAN" =>
        cellData.value.foreach { v =>
          cell.setCellValue(v.toBoolean)
        }

      case "FORMULA" =>
        if (enableFormulas) {
          cellData.formula.foreach { f =>
            cell.setCellFormula(f)
          }
        } else {
          // If formulas are disabled, use the formatted value if available, otherwise the raw value
          cellData.formattedValue.orElse(cellData.value).foreach { v =>
            cell.setCellValue(v)
          }
        }

      case "ERROR" =>
        cellData.errorValue.foreach { e =>
          cell.setCellErrorValue(e)
        }

      case _ => // String or other types
        cellData.value.foreach { v =>
          cell.setCellValue(v)
        }
    }

  private def applyCellStyle(
    cell: XSSFCell,
    cellData: CellData,
    workbook: XSSFWorkbook,
    styleCache: collection.mutable.Map[CellDataStyle, XSSFCellStyle],
    fontCache: collection.mutable.Map[FontData, XSSFFont]
  ): Unit = {
    // Get or create style based on cellData
    val style = cellData.style.map { styleData =>
      styleCache.getOrElseUpdate(
        styleData,
        createCellStyle(workbook, styleData)
      )
    }

    // Get or create font based on cellData
    val font = cellData.font.map { fontData =>
      fontCache.getOrElseUpdate(fontData, createFont(workbook, fontData))
    }

    // Apply style and font if present
    (style, font) match {
      case (Some(s), Some(f)) =>
        s.setFont(f)
        cell.setCellStyle(s)
      case (Some(s), None) =>
        cell.setCellStyle(s)
      case (None, Some(f)) =>
        val s = workbook.createCellStyle()
        s.setFont(f)
        cell.setCellStyle(s)
      case _ => ()
    }
  }

  private def createCellStyle(
    workbook: XSSFWorkbook,
    style: CellDataStyle
  ): XSSFCellStyle = {
    val xstyle = workbook.createCellStyle()

    // Background color
    style.backgroundColor.foreach { color =>
      val rgb    = parseColor(color)
      val xcolor = new XSSFColor(rgb)
      xstyle.setFillBackgroundColor(xcolor)
    }

    // Foreground color
    style.foregroundColor.foreach { color =>
      val rgb    = parseColor(color)
      val xcolor = new XSSFColor(rgb)
      xstyle.setFillForegroundColor(xcolor)
    }

    // Fill pattern
    style.pattern.foreach { pattern =>
      xstyle.setFillPattern(FillPatternType.valueOf(pattern))
    }

    // Rotation and indentation
    xstyle.setRotation(style.rotation.toShort)
    xstyle.setIndention(style.indention.toShort)

    // Borders
    style.borderTop.foreach(b => xstyle.setBorderTop(BorderStyle.valueOf(b)))
    style.borderRight.foreach { b =>
      xstyle.setBorderRight(BorderStyle.valueOf(b))
    }
    style.borderBottom.foreach { b =>
      xstyle.setBorderBottom(BorderStyle.valueOf(b))
    }
    style.borderLeft.foreach { b =>
      xstyle.setBorderLeft(BorderStyle.valueOf(b))
    }

    // Border colors
    style.borderColors.foreach { case (side, color) =>
      val rgb    = parseColor(color)
      val xcolor = new XSSFColor(rgb)
      side match {
        case "top"    => xstyle.setTopBorderColor(xcolor)
        case "right"  => xstyle.setRightBorderColor(xcolor)
        case "bottom" => xstyle.setBottomBorderColor(xcolor)
        case "left"   => xstyle.setLeftBorderColor(xcolor)
        case _        => ()
      }
    }

    xstyle
  }

  private def createFont(workbook: XSSFWorkbook, font: FontData): XSSFFont = {
    val xfont = workbook.createFont()

    xfont.setFontName(font.name)
    font.size.foreach(x => xfont.setFontHeightInPoints(x.toShort))
    xfont.setBold(font.bold)
    xfont.setItalic(font.italic)
    font.underline.foreach(xfont.setUnderline)
    xfont.setStrikeout(font.strikeout)

    // Set color if present
    font.rgbColor.foreach { color =>
      val rgb    = parseColor(color)
      val xcolor = new XSSFColor(rgb)
      xfont.setColor(xcolor)
    }

    xfont
  }

  private def parseColor(hexColor: String): Array[Byte] = {
    // Parse "#RRGGBB" format
    require(
      hexColor.startsWith("#") && hexColor.length == 7,
      s"Invalid color format: $hexColor, expected #RRGGBB"
    )

    val rgb = hexColor
      .substring(1)
      .sliding(2, 2)
      .map { hex =>
        Integer.parseInt(hex, 16).toByte
      }
      .toArray

    require(rgb.length == 3, s"Invalid RGB color: $hexColor")
    rgb
  }
}
