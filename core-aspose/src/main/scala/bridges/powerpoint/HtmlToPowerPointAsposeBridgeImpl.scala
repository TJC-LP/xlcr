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
import utils.aspose.{ AsposeLicense, BridgeContext }
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

          // Remove the default blank slide that Aspose creates
          // Without this, the output would have an unwanted blank slide at the beginning
          if (presentation.getSlides.size() > 0) {
            presentation.getSlides.removeAt(0)
          }

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

          // Remove unused master slides and layouts to create cleaner presentations
          // This helps ensure smoother round-trip conversions by eliminating
          // default templates that Aspose may have applied during HTML import
          // Note: This is enabled by default for HTML->PowerPoint conversions
          val stripMasters = BridgeContext.get().stripMasters
          val shouldCleanupMasters = stripMasters || true // Always cleanup for HTML->PowerPoint by default

          if (shouldCleanupMasters) {
            try {
              // First remove unused layouts from each master
              var totalLayoutsRemoved = 0
              val masters = presentation.getMasters
              for (i <- 0 until masters.size()) {
                val master = masters.get_Item(i)
                val layoutsBefore = master.getLayoutSlides.size()
                master.getLayoutSlides.removeUnused()
                val layoutsAfter = master.getLayoutSlides.size()
                totalLayoutsRemoved += (layoutsBefore - layoutsAfter)
              }
              if (totalLayoutsRemoved > 0) {
                if (stripMasters) {
                  logger.info(s"Removed $totalLayoutsRemoved unused layout slides (--strip-masters)")
                } else {
                  logger.debug(s"Removed $totalLayoutsRemoved unused layout slides")
                }
              }

              // Then remove unused master slides
              val mastersBeforeCleanup = presentation.getMasters.size()
              presentation.getMasters.removeUnused(true) // true = ignore preserve field
              val mastersAfterCleanup = presentation.getMasters.size()
              if (mastersBeforeCleanup > mastersAfterCleanup) {
                val message = s"Removed ${mastersBeforeCleanup - mastersAfterCleanup} unused master slides"
                if (stripMasters) {
                  logger.info(s"$message (--strip-masters)")
                } else {
                  logger.debug(message)
                }
              }
            } catch {
              case ex: Exception =>
                // Don't fail the entire conversion if cleanup fails
                logger.warn(s"Failed to remove unused masters/layouts: ${ex.getMessage}")
            }
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
