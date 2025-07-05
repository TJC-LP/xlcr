package com.tjclp.xlcr
package splitters
package excel

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import org.apache.poi.openxml4j.util.ZipSecureFile
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.slf4j.LoggerFactory

import models.FileContent
import types.MimeType

/**
 * Trait providing common Excel sheet splitting functionality. Can be mixed into specific splitter
 * objects for different Excel MIME types.
 */
trait ExcelSheetSplitterTrait[M <: MimeType] extends SplitFailureHandler {

  override protected val logger: org.slf4j.Logger = LoggerFactory.getLogger(getClass)

  /**
   * Split an Excel workbook into individual sheets
   */
  def splitWorkbook(
    content: FileContent[M],
    cfg: SplitConfig,
    mimeType: M
  ): Seq[DocChunk[M]] = {

    // Check for valid strategy
    if (!cfg.hasStrategy(SplitStrategy.Sheet)) {
      return handleInvalidStrategy(
        content,
        cfg,
        cfg.strategy.map(_.displayName).getOrElse("none"),
        Seq("sheet")
      ).asInstanceOf[Seq[DocChunk[M]]]
    }

    // Wrap main logic with failure handling
    withFailureHandling(content, cfg) {
      try {
        ZipSecureFile.setMaxFileCount(cfg.maxFileCount)

        val tempWb = WorkbookFactory.create(new ByteArrayInputStream(content.data))
        val total  = tempWb.getNumberOfSheets

        if (total == 0) {
          tempWb.close()
          throw new EmptyDocumentException(
            content.mimeType.toString,
            "Workbook contains no sheets"
          )
        }

        val sheetNames = (0 until total).map(tempWb.getSheetName)
        tempWb.close()

        // Determine which sheets to extract based on configuration
        val sheetsToExtract = cfg.chunkRange match {
          case Some(range) =>
            // Filter to valid sheet indices
            range.filter(i => i >= 0 && i < total)
          case None =>
            0 until total
        }

        sheetsToExtract.map { idx =>
          val name = sheetNames(idx)
          val wb   = WorkbookFactory.create(new ByteArrayInputStream(content.data))
          val cnt  = wb.getNumberOfSheets
          (cnt - 1 to 0 by -1).foreach(i => if (i != idx) wb.removeSheetAt(i))

          val baos = new ByteArrayOutputStream()
          wb.write(baos)
          wb.close()

          val fc = FileContent(baos.toByteArray, mimeType)
          DocChunk(fc, name, idx, total)
        }
      } finally ZipSecureFile.setMaxFileCount(1000L) // Revert back to default setting
    }.asInstanceOf[Seq[DocChunk[M]]]
  }
}
