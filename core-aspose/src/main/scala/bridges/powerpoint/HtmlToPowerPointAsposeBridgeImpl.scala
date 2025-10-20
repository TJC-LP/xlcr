package com.tjclp.xlcr
package bridges
package powerpoint

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import scala.util.Using

import com.aspose.slides.Presentation
import org.slf4j.LoggerFactory

import models.FileContent
import renderers.{ Renderer, SimpleRenderer }
import types.MimeType
import types.MimeType.TextHtml
import utils.aspose.AsposeLicense
import utils.resource.ResourceWrappers._

/**
 * Common implementation for HTML to PowerPoint bridges that works with both Scala 2 and Scala 3.
 * This trait contains all the business logic for converting HTML documents to PowerPoint
 * presentations using Aspose.Slides.
 *
 * @tparam O
 *   The specific PowerPoint output MimeType (PPT or PPTX)
 */
trait HtmlToPowerPointAsposeBridgeImpl[O <: MimeType]
    extends HighPrioritySimpleBridge[TextHtml.type, O] {

  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * The SaveFormat to use for the output PowerPoint file (Ppt or Pptx)
   */
  protected def saveFormat: Int

  /**
   * The output MIME type for the PowerPoint file
   */
  protected def outputMimeType: O

  override private[bridges] def outputRenderer: Renderer[M, O] =
    HtmlToPowerPointAsposeRenderer

  /**
   * Renderer that performs HTML to PowerPoint conversion via Aspose.Slides. This works for both
   * PPT and PPTX output formats.
   */
  private object HtmlToPowerPointAsposeRenderer extends SimpleRenderer[M, O] {
    override def render(model: M): FileContent[O] =
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info(
          s"Rendering HTML to ${saveFormat.toString} using Aspose.Slides."
        )

        // Validate input data
        if (model.data == null || model.data.isEmpty) {
          throw new IllegalArgumentException("HTML file data is null or empty")
        }

        val powerPointBytes = Using.Manager { use =>
          val inputStream = use(new ByteArrayInputStream(model.data))

          // Create a new empty presentation
          val presentation = new Presentation()
          use(new DisposableWrapper(presentation))

          // Import HTML content into presentation slides
          // This method parses the HTML and creates slides accordingly
          presentation.getSlides.addFromHtml(inputStream)

          // Validate presentation structure
          if (presentation.getSlides == null) {
            throw new IllegalStateException("Presentation slides collection is null after HTML import")
          }

          val slideCount = presentation.getSlides.size()
          logger.debug(s"Created presentation with $slideCount slides from HTML")

          if (slideCount == 0) {
            logger.warn("HTML import resulted in a presentation with no slides")
          }

          val outputStream = use(new ByteArrayOutputStream())

          // Save to the specified format (PPT or PPTX)
          presentation.save(outputStream, saveFormat)

          outputStream.toByteArray
        }.get

        logger.info(
          s"Successfully converted HTML to ${saveFormat.toString}, output size = ${powerPointBytes.length} bytes."
        )
        FileContent[O](powerPointBytes, outputMimeType)
      } catch {
        case e: NullPointerException =>
          logger.error(
            s"NullPointerException during HTML -> ${saveFormat.toString} conversion. This may be due to corrupted HTML content or unsupported HTML features.",
            e
          )
          throw RendererError(
            s"HTML to ${saveFormat.toString} conversion failed due to null reference: ${Option(e.getMessage).getOrElse("Unknown null pointer error")}",
            Some(e)
          )
        case e: IllegalArgumentException =>
          logger.error(s"Invalid input for HTML -> ${saveFormat.toString} conversion: ${e.getMessage}", e)
          throw RendererError(s"Invalid HTML file: ${e.getMessage}", Some(e))
        case e: IllegalStateException =>
          logger.error(s"Invalid presentation state after HTML import: ${e.getMessage}", e)
          throw RendererError(s"Corrupted HTML or import failure: ${e.getMessage}", Some(e))
        case ex: Exception =>
          logger.error(
            s"Unexpected error during HTML -> ${saveFormat.toString} conversion with Aspose.Slides.",
            ex
          )
          throw RendererError(
            s"HTML to ${saveFormat.toString} conversion failed: ${Option(ex.getMessage).getOrElse(ex.getClass.getSimpleName)}",
            Some(ex)
          )
      }
  }
}
