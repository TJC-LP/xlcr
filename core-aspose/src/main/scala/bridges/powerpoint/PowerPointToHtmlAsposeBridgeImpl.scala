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
import types.MimeType.TextHtml
import utils.aspose.{ AsposeLicense, BridgeContext }
import utils.resource.ResourceWrappers._

/**
 * Common implementation for PowerPoint to HTML bridges that works with both Scala 2 and Scala 3.
 * This trait contains all the business logic for converting PowerPoint presentations to HTML
 * documents using Aspose.Slides.
 *
 * @tparam I
 *   The specific PowerPoint input MimeType (PPT or PPTX)
 */
trait PowerPointToHtmlAsposeBridgeImpl[I <: MimeType]
    extends HighPrioritySimpleBridge[I, TextHtml.type] {

  private val logger = LoggerFactory.getLogger(getClass)

  override private[bridges] def outputRenderer: Renderer[M, TextHtml.type] =
    PowerPointToHtmlAsposeRenderer

  /**
   * Renderer that performs PowerPoint to HTML conversion via Aspose.Slides. This works for both
   * PPT and PPTX input formats.
   */
  private object PowerPointToHtmlAsposeRenderer extends SimpleRenderer[M, TextHtml.type] {
    override def render(model: M): FileContent[TextHtml.type] =
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info(
          s"Rendering ${model.mimeType.getClass.getSimpleName} to HTML using Aspose.Slides."
        )

        // Validate input data
        if (model.data == null || model.data.isEmpty) {
          throw new IllegalArgumentException("PowerPoint file data is null or empty")
        }

        val htmlBytes = Using.Manager { use =>
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

          // Optionally create a clean copy without masters/layouts before conversion
          // This enables cleaner serialization and template swapping workflows
          val stripMasters = BridgeContext.get().stripMasters
          val presentationToConvert = if (stripMasters) {
            try {
              logger.info(s"Creating blank presentation copy without masters/layouts (--strip-masters)")

              // Create a new blank presentation
              val blankPresentation = new Presentation()
              use(new DisposableWrapper(blankPresentation))

              // Remove the default empty slide from blank presentation
              if (blankPresentation.getSlides.size() > 0) {
                blankPresentation.getSlides.removeAt(0)
              }

              // Copy all slides from original to blank presentation
              // This copies slide content without carrying over masters/layouts
              for (i <- 0 until presentation.getSlides.size()) {
                val sourceSlide = presentation.getSlides.get_Item(i)
                blankPresentation.getSlides.addClone(sourceSlide)
              }

              logger.info(s"Copied ${presentation.getSlides.size()} slides to blank presentation (--strip-masters)")
              blankPresentation
            } catch {
              case ex: Exception =>
                // If copy fails, fall back to using original presentation
                logger.warn(s"Failed to create blank copy, using original presentation: ${ex.getMessage}")
                presentation
            }
          } else {
            presentation
          }

          val htmlOutput = use(new ByteArrayOutputStream())

          // Save as HTML with default options
          // Aspose.Slides will automatically handle HTML formatting
          try
            presentationToConvert.save(htmlOutput, SaveFormat.Html)
          catch {
            case e: NullPointerException =>
              logger.warn(
                "NullPointerException during HTML conversion, this may indicate corrupted content",
                e
              )
              // Re-throw as we cannot recover from this
              throw e
          }

          htmlOutput.toByteArray
        }.get

        logger.info(
          s"Successfully converted PowerPoint to HTML, output size = ${htmlBytes.length} bytes."
        )
        FileContent[TextHtml.type](htmlBytes, TextHtml)
      } catch {
        case e: NullPointerException =>
          logger.error(
            s"NullPointerException during PowerPoint -> HTML conversion. This may be due to corrupted content or unsupported features in the presentation.",
            e
          )
          throw RendererError(
            s"PowerPoint to HTML conversion failed due to null reference: ${Option(e.getMessage).getOrElse("Unknown null pointer error")}",
            Some(e)
          )
        case e: IllegalArgumentException =>
          logger.error(s"Invalid input for PowerPoint -> HTML conversion: ${e.getMessage}", e)
          throw RendererError(s"Invalid PowerPoint file: ${e.getMessage}", Some(e))
        case e: IllegalStateException =>
          logger.error(s"Invalid presentation state: ${e.getMessage}", e)
          throw RendererError(s"Corrupted PowerPoint file: ${e.getMessage}", Some(e))
        case ex: Exception =>
          logger.error(
            "Unexpected error during PowerPoint -> HTML conversion with Aspose.Slides.",
            ex
          )
          throw RendererError(
            s"PowerPoint to HTML conversion failed: ${Option(ex.getMessage).getOrElse(ex.getClass.getSimpleName)}",
            Some(ex)
          )
      }
  }
}
