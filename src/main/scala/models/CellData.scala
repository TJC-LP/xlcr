package com.tjclp.xlcr
package models

import models.ExcelReference.{Col, Row, Cell as RefCell}

import io.circe.*
import io.circe.generic.semiauto.*
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.{XSSFCellStyle, XSSFColor, XSSFFont}

final case class CellData(
                           referenceA1: String,
                           rowIndex: Int,
                           columnIndex: Int,
                           address: String,
                           cellType: String,
                           value: Option[String],
                           formula: Option[String],
                           errorValue: Option[Byte],
                           comment: Option[String],
                           commentAuthor: Option[String],
                           hyperlink: Option[String],
                           dataFormat: Option[String],
                           formattedValue: Option[String],
                           font: Option[FontData] = None,
                           style: Option[com.tjclp.xlcr.models.CellStyle] = None
                         )

object CellData:
  implicit val encoder: Encoder[CellData] = deriveEncoder[CellData]
  implicit val decoder: Decoder[CellData] = deriveDecoder[CellData]

  /**
   * Convert a POI cell to our CellData representation.
   */
  def fromCell(cell: Cell, sheetName: String, evaluator: FormulaEvaluator, formatter: DataFormatter): CellData =
    val ref = RefCell(
      sheet = sheetName,
      row = Row(cell.getRowIndex + 1),
      col = Col(cell.getColumnIndex)
    )

    val cellType = cell.getCellType
    val cellTypeName = cellType.name()
    var valueOpt = Option.empty[String]
    var formulaOpt = Option.empty[String]
    var errorOpt = Option.empty[Byte]
    var formattedOpt = Option.empty[String]

    cellType match
      case CellType.STRING =>
        valueOpt = Some(cell.getStringCellValue)
      case CellType.NUMERIC =>
        valueOpt = Some(cell.getNumericCellValue.toString)
        formattedOpt = Some(formatter.formatCellValue(cell))
      case CellType.BOOLEAN =>
        valueOpt = Some(cell.getBooleanCellValue.toString)
      case CellType.FORMULA =>
        formulaOpt = Some(cell.getCellFormula)
        val evaluatedValue = evaluator.evaluate(cell)
        if evaluatedValue != null then
          evaluatedValue.getCellType match
            case CellType.STRING =>
              valueOpt = Some(evaluatedValue.getStringValue)
            case CellType.NUMERIC =>
              valueOpt = Some(evaluatedValue.getNumberValue.toString)
              formattedOpt = Some(formatter.formatCellValue(cell, evaluator))
            case CellType.BOOLEAN =>
              valueOpt = Some(evaluatedValue.getBooleanValue.toString)
            case CellType.ERROR =>
              errorOpt = Some(evaluatedValue.getErrorValue)
            case CellType.BLANK =>
              valueOpt = None
            case _ => ()
      case CellType.ERROR =>
        errorOpt = Some(cell.getErrorCellValue)
      case CellType.BLANK =>
        // do nothing
        ()
      case _ => ()

    // Comments
    val commentObj = Option(cell.getCellComment)
    val commentAuthorOpt = commentObj.map(_.getAuthor)
    val commentTextOpt = commentObj.map(_.getString.getString)

    // Hyperlink
    val hyperlinkOpt = Option(cell.getHyperlink).map(_.getAddress)

    // Style & data format
    val style = cell.getCellStyle
    val dataFormatOpt = Option(style).map(_.getDataFormatString)

    // Extract font
    val fontData = Option(style).map { st =>
      val font = cell.getSheet.getWorkbook.getFontAt(st.getFontIndex)
      fromPoiFont(font)
    }

    // Extract style
    val styleData = Option(style).map { st =>
      fromPoiCellStyle(st.asInstanceOf[XSSFCellStyle])
    }

    CellData(
      referenceA1 = ref.toA1,
      rowIndex = cell.getRowIndex,
      columnIndex = cell.getColumnIndex,
      address = cell.getAddress.formatAsString,
      cellType = cellTypeName,
      value = valueOpt,
      formula = formulaOpt,
      errorValue = errorOpt,
      comment = commentTextOpt,
      commentAuthor = commentAuthorOpt,
      hyperlink = hyperlinkOpt,
      dataFormat = dataFormatOpt,
      formattedValue = formattedOpt,
      font = fontData,
      style = styleData
    )

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
      rgbColor = font match
        case xf: XSSFFont => colorToRgb(xf.getXSSFColor)
        case _ => None
    )

  /**
   * Helper to convert an XSSFColor to a hex RGB string, e.g. #RRGGBB
   */
  private def colorToRgb(color: XSSFColor): Option[String] =
    Option(color).flatMap(c => Option(c.getRGB)).map { arr =>
      f"#${arr(0) & 0xFF}%02X${arr(1) & 0xFF}%02X${arr(2) & 0xFF}%02X"
    }

  /**
   * Helper to convert a POI XSSFCellStyle to CellStyle.
   */
  private def fromPoiCellStyle(xstyle: XSSFCellStyle): com.tjclp.xlcr.models.CellStyle =
    com.tjclp.xlcr.models.CellStyle(
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
        "top" -> colorToRgb(xstyle.getTopBorderXSSFColor),
        "right" -> colorToRgb(xstyle.getRightBorderXSSFColor),
        "bottom" -> colorToRgb(xstyle.getBottomBorderXSSFColor),
        "left" -> colorToRgb(xstyle.getLeftBorderXSSFColor)
      ).collect { case (k, Some(v)) => k -> v }
    )