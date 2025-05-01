package com.tjclp.xlcr
package utils.aspose

import models.FileContent
import types.MimeType
import utils.{DocChunk, DocumentSplitter, SplitConfig, SplitStrategy}

import com.aspose.cells.FileFormatType

/** 
 * Splits an XLSX (*.xlsx) workbook into individual worksheet documents.
 */
object ExcelXSheetAsposeSplitter
    extends HighPrioritySplitter[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type] {

  override def split(
      content: FileContent[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    ExcelSheetAsposeSplitter.splitWorkbook(
      content,
      cfg,
      FileFormatType.XLSX,
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
    )
  }
}