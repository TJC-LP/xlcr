package com.tjclp.xlcr
package models.spreadsheetllm

import models.Model

/**
 * Companion object for CompressedSheet.
 */
object CompressedSheet:
  /**
   * Represents information about a detected table in the sheet
   *
   * @param id         Unique identifier for the table
   * @param range      The cell range of the table (e.g., "A1:E10")
   * @param hasHeaders Whether the table has headers
   * @param headerRow  Optional row index of the headers (if present)
   */
  case class TableInfo(
                        id: String,
                        range: String,
                        hasHeaders: Boolean,
                        headerRow: Option[Int]
                      )

/**
 * Represents a compressed spreadsheet after applying the SpreadsheetLLM compression techniques.
 * This is the main data structure for a single sheet in the workbook.
 *
 * @param name                The name of the sheet
 * @param content             A map of cell content to addresses/ranges
 * @param formulas            Optional map of formula expressions and their target cells
 * @param tables              Optional list of detected table regions in the sheet
 * @param originalRowCount    The original number of rows in the sheet
 * @param originalColumnCount The original number of columns in the sheet
 * @param compressionMetadata Optional metadata about the compression process
 */
case class CompressedSheet(
                            name: String,
                            content: Map[String, Either[String, List[String]]], // Either a single range or list of addresses
                            formulas: Map[String, String] = Map.empty, // Formula expression -> target cell
                            tables: List[CompressedSheet.TableInfo] = List.empty,
                            originalRowCount: Int,
                            originalColumnCount: Int,
                            compressionMetadata: Map[String, String] = Map.empty
                          ) extends Model:

  /**
   * Returns statistics about the compression.
   *
   * @return Map containing compression statistics
   */
  def compressionStats: Map[String, Any] = {
    val contentEntryCount = content.size
    Map(
      "sheet" -> name,
      "originalCellCount" -> (originalRowCount * originalColumnCount),
      "compressedEntryCount" -> contentEntryCount,
      "formulaCount" -> formulas.size,
      "tableCount" -> tables.size,
      "metadata" -> compressionMetadata
    )
  }

  /**
   * Adds a content value that spans a range of cells.
   *
   * @param value The content value (text or format descriptor)
   * @param range A range of cells (e.g., "A1:B10")
   * @return A new CompressedSheet with the added content
   */
  def addContentRange(value: String, range: String): CompressedSheet =
    addContent(value, range)

  /**
   * Adds a single content entry to this sheet.
   *
   * @param value   The content value (text or format descriptor)
   * @param address A single cell address (e.g., "A1")
   * @return A new CompressedSheet with the added content
   */
  def addContent(value: String, address: String): CompressedSheet = {
    val updatedContent = content.get(value) match {
      case Some(Left(existingRange)) =>
        // If this is already a range, convert to list and add new address
        content + (value -> Right(List(existingRange, address)))
      case Some(Right(existingAddresses)) =>
        // Add to existing list of addresses
        content + (value -> Right(existingAddresses :+ address))
      case None =>
        // Create new entry with single address
        content + (value -> Left(address))
    }
    this.copy(content = updatedContent)
  }

  /**
   * Adds a formula expression to the sheet.
   *
   * @param formula The formula expression (e.g., "=SUM(A1:A10)")
   * @param target  The target cell where the formula is located (e.g., "A11")
   * @return A new CompressedSheet with the added formula
   */
  def addFormula(formula: String, target: String): CompressedSheet =
    this.copy(formulas = formulas + (formula -> target))

  /**
   * Adds a detected table to the sheet.
   *
   * @param range      The cell range of the table (e.g., "A1:E10")
   * @param hasHeaders Whether the table has headers
   * @param headerRow  Optional row index of the headers (if present)
   * @return A new CompressedSheet with the added table
   */
  def addTable(range: String, hasHeaders: Boolean, headerRow: Option[Int] = None): CompressedSheet =
    val tableId = s"table_${tables.size + 1}"
    addTable(CompressedSheet.TableInfo(tableId, range, hasHeaders, headerRow))

  /**
   * Adds a detected table to the sheet.
   *
   * @param tableInfo Information about the detected table
   * @return A new CompressedSheet with the added table
   */
  def addTable(tableInfo: CompressedSheet.TableInfo): CompressedSheet =
    this.copy(tables = tables :+ tableInfo)
