package com.tjclp.xlcr
package bridges
package powerpoint

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import scala.util.Using

import com.aspose.slides.{ Presentation, SaveFormat }
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

        val pdfBytes = Using.Manager { use =>
          val inputStream  = use(new ByteArrayInputStream(model.data))
          val presentation = new Presentation(inputStream)
          use(new DisposableWrapper(presentation))
          val pdfOutput = use(new ByteArrayOutputStream())

          presentation.save(pdfOutput, SaveFormat.Pdf)
          pdfOutput.toByteArray
        }.get

        logger.info(
          s"Successfully converted PowerPoint to PDF, output size = ${pdfBytes.length} bytes."
        )
        FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)
      } catch {
        case ex: Exception =>
          logger.error(
            "Error during PowerPoint -> PDF conversion with Aspose.Slides.",
            ex
          )
          throw RendererError(
            s"PowerPoint to PDF conversion failed: ${ex.getMessage}",
            Some(ex)
          )
      }
  }
}
