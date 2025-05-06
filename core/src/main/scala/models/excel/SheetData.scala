package com.tjclp.xlcr
package models.excel

import scala.jdk.CollectionConverters._

import io.circe._
import io.circe.syntax._
import org.apache.poi.ss.usermodel._
import org.apache.poi.xssf.usermodel.XSSFSheet

import types.Mergeable

/**
 * Represents the data of a single sheet in an Excel workbook:
 *   - name: The sheet name
 *   - index: The sheet index
 *   - isHidden: Whether the sheet is hidden
 *   - cells: A list of CellData
 *   - hasAutoFilter: Whether the sheet has an auto-filter
 */
case class SheetData(
  name: String,
  index: Int,
  isHidden: Boolean = false,
  cells: List[CellData],
  mergedRegions: List[String] = List.empty,
  protectionStatus: Boolean = false,
  hasAutoFilter: Boolean = false
) extends Mergeable[SheetData] {

  // Calculate row and column counts from cells
  lazy val rowCount: Int    = if (cells.isEmpty) 0 else cells.map(_.rowIndex).max + 1
  lazy val columnCount: Int = if (cells.isEmpty) 0 else cells.map(_.columnIndex).max + 1

  /**
   * Merge two SheetData objects by combining their cells. The merged regions are combined.
   */
  override def merge(other: SheetData): SheetData = {
    val thisMap   = this.cells.map(c => c.address -> c).toMap
    val thatMap   = other.cells.map(c => c.address -> c).toMap
    val mergedMap = thisMap ++ thatMap

    val mergedCells = mergedMap.values.toList
    val allRegions  = (this.mergedRegions ++ other.mergedRegions).distinct

    this.copy(
      cells = mergedCells,
      mergedRegions = allRegions
    )
  }
}

object SheetData extends SheetDataCodecs {

  /**
   * Generate a single JSON object representing one sheet.
   */
  def toJson(sheetData: SheetData): String =
    sheetData.asJson.spaces2

  /**
   * Parse a single JSON object into a SheetData. (Preserves backward compatibility for single-sheet
   * usage.)
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
    sheets.asJson.spaces2

  /**
   * Parse a JSON array (or single JSON object) into a list of SheetData objects.
   *
   * This tries to parse an array first. If that fails, it attempts to parse a single object for
   * backward compatibility, wrapping it in a List.
   */
  def fromJsonMultiple(json: String): Either[Error, List[SheetData]] = {
    val parsedArray = io.circe.parser.decode[List[SheetData]](json)
    parsedArray match {
      case Right(list) => Right(list)
      case Left(_)     =>
        // If it's not an array, try parsing as a single SheetData
        io.circe.parser.decode[SheetData](json) match {
          case Right(singleSheet) => Right(List(singleSheet))
          case Left(err)          => Left(err)
        }
    }
  }

  /**
   * Create a SheetData from an Apache POI Sheet by extracting relevant fields and building a list
   * of CellData. We scan all rows and cells to determine the maximum bounding box for this sheet.
   */
  def fromSheet(sheet: Sheet, evaluator: FormulaEvaluator): SheetData = {
    val formatter = new DataFormatter()

    // Convert rowIterator to a list for consistent iteration
    val rowList = sheet.rowIterator().asScala.toList

    // Build a list of CellData from each cell in each row
    val cellList: List[CellData] =
      rowList.flatMap { row =>
        row.cellIterator().asScala.map { cell =>
          CellData.fromCell(cell, sheet.getSheetName, evaluator, formatter)
        }
      }
    val mergedRegions = sheet.getMergedRegions.asScala.map(_.formatAsString()).toList

    // Attempt to detect auto-filters (for demonstration only, can vary by usage)
    val hasAutoFilter = sheet match {
      case xssfSheet: XSSFSheet =>
        val sheetConditionalFormatting = xssfSheet.getSheetConditionalFormatting
        (0 until sheetConditionalFormatting.getNumConditionalFormattings).exists { i =>
          val cf = sheetConditionalFormatting.getConditionalFormattingAt(i)
          cf.getFormattingRanges.exists(_.isFullColumnRange)
        }
      case _ => false
    }

    SheetData(
      name = sheet.getSheetName,
      index = sheet.getWorkbook.getSheetIndex(sheet),
      isHidden = sheet.getWorkbook.isSheetHidden(sheet.getWorkbook.getSheetIndex(sheet)),
      cells = cellList,
      mergedRegions = mergedRegions,
      hasAutoFilter = hasAutoFilter
    )
  }
}
