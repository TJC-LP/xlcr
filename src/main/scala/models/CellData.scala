package com.tjclp.xlcr
package models

import models.ExcelReference.{Col, Row, Cell as RefCell}

import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import org.apache.poi.ss.usermodel.*

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
                           formattedValue: Option[String]
                         )

object CellData {
  // Circe encoders and decoders
  implicit val cellDataEncoder: Encoder[CellData] = deriveEncoder[CellData]
  implicit val cellDataDecoder: Decoder[CellData] = deriveDecoder[CellData]

  // Method to convert CellData to JSON string
  def toJson(cellData: CellData): String = cellData.asJson.noSpaces

  // Method to parse JSON string to CellData
  def fromJson(json: String): Either[Error, CellData] = parser.decode[CellData](json)

  def fromCell(cell: Cell, sheetName: String, evaluator: FormulaEvaluator, formatter: DataFormatter): CellData = {
    // Create an ExcelReference for the cell
    // Note: POI rows are 0-based, so we add 1 to match our Row definition
    val ref = RefCell(
      sheet = sheetName,
      row = Row(cell.getRowIndex + 1),
      col = Col(cell.getColumnIndex)
    )

    val cellType = cell.getCellType
    val cellTypeName = cellType.name()

    // Prepare fields
    var valueOpt: Option[String] = None
    var formulaOpt: Option[String] = None
    var errorOpt: Option[Byte] = None
    var formattedValueOpt: Option[String] = None

    // Extract value based on cell type
    cellType match {
      case CellType.STRING =>
        valueOpt = Some(cell.getStringCellValue)

      case CellType.NUMERIC =>
        valueOpt = Some(cell.getNumericCellValue.toString)
        formattedValueOpt = Some(formatter.formatCellValue(cell))

      case CellType.BOOLEAN =>
        valueOpt = Some(cell.getBooleanCellValue.toString)

      case CellType.FORMULA =>
        formulaOpt = Some(cell.getCellFormula)
        val evaluatedValue = evaluator.evaluate(cell)
        if (evaluatedValue != null) {
          evaluatedValue.getCellType match {
            case CellType.STRING =>
              valueOpt = Some(evaluatedValue.getStringValue)
            case CellType.NUMERIC =>
              valueOpt = Some(evaluatedValue.getNumberValue.toString)
              formattedValueOpt = Some(formatter.formatCellValue(cell, evaluator))
            case CellType.BOOLEAN =>
              valueOpt = Some(evaluatedValue.getBooleanValue.toString)
            case CellType.ERROR =>
              errorOpt = Some(evaluatedValue.getErrorValue)
            case CellType.BLANK =>
              valueOpt = None
            case _ => // No-op
          }
        }

      case CellType.ERROR =>
        errorOpt = Some(cell.getErrorCellValue)

      case CellType.BLANK =>
      // leave valueOpt as None

      case _ => // unexpected type
    }

    // Comment
    val comment = cell.getCellComment
    val commentOpt = Option(comment).map(_.getString.getString)
    val commentAuthorOpt = Option(comment).map(_.getAuthor)

    // Hyperlink
    val hyperlink = cell.getHyperlink
    val hyperlinkOpt = Option(hyperlink).map(_.getAddress)

    // Style & Data Format
    val style = cell.getCellStyle
    val dataFormatOpt = Option(style).map(_.getDataFormatString)

    CellData(
      referenceA1 = ref.toA1,
      rowIndex = cell.getRowIndex,
      columnIndex = cell.getColumnIndex,
      address = cell.getAddress.formatAsString,
      cellType = cellTypeName,
      value = valueOpt,
      formula = formulaOpt,
      errorValue = errorOpt,
      comment = commentOpt,
      commentAuthor = commentAuthorOpt,
      hyperlink = hyperlinkOpt,
      dataFormat = dataFormatOpt,
      formattedValue = formattedValueOpt
    )
  }
}
