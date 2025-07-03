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
 */
object PdfPageSplitter extends DocumentSplitter[MimeType.ApplicationPdf.type] {
  private val logger = LoggerFactory.getLogger(getClass)

  override def split(
    content: FileContent[MimeType.ApplicationPdf.type],
    cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {

    if (!cfg.hasStrategy(SplitStrategy.Page))
      return Seq(DocChunk(content, "document", 0, 1))

    // Load the PDF document
    val original = PDDocument.load(content.data)
    try {
      val total = original.getNumberOfPages
      logger.info(s"Splitting PDF into $total pages")

      // Determine which pages to extract based on configuration
      val pagesToExtract = cfg.chunkRange match {
        case Some(range) =>
          // Ensure range is within bounds (PDFBox uses 0-based indexing)
          range.filter(i => i >= 0 && i < total)
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
          chunkDoc.addPage(original.getPage(idx))

          // Save the new document to a byte array
          val baos = new ByteArrayOutputStream()
          chunkDoc.save(baos)

          // Create a FileContent with the new document
          val fc = FileContent(baos.toByteArray, MimeType.ApplicationPdf)

          // Create a DocChunk with the page information (1-based display)
          DocChunk(fc, s"Page ${idx + 1}", idx, total)
        } finally chunkDoc.close()
      }
    } finally original.close()
  }
}
