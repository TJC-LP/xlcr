package com.tjclp.xlcr
package models

import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFSheet

import scala.jdk.CollectionConverters.*

/**
 * Represents the data of a single sheet in an Excel workbook:
 * - name: The sheet name
 * - index: The sheet index
 * - isHidden: Whether the sheet is hidden
 * - rowCount: Number of rows
 * - columnCount: Number of columns
 * - cells: A list of CellData
 * - mergedRegions: List of merged regions in A1 notation
 * - protectionStatus: Whether the sheet is protected
 * - hasAutoFilter: Whether the sheet has an auto-filter
 */
case class SheetData(
                      name: String,
                      index: Int,
                      isHidden: Boolean,
                      rowCount: Int,
                      columnCount: Int,
                      cells: List[CellData],
                      mergedRegions: List[String],
                      protectionStatus: Boolean,
                      hasAutoFilter: Boolean
                    )

object SheetData:
  // Circe encoders and decoders for a single SheetData
  implicit val sheetDataEncoder: Encoder[SheetData] = deriveEncoder[SheetData]
  implicit val sheetDataDecoder: Decoder[SheetData] = deriveDecoder[SheetData]

  /**
   * Generate a single JSON object representing one sheet.
   */
  def toJson(sheetData: SheetData): String =
    sheetData.asJson.noSpaces

  /**
   * Parse a single JSON object into a SheetData.
   * (Preserves backward compatibility for single-sheet usage.)
   */
  def fromJson(json: String): Either[Error, SheetData] =
    io.circe.parser.decode[SheetData](json)

  // --------------------------------------------------------------------------
  // Multi-Sheet JSON Methods
  // --------------------------------------------------------------------------

  /**
   * Convert a list of SheetData objects into a JSON array string.
   */
  def toJsonMultiple(sheets: List[SheetData]): String =
    sheets.asJson.noSpaces

  /**
   * Parse a JSON array (or single JSON object) into a list of SheetData objects.
   *
   * This tries to parse an array first. If that fails, it attempts to parse a
   * single object for backward compatibility, wrapping it in a List.
   */
  def fromJsonMultiple(json: String): Either[Error, List[SheetData]] =
    // Attempt to parse as a List[SheetData]
    val parsedArray = io.circe.parser.decode[List[SheetData]](json)
    parsedArray match
      case Right(list) => Right(list)
      case Left(_) =>
        // If it's not an array, try parsing as a single SheetData
        io.circe.parser.decode[SheetData](json) match
          case Right(singleSheet) => Right(List(singleSheet))
          case Left(err) => Left(err)

  /**
   * Create a SheetData from an Apache POI Sheet by extracting relevant fields
   * and building a list of CellData. We scan all rows and cells to determine
   * the maximum bounding box for this sheet.
   */
  def fromSheet(sheet: Sheet, evaluator: FormulaEvaluator): SheetData =
    val formatter = new DataFormatter()

    // Convert rowIterator to a list for consistent iteration
    val rowList = sheet.rowIterator().asScala.toList

    // Determine the actual rowCount by scanning all rows
    val rowCount =
      if rowList.isEmpty then 0
      else rowList.map(_.getRowNum).max + 1

    // Determine the actual columnCount by scanning for the highest column index in any row
    val colCount =
      if rowList.isEmpty then 0
      else rowList.flatMap(row => row.cellIterator().asScala.map(_.getColumnIndex)).max + 1

    // Build a list of CellData from each cell in each row
    val cellList: List[CellData] =
      rowList.flatMap { row =>
        row.cellIterator().asScala.map { cell =>
          CellData.fromCell(cell, sheet.getSheetName, evaluator, formatter)
        }
      }

    val mergedRegions = sheet.getMergedRegions.asScala.map(_.formatAsString()).toList
    val protectionStatus = sheet.getProtect

    // Attempt to detect auto-filters (for demonstration only, can vary by usage)
    val hasAutoFilter = sheet match
      case xssfSheet: XSSFSheet =>
        val sheetConditionalFormatting = xssfSheet.getSheetConditionalFormatting
        (0 until sheetConditionalFormatting.getNumConditionalFormattings).exists: i =>
          val cf = sheetConditionalFormatting.getConditionalFormattingAt(i)
          cf.getFormattingRanges.exists(_.isFullColumnRange)
      case _ => false

    SheetData(
      name = sheet.getSheetName,
      index = sheet.getWorkbook.getSheetIndex(sheet),
      isHidden = sheet.getWorkbook.isSheetHidden(sheet.getWorkbook.getSheetIndex(sheet)),
      rowCount = rowCount,
      columnCount = colCount,
      cells = cellList,
      mergedRegions = mergedRegions,
      protectionStatus = protectionStatus,
      hasAutoFilter = hasAutoFilter
    )