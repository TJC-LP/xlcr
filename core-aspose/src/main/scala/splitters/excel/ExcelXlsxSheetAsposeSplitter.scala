package com.tjclp.xlcr
package splitters.excel

import com.aspose.cells.FileFormatType

import models.FileContent
import splitters.{ DocChunk, HighPrioritySplitter, SplitConfig }
import types.MimeType

/**
 * Splits an XLSX (*.xlsx) workbook into individual worksheet documents.
 */
object ExcelXlsxSheetAsposeSplitter
    extends HighPrioritySplitter[
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
    ] {

  override def split(
    content: FileContent[
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
    ],
    cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] =
    ExcelSheetAsposeSplitter.splitWorkbook(
      content,
      cfg,
      FileFormatType.XLSX,
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
    )
}
