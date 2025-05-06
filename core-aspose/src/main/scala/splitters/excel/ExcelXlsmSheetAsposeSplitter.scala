package com.tjclp.xlcr
package splitters.excel

import com.aspose.cells.FileFormatType

import models.FileContent
import splitters.{ DocChunk, HighPrioritySplitter, SplitConfig }
import types.MimeType

/**
 * Splits an XLSM (macro-enabled Excel) workbook into individual worksheet documents.
 */
object ExcelXlsmSheetAsposeSplitter
    extends HighPrioritySplitter[MimeType.ApplicationVndMsExcelSheetMacroEnabled.type] {

  override def split(
    content: FileContent[MimeType.ApplicationVndMsExcelSheetMacroEnabled.type],
    cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] =
    ExcelSheetAsposeSplitter.splitWorkbook(
      content,
      cfg,
      FileFormatType.XLSM,
      MimeType.ApplicationVndMsExcelSheetMacroEnabled
    )
}
