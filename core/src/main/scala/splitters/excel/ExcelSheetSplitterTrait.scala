package com.tjclp.xlcr
package splitters
package excel

import models.FileContent
import types.MimeType

import org.apache.poi.ss.usermodel.WorkbookFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/**
 * Trait providing common Excel sheet splitting functionality.
 * Can be mixed into specific splitter objects for different Excel MIME types.
 */
trait ExcelSheetSplitterTrait {
  
  /**
   * Split an Excel workbook into individual sheets
   */
  def splitWorkbook[M <: MimeType](
      content: FileContent[M],
      cfg: SplitConfig,
      mimeType: M
  ): Seq[DocChunk[M]] = {

    if (!cfg.hasStrategy(SplitStrategy.Sheet))
      return Seq(DocChunk(content, "workbook", 0, 1))

    val tempWb = WorkbookFactory.create(new ByteArrayInputStream(content.data))
    val total = tempWb.getNumberOfSheets
    val sheetNames = (0 until total).map(tempWb.getSheetName)
    tempWb.close()

    sheetNames.zipWithIndex.map { case (name, idx) =>
      val wb = WorkbookFactory.create(new ByteArrayInputStream(content.data))
      val cnt = wb.getNumberOfSheets
      (cnt - 1 to 0 by -1).foreach { i => if (i != idx) wb.removeSheetAt(i) }

      val baos = new ByteArrayOutputStream()
      wb.write(baos)
      wb.close()

      val fc = FileContent(baos.toByteArray, mimeType)
      DocChunk(fc, name, idx, total)
    }
  }
}