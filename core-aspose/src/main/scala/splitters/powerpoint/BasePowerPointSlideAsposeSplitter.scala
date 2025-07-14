package com.tjclp.xlcr
package splitters.powerpoint

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import scala.util.Using

import com.aspose.slides.Presentation
import org.slf4j.LoggerFactory

import models.FileContent
import splitters.{
  DocChunk,
  EmptyDocumentException,
  SplitConfig,
  SplitFailureHandler,
  SplitStrategy
}
import types.MimeType
import utils.resource.ResourceWrappers._

/**
 * Base implementation for PowerPoint slide splitters. Used by both PPT and PPTX splitters.
 */
object BasePowerPointSlideAsposeSplitter extends SplitFailureHandler {

  override protected val logger: org.slf4j.Logger = LoggerFactory.getLogger(getClass)

  /**
   * Split a PowerPoint presentation into single slides
   *
   * @param content
   *   The file content to split
   * @param cfg
   *   The split configuration
   * @param saveFormat
   *   The SaveFormat to use (Ppt or Pptx)
   * @param outputMimeType
   *   The output MIME type to use
   * @return
   *   A sequence of document chunks
   */
  def splitPresentation[M <: MimeType](
    content: FileContent[M],
    cfg: SplitConfig,
    saveFormat: Int,
    outputMimeType: M
  ): Seq[DocChunk[_ <: MimeType]] = {

    // Initialize Aspose license on executor
    utils.aspose.AsposeLicense.initializeIfNeeded()

    // Check for valid strategy
    if (!cfg.hasStrategy(SplitStrategy.Slide)) {
      return handleInvalidStrategy(
        content,
        cfg,
        cfg.strategy.map(_.displayName).getOrElse("none"),
        Seq("slide")
      )
    }

    // Wrap main logic with failure handling
    withFailureHandling(content, cfg) {
      // Validate input data
      if (content.data == null || content.data.isEmpty) {
        throw new EmptyDocumentException(
          content.mimeType.toString,
          "PowerPoint file data is null or empty"
        )
      }

      // Load the source presentation once so we can access slide metadata.
      val srcInputStream = new ByteArrayInputStream(content.data)
      val srcPres        = new Presentation(srcInputStream)
      Using.resource(new DisposableWrapper(srcPres)) { wrapper =>
        val srcPres = wrapper.resource
        
        // Validate presentation structure
        if (srcPres.getSlides == null) {
          throw new EmptyDocumentException(
            content.mimeType.toString,
            "Presentation slides collection is null"
          )
        }
        
        val total   = srcPres.getSlides.size()

        // Check if presentation is empty
        if (total == 0) {
          throw new EmptyDocumentException(
            content.mimeType.toString,
            "Presentation contains no slides"
          )
        }

        // Determine which slides to extract based on configuration
        val slidesToExtract = cfg.chunkRange match {
          case Some(range) =>
            // Filter to valid slide indices
            range.filter(i => i >= 0 && i < total)
          case None =>
            0 until total
        }

        slidesToExtract.map { idx =>
          val slideBytes = if (cfg.useCloneForSlides) {
            // ---------- preferred clone‑based branch ----------
            Using.Manager { use =>
              val outBaos = use(new ByteArrayOutputStream())
              val newPres = new Presentation()
              use(new DisposableWrapper(newPres))
              val srcPresForSlide = new Presentation(new ByteArrayInputStream(content.data))
              use(new DisposableWrapper(srcPresForSlide))

              // Copy slide size from source presentation to preserve dimensions
              val srcSlideSize = srcPresForSlide.getSlideSize
              val srcWidth     = srcSlideSize.getSize.getWidth
              val srcHeight    = srcSlideSize.getSize.getHeight

              logger.debug(s"Source presentation dimensions: ${srcWidth}x${srcHeight}")

              newPres.getSlideSize.setSize(
                srcWidth.toFloat,
                srcHeight.toFloat,
                com.aspose.slides.SlideSizeScaleType.DoNotScale
              )

              // Remove the automatically created blank slide so the cloned slide becomes index 0.
              newPres.getSlides.removeAt(0)

              // Clone the target slide and keep a reference to the clone.
              val srcSlide = srcPresForSlide.getSlides.get_Item(idx)
              if (srcSlide == null) {
                logger.warn(s"Slide at index $idx is null, skipping")
                throw new IllegalStateException(s"Slide at index $idx is null")
              }
              
              val clonedSlide = try {
                newPres.getSlides.addClone(srcSlide)
              } catch {
                case e: NullPointerException =>
                  logger.error(s"Failed to clone slide $idx due to null pointer", e)
                  throw new IllegalStateException(s"Failed to clone slide $idx: ${Option(e.getMessage).getOrElse("Unknown error")}", e)
              }

              // Copy notes, if requested, onto the cloned slide (not the blank slide).
              if (cfg.preserveSlideNotes) {
                val srcNotes = srcSlide.getNotesSlideManager.getNotesSlide
                if (srcNotes != null) {
                  val notesMgr = clonedSlide.getNotesSlideManager
                  val destNotes = Option(notesMgr.getNotesSlide).getOrElse {
                    notesMgr.addNotesSlide(); notesMgr.getNotesSlide
                  }

                  // Clear any default shapes for safety, then clone note shapes.
                  destNotes.getShapes.clear()
                  for (i <- 0 until srcNotes.getShapes.size())
                    destNotes.getShapes.addClone(srcNotes.getShapes.get_Item(i))
                }
              }

              newPres.save(outBaos, saveFormat)
              outBaos.toByteArray
            }.get

          } else {
            // ---------- legacy destructive branch ----------
            Using.Manager { use =>
              val outBaos = use(new ByteArrayOutputStream())
              val pres    = new Presentation(new ByteArrayInputStream(content.data))
              use(new DisposableWrapper(pres))

              var i = pres.getSlides.size() - 1
              while (i >= 0) {
                if (i != idx) pres.getSlides.removeAt(i)
                i -= 1
              }
              pres.save(outBaos, saveFormat)
              outBaos.toByteArray
            }.get
          }

          // Determine slide title safely
          val title = try {
            val slide = srcPres.getSlides.get_Item(idx)
            if (slide != null) {
              Option(slide.getName).filter(_.nonEmpty).getOrElse(s"Slide ${idx + 1}")
            } else {
              logger.warn(s"Could not access slide $idx for title extraction")
              s"Slide ${idx + 1}"
            }
          } catch {
            case e: Exception =>
              logger.debug(s"Error getting slide title for index $idx: ${e.getMessage}")
              s"Slide ${idx + 1}"
          }

          val fc = FileContent(slideBytes, outputMimeType)
          DocChunk(fc, title, idx, total)
        }
      }
    }
  }
}
