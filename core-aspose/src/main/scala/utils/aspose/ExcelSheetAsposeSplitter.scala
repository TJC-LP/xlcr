package com.tjclp.xlcr
package utils.aspose

import models.FileContent
import types.MimeType
import utils.{DocChunk, DocumentSplitter, SplitConfig, SplitStrategy}
import compat.aspose.{AsposeWorkbook}

import com.aspose.cells.FileFormatType

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/**
 * Base implementation for Excel sheet splitters using Aspose.Cells.
 * Used by both XLS and XLSX splitters.
 */
object ExcelSheetAsposeSplitter {

  /**
   * Split an Excel workbook into individual worksheet documents
   *
   * @param content The file content to split
   * @param cfg The split configuration
   * @param fileFormatType The FileFormatType to use (Xlsx or Xls)
   * @param outputMimeType The output MIME type to use
   * @return A sequence of document chunks
   */
  def splitWorkbook[M <: MimeType](
      content: FileContent[M],
      cfg: SplitConfig,
      fileFormatType: Int,
      outputMimeType: M
  ): Seq[DocChunk[_ <: MimeType]] = {

    // Only run when the caller requested sheet-level splitting.
    if (cfg.strategy != SplitStrategy.Sheet)
      return Seq(DocChunk(content, "workbook", 0, 1))

    // Load the source workbook to access sheet metadata
    val srcWb = new AsposeWorkbook(new ByteArrayInputStream(content.data))

    try {
      val sheets = srcWb.getWorksheets
      val total = sheets.getCount

      (0 until total).map { idx =>
        // Reload original bytes for each iteration to get a fresh copy
        val wb = new AsposeWorkbook(new ByteArrayInputStream(content.data))
        val wSheets = wb.getWorksheets

        // Remove every sheet except the target one (iterate backwards)
        for (i <- wSheets.getCount - 1 to 0 by -1) {
          if (i != idx) wSheets.removeAt(i)
        }

        val outBaos = new ByteArrayOutputStream()
        wb.save(outBaos, fileFormatType)

        val sheetName = srcWb.getWorksheets.get(idx).getName

        val fc = FileContent(
          outBaos.toByteArray,
          outputMimeType
        )
        DocChunk(fc, sheetName, idx, total)
      }
    } finally {
      // No explicit dispose() in AsposeWorkbook, but good to mark the intent
      // srcWb should be garbage collected properly
    }
  }
}