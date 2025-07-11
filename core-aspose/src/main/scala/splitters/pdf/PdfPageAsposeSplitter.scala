package com.tjclp.xlcr
package splitters
package pdf

import java.io.ByteArrayOutputStream

import scala.util.Using

import com.aspose.pdf.OptimizedMemoryStream
import org.slf4j.LoggerFactory

import compat.aspose.AsposePdfDocument
import models.FileContent
import types.MimeType
import utils.resource.ResourceWrappers._

/**
 * Aspose implementation for splitting PDF files by page.
 *
 * This splitter creates a separate PDF file for each page in the original document, using the
 * Aspose.Pdf library. Unlike previous implementations, it is format-agnostic; any format conversion
 * (e.g., to PNG or JPEG) is now handled by Pipeline via the bridge system.
 */
object PdfPageAsposeSplitter
    extends HighPrioritySplitter[MimeType.ApplicationPdf.type]
    with SplitFailureHandler {
  override protected val logger = LoggerFactory.getLogger(getClass)

  override def split(
    content: FileContent[MimeType.ApplicationPdf.type],
    cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    // Check for valid strategy
    if (!cfg.hasStrategy(SplitStrategy.Page)) {
      return handleInvalidStrategy(
        content,
        cfg,
        cfg.strategy.map(_.displayName).getOrElse("none"),
        Seq("page")
      )
    }

    // Wrap main logic with failure handling
    withFailureHandling(content, cfg) {
      // Initialize Aspose license on executor
      utils.aspose.AsposeLicense.initializeIfNeeded()

      Using.Manager { use =>
        // Load PDF document using OptimizedMemoryStream for better memory handling
        val optimizedStream = new OptimizedMemoryStream()
        use(new CloseableWrapper(optimizedStream)) // Use WorkbookWrapper for close() method
        optimizedStream.write(content.data, 0, content.data.length)
        val pdfDocument = new AsposePdfDocument(optimizedStream)
        use(new CloseableWrapper(pdfDocument)) // Use WorkbookWrapper for close() method
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
          Using.Manager { pageUse =>
            // Create a new PDF with just this page
            val newDocument = new AsposePdfDocument()
            pageUse(new CloseableWrapper(newDocument))

            // Add the current page to the new document
            val page = pdfDocument.getPages.get_Item(pageIndex)
            newDocument.getPages.add(page)

            // Save to byte array
            val outputStream = pageUse(new ByteArrayOutputStream())
            newDocument.save(outputStream)
            val fc = FileContent(outputStream.toByteArray, MimeType.ApplicationPdf)
            DocChunk(fc, s"Page $pageIndex", pageIndex - 1, pageCount)
          }.get
        }
      }.get
    }
  }
}
