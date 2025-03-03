package com.tjclp.xlcr
package models.spreadsheetllm

import models.Model

/**
 * Represents a compressed spreadsheet after applying the SpreadsheetLLM compression techniques.
 * This is the main data structure for a single sheet in the workbook.
 *
 * @param name The name of the sheet
 * @param content A map of cell content to addresses/ranges
 * @param originalRowCount The original number of rows in the sheet
 * @param originalColumnCount The original number of columns in the sheet
 * @param compressionMetadata Optional metadata about the compression process
 */
case class CompressedSheet(
  name: String,
  content: Map[String, Either[String, List[String]]], // Either a single range or list of addresses
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
      "metadata" -> compressionMetadata
    )
  }
  
  /**
   * Adds a single content entry to this sheet.
   * 
   * @param value The content value (text or format descriptor)
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
   * Adds a content value that spans a range of cells.
   * 
   * @param value The content value (text or format descriptor)
   * @param range A range of cells (e.g., "A1:B10")
   * @return A new CompressedSheet with the added content
   */
  def addContentRange(value: String, range: String): CompressedSheet =
    addContent(value, range)
