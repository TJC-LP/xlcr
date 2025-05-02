package com.tjclp.xlcr
package splitters.excel

import models.FileContent
import splitters.{DocChunk, HighPrioritySplitter, SplitConfig}
import types.MimeType

import com.aspose.cells.FileFormatType

/** 
 * Splits an XLS (*.xls) workbook into individual worksheet documents.
 */
object ExcelXlsSheetAsposeSplitter
    extends HighPrioritySplitter[MimeType.ApplicationVndMsExcel.type] {

  override def split(
      content: FileContent[MimeType.ApplicationVndMsExcel.type],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    ExcelSheetAsposeSplitter.splitWorkbook(
      content,
      cfg,
      FileFormatType.EXCEL_97_TO_2003,
      MimeType.ApplicationVndMsExcel
    )
  }
}