package com.tjclp.xlcr
package splitters
package pdf

import compat.aspose.AsposePdfDocument
import models.FileContent
import types.MimeType

import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/** Aspose implementation for splitting PDF files by page.
  *
  * This splitter creates a separate PDF file for each page in the original document,
  * using the Aspose.Pdf library. Unlike previous implementations, it is format-agnostic;
  * any format conversion (e.g., to PNG or JPEG) is now handled by Pipeline via the bridge system.
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

    try {
      // Load PDF document
      val pdfDocument = new AsposePdfDocument(
        new ByteArrayInputStream(content.data)
      )
      val pageCount = pdfDocument.getPages.size
      
      logger.info(s"Splitting PDF into $pageCount pages")

      // Extract each page as a separate PDF
      (1 to pageCount).map { pageIndex =>
        // Create a new PDF with just this page
        val newDocument = new AsposePdfDocument()
        
        // Add the current page to the new document
        newDocument.getPages.add(pdfDocument.getPages.get_Item(pageIndex))

        // Save to byte array
        val outputStream = new ByteArrayOutputStream()
        newDocument.save(outputStream)
        
        val fc = FileContent(outputStream.toByteArray, MimeType.ApplicationPdf)
        DocChunk(fc, s"Page $pageIndex", pageIndex - 1, pageCount)
      }
    } catch {
      case e: Exception =>
        logger.error(s"Error splitting PDF: ${e.getMessage}", e)
        Seq(DocChunk(content, "document", 0, 1))
    }
  }
}
