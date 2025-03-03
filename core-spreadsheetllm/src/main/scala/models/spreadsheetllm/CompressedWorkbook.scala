package com.tjclp.xlcr
package models.spreadsheetllm

import models.Model

/**
 * Represents a compressed Excel workbook containing multiple sheets.
 * This is the top-level container for the SpreadsheetLLM compression output.
 *
 * @param fileName Original file name (without path)
 * @param sheets   List of compressed sheets in the workbook
 * @param metadata Additional metadata about the workbook and compression
 */
case class CompressedWorkbook(
                               fileName: String,
                               sheets: List[CompressedSheet],
                               metadata: Map[String, String] = Map.empty
                             ) extends Model:
  /**
   * Returns all children models (sheets in this case).
   */
  override def children: Option[List[Model]] = Some(sheets)

  /**
   * Calculates overall compression statistics for the workbook.
   *
   * @return A map of statistics about the compression
   */
  def compressionStats: Map[String, Any] = {
    val totalOriginalCells = sheets.map(s => s.originalRowCount * s.originalColumnCount).sum
    val totalCompressedEntries = sheets.map(_.content.size).sum
    val compressionRatio = if (totalOriginalCells > 0) totalOriginalCells.toDouble / totalCompressedEntries else 0

    Map(
      "fileName" -> fileName,
      "sheetCount" -> sheets.size,
      "totalOriginalCells" -> totalOriginalCells,
      "totalCompressedEntries" -> totalCompressedEntries,
      "compressionRatio" -> compressionRatio,
      "metadata" -> metadata,
      "sheetStats" -> sheets.map(_.compressionStats)
    )
  }

  /**
   * Adds a sheet to the workbook.
   *
   * @param sheet The compressed sheet to add
   * @return A new CompressedWorkbook with the added sheet
   */
  def addSheet(sheet: CompressedSheet): CompressedWorkbook =
    this.copy(sheets = sheets :+ sheet)

  /**
   * Finds a sheet by name.
   *
   * @param sheetName The name of the sheet to find
   * @return Option containing the found sheet, or None if not found
   */
  def findSheet(sheetName: String): Option[CompressedSheet] =
    sheets.find(_.name == sheetName)