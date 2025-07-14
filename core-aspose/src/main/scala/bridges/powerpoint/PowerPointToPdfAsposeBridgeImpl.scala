package com.tjclp.xlcr
package bridges
package powerpoint

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import scala.util.Using

import com.aspose.slides.{ PdfOptions, PdfTextCompression, Presentation, SaveFormat }
import org.slf4j.LoggerFactory

import models.FileContent
import renderers.{ Renderer, SimpleRenderer }
import types.MimeType
import types.MimeType.ApplicationPdf
import utils.aspose.AsposeLicense
import utils.resource.ResourceWrappers._

/**
 * Common implementation for PowerPoint to PDF bridges that works with both Scala 2 and Scala 3.
 * This trait contains all the business logic for converting PowerPoint files to PDF using
 * Aspose.Slides.
 *
 * @tparam I
 *   The specific PowerPoint input MimeType
 */
trait PowerPointToPdfAsposeBridgeImpl[I <: MimeType]
    extends HighPrioritySimpleBridge[I, ApplicationPdf.type] {

  private val logger = LoggerFactory.getLogger(getClass)

  override private[bridges] def outputRenderer: Renderer[M, ApplicationPdf.type] =
    PowerPointToPdfAsposeRenderer

  /**
   * Renderer that performs PowerPoint to PDF conversion via Aspose.Slides. This works for both PPT
   * and PPTX formats.
   */
  private object PowerPointToPdfAsposeRenderer
      extends SimpleRenderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] =
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info(
          s"Rendering ${model.mimeType.getClass.getSimpleName} to PDF using Aspose.Slides."
        )

        // Validate input data
        if (model.data == null || model.data.isEmpty) {
          throw new IllegalArgumentException("PowerPoint file data is null or empty")
        }

        val pdfBytes = Using.Manager { use =>
          val inputStream  = use(new ByteArrayInputStream(model.data))
          val presentation = new Presentation(inputStream)
          use(new DisposableWrapper(presentation))

          // Validate presentation structure
          if (presentation.getSlides == null) {
            throw new IllegalStateException("Presentation slides collection is null")
          }

          val slideCount = presentation.getSlides.size()
          logger.debug(s"Loaded presentation with $slideCount slides")

          if (slideCount == 0) {
            throw new IllegalStateException("Presentation has no slides")
          }

          val pdfOutput = use(new ByteArrayOutputStream())

          // Use PDF options to handle problematic content more gracefully
          val pdfOptions = new PdfOptions()
          pdfOptions.setTextCompression(PdfTextCompression.None)

          // Try to save with options first
          try
            presentation.save(pdfOutput, SaveFormat.Pdf, pdfOptions)
          catch {
            case e: NullPointerException =>
              logger.warn(
                "NullPointerException during PDF conversion with options, trying without options",
                e
              )
              // Fallback to simple save without options
              presentation.save(pdfOutput, SaveFormat.Pdf)
          }

          pdfOutput.toByteArray
        }.get

        logger.info(
          s"Successfully converted PowerPoint to PDF, output size = ${pdfBytes.length} bytes."
        )
        FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)
      } catch {
        case e: NullPointerException =>
          logger.error(
            s"NullPointerException during PowerPoint -> PDF conversion. This may be due to corrupted content or unsupported features in the presentation.",
            e
          )
          throw RendererError(
            s"PowerPoint to PDF conversion failed due to null reference: ${Option(e.getMessage).getOrElse("Unknown null pointer error")}",
            Some(e)
          )
        case e: IllegalArgumentException =>
          logger.error(s"Invalid input for PowerPoint -> PDF conversion: ${e.getMessage}", e)
          throw RendererError(s"Invalid PowerPoint file: ${e.getMessage}", Some(e))
        case e: IllegalStateException =>
          logger.error(s"Invalid presentation state: ${e.getMessage}", e)
          throw RendererError(s"Corrupted PowerPoint file: ${e.getMessage}", Some(e))
        case ex: Exception =>
          logger.error(
            "Unexpected error during PowerPoint -> PDF conversion with Aspose.Slides.",
            ex
          )
          throw RendererError(
            s"PowerPoint to PDF conversion failed: ${Option(ex.getMessage).getOrElse(ex.getClass.getSimpleName)}",
            Some(ex)
          )
      }
  }
}
