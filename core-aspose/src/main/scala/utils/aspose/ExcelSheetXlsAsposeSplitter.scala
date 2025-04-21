package com.tjclp.xlcr
package utils.aspose

import models.FileContent
import types.MimeType
import utils.{DocChunk, DocumentSplitter, SplitConfig, SplitStrategy}

import com.aspose.cells.FileFormatType

/** 
 * Splits an XLS (*.xls) workbook into individual worksheet documents.
 */
object ExcelSheetXlsAsposeSplitter
    extends DocumentSplitter[MimeType.ApplicationVndMsExcel.type] {

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