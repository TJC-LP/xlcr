package com.tjclp.xlcr
package splitters
package excel

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import java.util.logging.Level

import scala.util.Using

import org.odftoolkit.odfdom.doc.OdfSpreadsheetDocument

import models.FileContent
import types.MimeType
import utils.resource.ResourceWrappers._

object OdsSheetSplitter
    extends DocumentSplitter[
      MimeType.ApplicationVndOasisOpendocumentSpreadsheet.type
    ]
    with SplitFailureHandler {

  override protected val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  // Configure ODFDOM logging to avoid excessive log output
  {
    val rootLogger = java.util.logging.Logger.getLogger("")
    rootLogger.setLevel(Level.WARNING)
  }

  private val supported = Set(
    MimeType.ApplicationVndOasisOpendocumentSpreadsheet
  )

  override def split(
    content: FileContent[
      MimeType.ApplicationVndOasisOpendocumentSpreadsheet.type
    ],
    cfg: SplitConfig
  ): Seq[DocChunk[MimeType.ApplicationVndOasisOpendocumentSpreadsheet.type]] = {

    if (!supported.contains(content.mimeType)) {
      return Seq(DocChunk(content, "workbook", 0, 1))
    }

    // Check for valid strategy
    if (!cfg.hasStrategy(SplitStrategy.Sheet)) {
      return handleInvalidStrategy(
        content,
        cfg,
        cfg.strategy.map(_.displayName).getOrElse("none"),
        Seq("sheet")
      ).asInstanceOf[Seq[DocChunk[MimeType.ApplicationVndOasisOpendocumentSpreadsheet.type]]]
    }

    // Wrap main logic with failure handling
    withFailureHandling(content, cfg) {
      // Load the ODS document to get sheet information
      val (total, sheetNames) = {
        val tempDoc = OdfSpreadsheetDocument.loadDocument(new ByteArrayInputStream(content.data))
        Using.resource(new CloseableWrapper(tempDoc)) { _ =>
          val sheets = tempDoc.getTableList(false)
          val total  = sheets.size()

          if (total == 0) {
            throw new EmptyDocumentException(
              content.mimeType.mimeType,
              "ODS spreadsheet contains no sheets"
            )
          }

          val sheetNames = (0 until total).map(idx => sheets.get(idx).getTableName)
          (total, sheetNames)
        }
      }

      // Determine which sheets to extract based on configuration
      val sheetsToExtract = cfg.chunkRange match {
        case Some(range) =>
          // Filter to valid sheet indices
          range.filter(i => i >= 0 && i < total)
        case None =>
          0 until total
      }

      // Create a new document for each sheet
      sheetsToExtract.map { idx =>
        val name = sheetNames(idx)
        Using.Manager { use =>
          // Create a new document with only this sheet
          val doc = OdfSpreadsheetDocument.loadDocument(new ByteArrayInputStream(content.data))
          use(new CloseableWrapper(doc))
          val allSheets = doc.getTableList(false)

          // Keep only the target sheet
          val sheetsToRemove = (0 until allSheets.size()).filter(_ != idx)
          sheetsToRemove.sorted.reverse.foreach { i =>
            // Get the sheet and remove it
            val sheetToRemove = allSheets.get(i)
            sheetToRemove.remove()
          }

          // Save to byte array
          val baos = use(new ByteArrayOutputStream())
          doc.save(baos)

          val fc = FileContent(baos.toByteArray, content.mimeType)
          DocChunk(fc, name, idx, total)
        }.get
      }
    }.asInstanceOf[Seq[DocChunk[MimeType.ApplicationVndOasisOpendocumentSpreadsheet.type]]]
  }
}
