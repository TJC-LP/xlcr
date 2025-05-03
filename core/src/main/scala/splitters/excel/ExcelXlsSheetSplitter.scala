package com.tjclp.xlcr
package splitters
package excel

import models.FileContent
import types.MimeType

/**
 * Splitter for XLS file format (.xls) - Excel 97-2003
 */
object ExcelXlsSheetSplitter 
  extends DocumentSplitter[MimeType.ApplicationVndMsExcel.type]
  with ExcelSheetSplitterTrait {

  override def split(
      content: FileContent[MimeType.ApplicationVndMsExcel.type],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    splitWorkbook(content, cfg, MimeType.ApplicationVndMsExcel)
  }
}