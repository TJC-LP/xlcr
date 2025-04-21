package com.tjclp.xlcr
package utils.aspose

import models.FileContent
import types.MimeType
import utils.{DocChunk, DocumentSplitter, SplitConfig, SplitStrategy}

import com.aspose.cells.FileFormatType

/** 
 * Splits an XLSB (binary Excel) workbook into individual worksheet documents.
 */
object ExcelXlsbSheetAsposeSplitter
    extends DocumentSplitter[MimeType.ApplicationVndMsExcelSheetBinary.type] {

  override def split(
      content: FileContent[MimeType.ApplicationVndMsExcelSheetBinary.type],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    ExcelSheetAsposeSplitter.splitWorkbook(
      content,
      cfg,
      FileFormatType.XLSB,
      MimeType.ApplicationVndMsExcelSheetBinary
    )
  }
}