package com.tjclp.xlcr
package splitters
package excel

import models.FileContent
import types.MimeType

import org.odftoolkit.odfdom.doc.OdfSpreadsheetDocument

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.logging.Level

object OdsSheetSplitter
    extends DocumentSplitter[
      MimeType.ApplicationVndOasisOpendocumentSpreadsheet.type
    ] {

  private val logger = org.slf4j.LoggerFactory.getLogger(getClass)

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
  ): Seq[DocChunk[_ <: MimeType]] = {

    if (
      !cfg.hasStrategy(SplitStrategy.Sheet) || !supported.contains(
        content.mimeType
      )
    )
      return Seq(DocChunk(content, "workbook", 0, 1))

    try {
      // Load the ODS document
      val tempDoc = OdfSpreadsheetDocument.loadDocument(
        new ByteArrayInputStream(content.data)
      )
      val sheets = tempDoc.getTableList(false)
      val total = sheets.size()
      val sheetNames = (0 until total).map(idx => sheets.get(idx).getTableName)

      tempDoc.close()

      // Create a new document for each sheet
      sheetNames.zipWithIndex.map { case (name, idx) =>
        try {
          // Create a new document with only this sheet
          val doc = OdfSpreadsheetDocument.loadDocument(
            new ByteArrayInputStream(content.data)
          )
          val allSheets = doc.getTableList(false)

          // Keep only the target sheet
          val sheetsToRemove = (0 until allSheets.size()).filter(_ != idx)
          sheetsToRemove.sorted.reverse.foreach { i =>
            // Get the sheet and remove it
            val sheetToRemove = allSheets.get(i)
            sheetToRemove.remove()
          }

          // Save to byte array
          val baos = new ByteArrayOutputStream()
          doc.save(baos)
          doc.close()

          val fc = FileContent(baos.toByteArray, content.mimeType)
          DocChunk(fc, name, idx, total)
        } catch {
          case e: Exception =>
            logger.error(
              s"Error processing ODS sheet $name: ${e.getMessage}",
              e
            )
            // Return the original content if we can't process this sheet
            DocChunk(content, s"sheet-$idx", idx, total)
        }
      }
    } catch {
      case e: Exception =>
        logger.error(s"Error processing ODS document: ${e.getMessage}", e)
        Seq(DocChunk(content, "workbook", 0, 1))
    }
  }
}
