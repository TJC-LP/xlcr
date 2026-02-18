package com.tjclp.xlcr
package splitters.excel

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import scala.util.Using

import compat.aspose.AsposeWorkbook
import models.FileContent
import splitters.{
  DocChunk,
  EmptyDocumentException,
  SplitConfig,
  SplitFailureHandler,
  SplitStrategy
}
import types.MimeType
import utils.aspose.AsposeLicense
import utils.resource.ResourceWrappers._

/**
 * Common helper used by the Aspose-based Excel sheet splitters.
 *
 * Up until now we implemented splitting by **removing** all worksheets except the target one and
 * then saving the mutated workbook. That approach breaks on workbooks where the remaining sheet is
 * *hidden* (Aspose throws "A workbook must contain at least a visible worksheet").
 *
 * The new implementation creates a *fresh* `Workbook` for every slice and copies the target
 * worksheet into it – guaranteeing that the resulting file always contains exactly one *visible*
 * worksheet regardless of the source visibility state.
 */
object ExcelSheetAsposeSplitter extends SplitFailureHandler {

  /**
   * Perform the actual sheet-level split.
   *
   * @param content
   *   original workbook bytes
   * @param cfg
   *   split configuration supplied by the caller
   * @param fileFormatType
   *   Aspose `FileFormatType` constant to use when saving
   * @param outputMimeType
   *   MIME type of the generated chunks
   */
  def splitWorkbook[M <: MimeType](
    content: FileContent[M],
    cfg: SplitConfig,
    fileFormatType: Int,
    outputMimeType: M
  ): Seq[DocChunk[_ <: MimeType]] = {

    // Initialize Aspose license on executor
    AsposeLicense.initializeIfNeeded()

    // Check for valid strategy
    if (!cfg.hasStrategy(SplitStrategy.Sheet)) {
      return handleInvalidStrategy(
        content,
        cfg,
        cfg.strategy.map(_.displayName).getOrElse("none"),
        Seq("sheet")
      )
    }

    // Wrap main logic with failure handling
    withFailureHandling(content, cfg) {
      // Load the source workbook once – copying sheets is cheap, parsing XLSX
      // multiple times is not.
      Using.Manager { use =>
        val srcInputStream = use(new ByteArrayInputStream(content.data))
        val srcWb          = new AsposeWorkbook(srcInputStream)
        use(new DisposableWrapper(srcWb))

        // Calculate all formulas while all sheets are present so cross-sheet
        // references resolve correctly (e.g. =Data!A1 in a Summary sheet).
        try srcWb.calculateFormula()
        catch { case _: Exception => () }

        val sheets = srcWb.getWorksheets
        val total  = sheets.getCount

        if (total == 0) {
          throw new EmptyDocumentException(
            content.mimeType.mimeType,
            "Workbook contains no sheets"
          )
        }

        // Determine which sheets to extract based on configuration
        val sheetsToExtract = cfg.chunkRange match {
          case Some(range) =>
            // Filter to valid sheet indices
            range.filter(i => i >= 0 && i < total)
          case None =>
            0 until total
        }

        sheetsToExtract.map { idx =>
          val srcSheet = sheets.get(idx)

          // Create a fresh workbook with *no* sheets, then copy the target one
          Using.Manager { destUse =>
            val destWb = new AsposeWorkbook()
            destUse(new DisposableWrapper(destWb))

            val destSheets = destWb.getWorksheets
            // Remove the default empty sheet Aspose creates
            destSheets.removeAt(0)

            // Create a fresh empty sheet and copy the source contents into it
            val newIdx    = destSheets.add()
            val destSheet = destSheets.get(newIdx)

            destSheet.copy(srcSheet)

            // Preserve name & ensure visibility
            destSheet.setName(srcSheet.getName)
            destSheet.setVisible(true)

            // Replace formula cells with computed values so the split file is
            // self-contained (no broken cross-sheet references).
            val cellIter = destSheet.getCells.iterator()
            while (cellIter.hasNext) {
              val cell = cellIter.next().asInstanceOf[com.aspose.cells.Cell]
              if (cell.getFormula != null && cell.getFormula.nonEmpty)
                try cell.putValue(cell.getValue)
                catch { case _: Exception => () }
            }

            // Persist to bytes
            val baos = destUse(new ByteArrayOutputStream())
            destWb.save(baos, fileFormatType)
            val fc = FileContent(baos.toByteArray, outputMimeType)
            DocChunk(fc, srcSheet.getName, idx, total)
          }.get
        }
      }.get
    }
  }
}
