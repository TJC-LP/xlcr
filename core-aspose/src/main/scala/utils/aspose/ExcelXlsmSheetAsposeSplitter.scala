package com.tjclp.xlcr
package utils.aspose

import models.FileContent
import types.MimeType
import utils.{DocChunk, DocumentSplitter, SplitConfig, SplitStrategy}

import com.aspose.cells.FileFormatType

/** 
 * Splits an XLSM (macro-enabled Excel) workbook into individual worksheet documents.
 */
object ExcelXlsmSheetAsposeSplitter
    extends DocumentSplitter[MimeType.ApplicationVndMsExcelSheetMacroEnabled.type] {

  override def split(
      content: FileContent[MimeType.ApplicationVndMsExcelSheetMacroEnabled.type],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    ExcelSheetAsposeSplitter.splitWorkbook(
      content,
      cfg,
      FileFormatType.XLSM,
      MimeType.ApplicationVndMsExcelSheetMacroEnabled
    )
  }
}