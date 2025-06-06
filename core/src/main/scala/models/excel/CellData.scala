package com.tjclp.xlcr
package models.excel

import org.apache.poi.ss.usermodel._
import org.apache.poi.xssf.usermodel.{ XSSFCellStyle, XSSFColor, XSSFFont }

import models.excel.ExcelReference.{ Cell => RefCell, Col, Row }
import utils.excel.ExcelUtils

final case class CellData(
  referenceA1: String,
  cellType: String,
  value: Option[String] = None,
  formula: Option[String] = None,
  errorValue: Option[Byte] = None,
  comment: Option[String] = None,
  commentAuthor: Option[String] = None,
  hyperlink: Option[String] = None,
  dataFormat: Option[String] = None,
  formattedValue: Option[String] = None,
  font: Option[FontData] = None,
  style: Option[CellDataStyle] = None,
  hidden: Boolean = false
) {
  def rowIndex: Int = ExcelUtils.a1ToReference(referenceA1)._1

  def columnIndex: Int = ExcelUtils.a1ToReference(referenceA1)._2

  def sheetName: Option[String] = ExcelUtils.a1ToSheetAndAddress(referenceA1)._1

  def address: String = ExcelUtils.a1ToSheetAndAddress(referenceA1)._2
}

object CellData extends CellDataCodecs {

  /**
   * Convert a POI cell to our CellData representation.
   */
  def fromCell(
    cell: Cell,
    sheetName: String,
    evaluator: FormulaEvaluator,
    formatter: DataFormatter
  ): CellData = {
    val ref = RefCell(
      sheet = sheetName,
      row = Row(cell.getRowIndex + 1),
      col = Col(cell.getColumnIndex)
    )

    val cellType     = cell.getCellType
    val cellTypeName = cellType.name()

    val (valueOpt, formulaOpt, errorOpt, formattedOpt) =
      extractCellData(cell, cellType, evaluator, formatter)

    val commentData                    = extractCommentData(cell)
    val hyperlinkOpt                   = Option(cell.getHyperlink).map(_.getAddress)
    val (dataFormatOpt, overallHidden) = extractVisibilityData(cell)
    val fontData                       = extractFontData(cell)
    val styleData                      = extractStyleData(cell)

    CellData(
      referenceA1 = ref.toA1,
      cellType = cellTypeName,
      value = valueOpt,
      formula = formulaOpt,
      errorValue = errorOpt,
      comment = commentData._1,
      commentAuthor = commentData._2,
      hyperlink = hyperlinkOpt,
      dataFormat = dataFormatOpt,
      formattedValue = formattedOpt,
      font = fontData,
      style = styleData,
      hidden = overallHidden
    )
  }

  private def extractCellData(
    cell: Cell,
    cellType: CellType,
    evaluator: FormulaEvaluator,
    formatter: DataFormatter
  ): (Option[String], Option[String], Option[Byte], Option[String]) = {
    def extractFormulaData(formulaCell: Cell)
      : (Option[String], Option[String], Option[Byte], Option[String]) =
      try {
        val evaluatedValue = evaluator.evaluate(formulaCell)
        if (evaluatedValue != null) {
          evaluatedValue.getCellType match {
            case CellType.STRING =>
              (Some(evaluatedValue.getStringValue), Some(formulaCell.getCellFormula), None, None)
            case CellType.NUMERIC =>
              (
                Some(evaluatedValue.getNumberValue.toString),
                Some(formulaCell.getCellFormula),
                None,
                Some(formatter.formatCellValue(formulaCell, evaluator))
              )
            case CellType.BOOLEAN =>
              (
                Some(evaluatedValue.getBooleanValue.toString),
                Some(formulaCell.getCellFormula),
                None,
                None
              )
            case CellType.ERROR =>
              (None, Some(formulaCell.getCellFormula), Some(evaluatedValue.getErrorValue), None)
            case CellType.BLANK =>
              (None, Some(formulaCell.getCellFormula), None, None)
            case CellType.FORMULA =>
              // Recursive case: if we get FORMULA again, try to evaluate it
              extractFormulaData(formulaCell)
            case _ =>
              (None, Some(formulaCell.getCellFormula), None, None)
          }
        } else (None, Some(formulaCell.getCellFormula), None, None)
      } catch {
        case _: Exception =>
          // Fallback to cached formula result
          val cachedFormulaType = formulaCell.getCachedFormulaResultType
          val fallbackFormula =
            try Some(formulaCell.getCellFormula)
            catch { case _: Exception => None }

          cachedFormulaType match {
            case CellType.STRING =>
              (Some(formulaCell.getStringCellValue), fallbackFormula, None, None)
            case CellType.NUMERIC =>
              (Some(formulaCell.getNumericCellValue.toString), fallbackFormula, None, None)
            case CellType.BOOLEAN =>
              (Some(formulaCell.getBooleanCellValue.toString), fallbackFormula, None, None)
            case CellType.ERROR =>
              (None, fallbackFormula, Some(formulaCell.getErrorCellValue), None)
            case _ =>
              (None, fallbackFormula, None, None)
          }
      }

    cellType match {
      case CellType.STRING =>
        (Some(cell.getStringCellValue), None, None, None)
      case CellType.NUMERIC =>
        (Some(cell.getNumericCellValue.toString), None, None, Some(formatter.formatCellValue(cell)))
      case CellType.BOOLEAN =>
        (Some(cell.getBooleanCellValue.toString), None, None, None)
      case CellType.FORMULA =>
        extractFormulaData(cell)
      case CellType.ERROR =>
        (None, None, Some(cell.getErrorCellValue), None)
      case CellType.BLANK =>
        (None, None, None, None)
      case _ =>
        (None, None, None, None)
    }
  }

