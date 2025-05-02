package com.tjclp.xlcr
package splitters.excel

import compat.aspose.AsposeWorkbook
import models.FileContent
import splitters.{DocChunk, SplitConfig, SplitStrategy}
import types.MimeType

import org.apache.poi.openxml4j.util.ZipSecureFile

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/** Common helper used by the Aspose-based Excel sheet splitters.
  *
  * Up until now we implemented splitting by **removing** all worksheets except
  * the target one and then saving the mutated workbook.  That approach breaks
  * on workbooks where the remaining sheet is *hidden* (Aspose throws
  * “A workbook must contain at least a visible worksheet”).
  *
  * The new implementation creates a *fresh* `Workbook` for every slice and
  * copies the target worksheet into it – guaranteeing that the resulting file
  * always contains exactly one *visible* worksheet regardless of the source
  * visibility state.
  */
object ExcelSheetAsposeSplitter {

  /** Perform the actual sheet-level split.
    *
    * @param content         original workbook bytes
    * @param cfg             split configuration supplied by the caller
    * @param fileFormatType  Aspose `FileFormatType` constant to use when saving
    * @param outputMimeType  MIME type of the generated chunks
    */
  def splitWorkbook[M <: MimeType](
      content: FileContent[M],
      cfg: SplitConfig,
      fileFormatType: Int,
      outputMimeType: M
  ): Seq[DocChunk[_ <: MimeType]] = {
    ZipSecureFile.setMaxFileCount(cfg.maxFileCount)

    // Only run when sheet-level splitting has been requested
    if (!cfg.hasStrategy(SplitStrategy.Sheet))
      return Seq(DocChunk(content, "workbook", 0, 1))

    // Load the source workbook once – copying sheets is cheap, parsing XLSX
    // multiple times is not.
    val srcWb = new AsposeWorkbook(new ByteArrayInputStream(content.data))
    val sheets = srcWb.getWorksheets
    val total = sheets.getCount

    (0 until total).map { idx =>
      val srcSheet = sheets.get(idx)

      // Create a fresh workbook with *no* sheets, then copy the target one
      val destWb = new AsposeWorkbook()
      val destSheets = destWb.getWorksheets
      // Remove the default empty sheet Aspose creates
      destSheets.removeAt(0)

      // Create a fresh empty sheet and copy the source contents into it
      val newIdx = destSheets.add()
      val destSheet = destSheets.get(newIdx)

      destSheet.copy(srcSheet)

      // Preserve name & ensure visibility
      destSheet.setName(srcSheet.getName)
      destSheet.setVisible(true)

      // Persist to bytes
      val baos = new ByteArrayOutputStream()
      destWb.save(baos, fileFormatType)

      val fc = FileContent(baos.toByteArray, outputMimeType)

      DocChunk(fc, srcSheet.getName, idx, total)
    }
  }
}
