package com.tjclp.xlcr
package splitters
package pdf

import compat.aspose.{AsposePdfDocument, AsposePdfResolution, AsposePdfJpegDevice, AsposePdfPngDevice}
import models.FileContent
import types.MimeType
import utils.aspose.AsposeLicense

import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/** Aspose implementation for splitting PDF files by page.
  *
  * This splitter creates a separate PDF file for each page in the original document,
  * or converts pages to images (PNG or JPEG) if requested, using the Aspose.Pdf library.
  */
class PdfPageAsposeSplitter
    extends HighPrioritySplitter[MimeType.ApplicationPdf.type] {
  private val logger = LoggerFactory.getLogger(getClass)

  // Initialize Aspose license
  AsposeLicense.initializeIfNeeded()

  override def split(
      content: FileContent[MimeType.ApplicationPdf.type],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {

    if (!cfg.hasStrategy(SplitStrategy.Page))
      return Seq(DocChunk(content, "document", 0, 1))

    // Determine output format - use PDF by default
    val outputFormat = cfg.outputFormat.getOrElse("pdf").toLowerCase

    try {
      // Load PDF document
      val pdfDocument = new AsposePdfDocument(new ByteArrayInputStream(content.data))
      val pageCount = pdfDocument.getPages.size

      (1 to pageCount).map { pageIndex =>
        outputFormat match {
          case "png" =>
            // Render as PNG
            logger.info(s"Rendering PDF page $pageIndex of $pageCount as PNG")
            val imageBytes = renderPageAsImage(
              pdfDocument,
              pageIndex,
              "png",
              cfg.imageDpi,
              cfg.maxImageWidth,
              cfg.maxImageHeight
            )
            val fc = FileContent(imageBytes, MimeType.ImagePng)
            DocChunk(fc, s"Page $pageIndex", pageIndex - 1, pageCount)

          case "jpg" | "jpeg" =>
            // Render as JPEG
            logger.info(s"Rendering PDF page $pageIndex of $pageCount as JPEG")
            val imageBytes = renderPageAsImage(
              pdfDocument,
              pageIndex,
              "jpg",
              cfg.imageDpi,
              cfg.maxImageWidth,
              cfg.maxImageHeight,
              cfg.jpegQuality
            )
            val fc = FileContent(imageBytes, MimeType.ImageJpeg)
            DocChunk(fc, s"Page $pageIndex", pageIndex - 1, pageCount)

          case _ =>
            // Default: Extract as PDF (one page per PDF)
            val newDocument = new AsposePdfDocument()
            newDocument.getPages.add(pdfDocument.getPages.get_Item(pageIndex))

            // Save to byte array
            val outputStream = new ByteArrayOutputStream()
            newDocument.save(outputStream)
            val fc =
              FileContent(outputStream.toByteArray, MimeType.ApplicationPdf)
            DocChunk(fc, s"Page $pageIndex", pageIndex - 1, pageCount)
        }
      }
    } catch {
      case e: Exception =>
        logger.error(s"Error splitting PDF: ${e.getMessage}", e)
        Seq(DocChunk(content, "document", 0, 1))
    }
  }

  /** Renders a PDF page as an image.
    *
    * @param document      The PDF document
    * @param pageIndex     The page number (1-based)
    * @param format        The image format ("png" or "jpg")
    * @param dpi           The resolution in DPI
    * @param maxWidth      Maximum width in pixels
    * @param maxHeight     Maximum height in pixels
    * @param jpegQuality   JPEG quality (0.0-1.0) for JPG format
    * @return The image bytes
    */
  private def renderPageAsImage(
      document: AsposePdfDocument,
      pageIndex: Int,
      format: String,
      dpi: Int = 300,
      maxWidth: Int = 2000,
      maxHeight: Int = 2000,
      jpegQuality: Float = 0.85f
  ): Array[Byte] = {
    val outputStream = new ByteArrayOutputStream()

    // Calculate resolution
    val resolution = new AsposePdfResolution(dpi)

    // Create appropriate image device based on format
    val device =
      if (format.equalsIgnoreCase("jpg") || format.equalsIgnoreCase("jpeg")) {
        // Quality is set in the constructor (0-100)
        val quality = (jpegQuality * 100).toInt
        new AsposePdfJpegDevice(resolution, quality)
      } else {
        // Default to PNG
        new AsposePdfPngDevice(resolution)
      }

    // Process the page
    device.process(document.getPages.get_Item(pageIndex), outputStream)
    outputStream.close()

    outputStream.toByteArray
  }
}
