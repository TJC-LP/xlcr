package com.tjclp.xlcr
package splitters
package excel

import models.FileContent
import types.MimeType

/**
 * Splitter for XLSX file format (.xlsx) - Excel 2007+
 */
object ExcelXlsxSheetSplitter
    extends DocumentSplitter[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type]
    with ExcelSheetSplitterTrait[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type] {

  override def split(
    content: FileContent[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type],
    cfg: SplitConfig
  ): Seq[DocChunk[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type]] =
    splitWorkbook(content, cfg, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)
}
