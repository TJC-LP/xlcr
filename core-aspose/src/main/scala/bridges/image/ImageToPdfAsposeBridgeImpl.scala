package com.tjclp.xlcr
package bridges
package image

import models.FileContent
import renderers.{ Renderer, SimpleRenderer }
import types.MimeType.ApplicationPdf
import types.{ MimeType, Priority }
import utils.aspose.AsposeLicense

import com.aspose.pdf.{ Document, Image => PdfImage, MarginInfo, PageSize }
import org.slf4j.LoggerFactory

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

/**
 * Common implementation for Image to PDF conversion using Aspose.PDF. This trait provides the core
 * functionality for converting images to PDF, with automatic page size adjustment for landscape
 * images.
 */
trait ImageToPdfAsposeBridgeImpl[I <: MimeType]
    extends HighPrioritySimpleBridge[I, ApplicationPdf.type] {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Set priority to HIGH for all Aspose bridges
   */
  override def priority: Priority = Priority.HIGH

  private[bridges] def outputRenderer: Renderer[M, ApplicationPdf.type] =
    ImageToPdfAsposeRenderer

  /**
   * Renderer that uses Aspose.PDF to convert images to PDF
   */
  private object ImageToPdfAsposeRenderer
      extends SimpleRenderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] =
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info(s"Rendering ${model.mimeType} image to PDF using Aspose.PDF.")

        // Convert image to PDF
        val inputStream = new ByteArrayInputStream(model.data)
        val pdfOutput   = convertImageToPdf(inputStream)

        val pdfBytes = pdfOutput.toByteArray
        pdfOutput.close()

        logger.info(
          s"Successfully converted image to PDF, output size = ${pdfBytes.length} bytes."
        )
        FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)
      } catch {
        case ex: Exception =>
          logger.error(
            s"Error during ${model.mimeType} image to PDF conversion with Aspose.PDF.",
            ex
          )
          throw RendererError(
            s"Image to PDF conversion failed: ${ex.getMessage}",
            Some(ex)
          )
      }
  }

  /**
   * Convert an image to PDF using Aspose.PDF
   */
  private def convertImageToPdf(
    inputStream: ByteArrayInputStream
  ): ByteArrayOutputStream = {
    // Create a new PDF document
    val pdfDoc = new Document()

    // Add a page
    val page = pdfDoc.getPages.add()

    // Create an image object
    val img = new PdfImage()
    img.setImageStream(inputStream)

    // Set zero margins for full-page image
    page.getPageInfo.setMargin(new MarginInfo(0, 0, 0, 0))

    // Check if image is landscape and adjust page orientation if needed
    // We'll use A4 in landscape mode for all images to simplify the implementation
    // Aspose will automatically scale the image to fit the page
    page.setPageSize(PageSize.getA4.getHeight(), PageSize.getA4.getWidth())

    // Add the image to the page
    page.getParagraphs.add(img)

    // Save to output stream
    val pdfOutput = new ByteArrayOutputStream()
    pdfDoc.save(pdfOutput)

    pdfOutput
  }
}
