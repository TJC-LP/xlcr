package com.tjclp.xlcr
package splitters.powerpoint

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

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
      // Load the source presentation once so we can access slide metadata.
      val srcPres = new Presentation(new ByteArrayInputStream(content.data))

      try {
        val total = srcPres.getSlides.size()

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
          val outBaos = new ByteArrayOutputStream()

          if (cfg.useCloneForSlides) {
            // ---------- preferred clone‑based branch ----------
            val newPres         = new Presentation()
            val srcPresForSlide = new Presentation(new ByteArrayInputStream(content.data))

            try {
              // Remove the automatically created blank slide so the cloned slide becomes index 0.
              newPres.getSlides.removeAt(0)

              // Clone the target slide and keep a reference to the clone.
              val srcSlide    = srcPresForSlide.getSlides.get_Item(idx)
              val clonedSlide = newPres.getSlides.addClone(srcSlide)

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
            } finally {
              srcPresForSlide.dispose()
              newPres.dispose()
            }

          } else {
            // ---------- legacy destructive branch ----------
            val pres = new Presentation(new ByteArrayInputStream(content.data))
            try {
              var i = pres.getSlides.size() - 1
              while (i >= 0) {
                if (i != idx) pres.getSlides.removeAt(i)
                i -= 1
              }
              pres.save(outBaos, saveFormat)
            } finally pres.dispose()
          }

          // Determine slide title safely
          val title = Option(srcPres.getSlides.get_Item(idx).getName)
            .filter(_.nonEmpty)
            .getOrElse(s"Slide ${idx + 1}")

          val fc = FileContent(outBaos.toByteArray, outputMimeType)
          DocChunk(fc, title, idx, total)
        }
      } finally srcPres.dispose()
    }
  }
}
