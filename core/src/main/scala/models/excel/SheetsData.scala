package com.tjclp.xlcr
package models.excel

import models.Model
import types.Mergeable

/**
 * SheetsData is a container for multiple SheetData instances.
 * It implements the Model trait so it can load (fromMimeType) or
 * export (toMimeType) data as JSON, Excel, or Markdown.
 */
final case class SheetsData(sheets: List[SheetData]) extends Model with Mergeable[SheetsData] {

  /**
   * Merge logic for SheetsData. Merges each sheet by name.
   * If the "other" SheetsData has a sheet of the same name,
   * merge them. Otherwise, add it.
   */
  override def merge(other: SheetsData): SheetsData = {
    val existingMap = this.sheets.map(s => s.name -> s).toMap
    val mergedMap = other.sheets.foldLeft(existingMap) { (acc, newSheet) =>
      acc.get(newSheet.name) match {
        case Some(existingSheet) => acc.updated(newSheet.name, existingSheet.merge(newSheet))
        case None => acc.updated(newSheet.name, newSheet)
      }
    }
    SheetsData(mergedMap.values.toList)
  }
}

object SheetsData extends SheetsDataCodecs with Model