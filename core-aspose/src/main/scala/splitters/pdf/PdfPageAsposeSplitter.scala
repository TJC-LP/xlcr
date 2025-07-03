package com.tjclp.xlcr
package splitters
package pdf

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import org.slf4j.LoggerFactory

import compat.aspose.AsposePdfDocument
import models.FileContent
import types.MimeType

/**
 * Aspose implementation for splitting PDF files by page.
 *
 * This splitter creates a separate PDF file for each page in the original document, using the
 * Aspose.Pdf library. Unlike previous implementations, it is format-agnostic; any format conversion
 * (e.g., to PNG or JPEG) is now handled by Pipeline via the bridge system.
 */
object PdfPageAsposeSplitter
    extends HighPrioritySplitter[MimeType.ApplicationPdf.type] {
  private val logger = LoggerFactory.getLogger(getClass)

  override def split(
    content: FileContent[MimeType.ApplicationPdf.type],
    cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {

    if (!cfg.hasStrategy(SplitStrategy.Page))
      return Seq(DocChunk(content, "document", 0, 1))

    var pdfDocument: AsposePdfDocument = null
    try {
      // Load PDF document
      pdfDocument = new AsposePdfDocument(
        new ByteArrayInputStream(content.data)
      )
      val pageCount = pdfDocument.getPages.size

      logger.info(s"Splitting PDF into $pageCount pages")

      // Determine which pages to extract based on configuration
      val pagesToExtract = cfg.chunkRange match {
        case Some(range) =>
          // Ensure range is within bounds (Aspose uses 1-based indexing)
          range.filter(i => i >= 0 && i < pageCount).map(_ + 1)
        case None =>
          1 to pageCount
      }

      logger.debug(s"Extracting pages: ${pagesToExtract.mkString(", ")}")

      // Extract specified pages
      pagesToExtract.map { pageIndex =>
        var newDocument: AsposePdfDocument = null
        try {
          // Create a new PDF with just this page
          newDocument = new AsposePdfDocument()
          
          // Add the current page to the new document
          val page = pdfDocument.getPages.get_Item(pageIndex)
          newDocument.getPages.add(page)

          // Save to byte array
          val outputStream = new ByteArrayOutputStream()
          try {
            newDocument.save(outputStream)
            val fc = FileContent(outputStream.toByteArray, MimeType.ApplicationPdf)
            DocChunk(fc, s"Page $pageIndex", pageIndex - 1, pageCount)
          } finally {
            outputStream.close()
          }
        } finally {
          if (newDocument != null) {
            try {
              newDocument.close()
            } catch {
              case e: Exception =>
                logger.warn(s"Error closing document for page $pageIndex: ${e.getMessage}")
            }
          }
        }
      }
    } catch {
      case e: Exception =>
        logger.error(s"Error splitting PDF: ${e.getMessage}", e)
        Seq(DocChunk(content, "document", 0, 1))
    } finally {
      if (pdfDocument != null) {
        try {
          pdfDocument.close()
        } catch {
          case e: Exception =>
            logger.warn(s"Error closing original PDF document: ${e.getMessage}")
        }
      }
    }
  }
}