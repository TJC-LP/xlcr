package com.tjclp.xlcr
package splitters.excel

import com.aspose.cells.FileFormatType

import models.FileContent
import splitters.{ DocChunk, HighPrioritySplitter, SplitConfig }
import types.MimeType

/**
 * Splits an XLSB (binary Excel) workbook into individual worksheet documents.
 */
object ExcelXlsbSheetAsposeSplitter
    extends HighPrioritySplitter[MimeType.ApplicationVndMsExcelSheetBinary.type] {

  override def split(
    content: FileContent[MimeType.ApplicationVndMsExcelSheetBinary.type],
    cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] =
    ExcelSheetAsposeSplitter.splitWorkbook(
      content,
      cfg,
      FileFormatType.XLSB,
      MimeType.ApplicationVndMsExcelSheetBinary
    )
}
