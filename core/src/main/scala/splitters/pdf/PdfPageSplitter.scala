package com.tjclp.xlcr
package splitters
package pdf

import java.io.ByteArrayOutputStream

import org.apache.pdfbox.pdmodel.PDDocument
import org.slf4j.LoggerFactory

import models.FileContent
import types.MimeType

/**
 * PDF Page splitter that splits a PDF into individual pages.
 *
 * This implementation extracts each page of a PDF into a separate one-page PDF document. Unlike
 * previous implementations, it is format-agnostic; any format conversion (e.g., to PNG or JPEG) is
 * now handled by Pipeline via the bridge system.
 * 
 * Now includes configurable failure handling via SplitFailureHandler trait.
 */
object PdfPageSplitter extends DocumentSplitter[MimeType.ApplicationPdf.type] 
    with SplitFailureHandler {
  override protected val logger = LoggerFactory.getLogger(getClass)

  override def split(
    content: FileContent[MimeType.ApplicationPdf.type],
    cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {

    // Check strategy compatibility
    if (!cfg.hasStrategy(SplitStrategy.Page)) {
      return handleInvalidStrategy(
        content, 
        cfg,
        cfg.strategy.map(_.displayName).getOrElse("none"),
        Seq("page")
      )
    }

    // Wrap main splitting logic with failure handling
    withFailureHandling(content, cfg) {
      // Validate PDF data is not empty
      if (content.data.isEmpty) {
        throw new EmptyDocumentException(
          content.mimeType.mimeType,
          "PDF file is empty"
        )
      }
      
      // Load the PDF document
      val original = try {
        PDDocument.load(content.data)
      } catch {
        case e: java.io.IOException =>
          throw new CorruptedDocumentException(
            content.mimeType.mimeType,
            s"Failed to load PDF: ${e.getMessage}",
            e
          )
      }
      
      try {
        val total = original.getNumberOfPages
        
        // Check if PDF has pages
        if (total == 0) {
          throw new EmptyDocumentException(
            content.mimeType.mimeType,
            "PDF contains no pages"
          )
        }
        
        logger.info(s"Splitting PDF into $total pages")

        // Determine which pages to extract based on configuration
        val pagesToExtract = cfg.chunkRange match {
          case Some(range) =>
            // Ensure range is within bounds (PDFBox uses 0-based indexing)
            val validPages = range.filter(i => i >= 0 && i < total)
            if (validPages.isEmpty) {
              throw new IllegalArgumentException(
                s"Requested page range ${range.start}-${range.end} is outside document bounds (0-${total-1})"
              )
            }
            validPages
          case None =>
            0 until total
        }

        logger.debug(s"Extracting pages: ${pagesToExtract.mkString(", ")}")

        // Extract specified pages as single-page PDFs
        pagesToExtract.map { idx =>
          // Create a new document with only one page
          val chunkDoc = new PDDocument()
          try {
            // Add the current page to the new document
            val page = original.getPage(idx)
            chunkDoc.addPage(page)

            // Save the new document to a byte array
            val baos = new ByteArrayOutputStream()
            chunkDoc.save(baos)

            // Create a FileContent with the new document
            val fc = FileContent(baos.toByteArray, MimeType.ApplicationPdf)

            // Create a DocChunk with the page information (1-based display)
            DocChunk(
              fc, 
              s"Page ${idx + 1}", 
              idx, 
              total,
              attrs = Map("source_page" -> (idx + 1).toString)
            )
          } catch {
            case e: Exception =>
              // If we fail to extract a specific page, we might want to continue
              // with other pages or fail entirely based on configuration
              throw new CorruptedDocumentException(
                content.mimeType.mimeType,
                s"Failed to extract page ${idx + 1}: ${e.getMessage}",
                e
              )
          } finally {
            chunkDoc.close()
          }
        }
      } finally {
        original.close()
      }
    }
  }
}
