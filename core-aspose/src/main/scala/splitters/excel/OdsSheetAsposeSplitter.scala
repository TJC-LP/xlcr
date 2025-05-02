package com.tjclp.xlcr
package splitters
package excel

import models.FileContent
import splitters.{DocChunk, SplitConfig}
import types.MimeType

/** Aspose implementation for splitting ODS (OpenDocument Spreadsheet) files by sheet
  *
  * This splitter creates a separate ODS file for each sheet in the original workbook,
  * using the Aspose.Cells library to handle the OpenDocument format.
  */
object OdsSheetAsposeSplitter
    extends DocumentSplitter[
      MimeType.ApplicationVndOasisOpendocumentSpreadsheet.type
    ] {

  override def split(
      content: FileContent[
        MimeType.ApplicationVndOasisOpendocumentSpreadsheet.type
      ],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    ExcelSheetAsposeSplitter.splitWorkbook(
      content,
      cfg,
      com.aspose.cells.SaveFormat.ODS,
      content.mimeType
    )
  }
}