  private def extractCommentData(cell: Cell): (Option[String], Option[String]) =
    Option(cell.getCellComment).map(comment =>
      (Some(comment.getString.getString), Some(comment.getAuthor))
    ).getOrElse((None, None))

  private def extractVisibilityData(cell: Cell): (Option[String], Boolean) = {
    val style         = cell.getCellStyle
    val dataFormatOpt = Option(style).map(_.getDataFormatString)
    val row           = cell.getRow
    val isCellHidden  = Option(style).exists(_.getHidden)
    val isRowHidden =
      row != null && (row.getZeroHeight || Option(row.getRowStyle).exists(_.getHidden))
    val isColumnHidden = cell.getSheet.isColumnHidden(cell.getColumnIndex)
    val overallHidden  = isCellHidden || isRowHidden || isColumnHidden
    (dataFormatOpt, overallHidden)
  }

  private def extractFontData(cell: Cell): Option[FontData] =
    Option(cell.getCellStyle).map { style =>
      val font = cell.getSheet.getWorkbook.getFontAt(style.getFontIndex)
      fromPoiFont(font)
    }

  private def extractStyleData(cell: Cell): Option[CellDataStyle] =
    Option(cell.getCellStyle).map { style =>
      fromPoiCellStyle(style.asInstanceOf[XSSFCellStyle])
    }

  /**
   * Helper to convert a POI Font to FontData.
   */
  private def fromPoiFont(font: org.apache.poi.ss.usermodel.Font): FontData =
    // Because we are referencing methods from FontData, we simply create it directly here.
    FontData(
      name = font.getFontName,
      size = Some(font.getFontHeightInPoints),
      bold = font.getBold,
      italic = font.getItalic,
      underline = Some(font.getUnderline),
      strikeout = font.getStrikeout,
      colorIndex = Some(font.getColor),
      rgbColor = font match {
        case xf: XSSFFont => colorToRgb(xf.getXSSFColor)
        case _            => None
      }
    )

  /**
   * Helper to convert a POI XSSFCellStyle to CellStyle.
   */
  private def fromPoiCellStyle(xstyle: XSSFCellStyle): CellDataStyle =
    CellDataStyle(
      backgroundColor = colorToRgb(xstyle.getFillBackgroundXSSFColor),
      foregroundColor = colorToRgb(xstyle.getFillForegroundXSSFColor),
      pattern = Some(xstyle.getFillPattern.toString),
      rotation = xstyle.getRotation,
      indention = xstyle.getIndention,
      borderTop = Some(xstyle.getBorderTop.toString),
      borderRight = Some(xstyle.getBorderRight.toString),
      borderBottom = Some(xstyle.getBorderBottom.toString),
      borderLeft = Some(xstyle.getBorderLeft.toString),
      borderColors = Map(
        "top"    -> colorToRgb(xstyle.getTopBorderXSSFColor),
        "right"  -> colorToRgb(xstyle.getRightBorderXSSFColor),
        "bottom" -> colorToRgb(xstyle.getBottomBorderXSSFColor),
        "left"   -> colorToRgb(xstyle.getLeftBorderXSSFColor)
      ).collect { case (k, Some(v)) => k -> v }
    )

  /**
   * Helper to convert an XSSFColor to a hex RGB string, e.g. #RRGGBB
   */
  private def colorToRgb(color: XSSFColor): Option[String] =
    Option(color).flatMap(c => Option(c.getRGB)).map { arr =>
      f"#${arr(0) & 0xff}%02X${arr(1) & 0xff}%02X${arr(2) & 0xff}%02X"
    }
}
