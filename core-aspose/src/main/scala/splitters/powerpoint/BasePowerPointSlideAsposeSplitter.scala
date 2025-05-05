package com.tjclp.xlcr
package splitters.powerpoint

import models.FileContent
import splitters.{ DocChunk, SplitConfig, SplitStrategy }
import types.MimeType

import com.aspose.slides.Presentation

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

/**
 * Base implementation for PowerPoint slide splitters. Used by both PPT and PPTX splitters.
 */
object BasePowerPointSlideAsposeSplitter {

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

    // Only run when the caller requested slide‑level splitting.
    if (!cfg.hasStrategy(SplitStrategy.Slide))
      return Seq(DocChunk(content, "presentation", 0, 1))

    // Load the source presentation once so we can access slide metadata.
    val srcPres = new Presentation(new ByteArrayInputStream(content.data))

    try {
      val total = srcPres.getSlides.size()

      (0 until total).map { idx =>
        val outBaos = new ByteArrayOutputStream()

        if (cfg.useCloneForSlides) {
          // Safe approach: Create a new presentation and clone the target slide into it
          val newPres = new Presentation()

          // Reload original bytes for each iteration to get a fresh copy
          val srcPresForSlide = new Presentation(new ByteArrayInputStream(content.data))

          try {
            // Clone the target slide into the new presentation
            newPres.getSlides.addClone(srcPresForSlide.getSlides.get_Item(idx))

            // Preserve slide notes if requested in config
            if (
              cfg.preserveSlideNotes && srcPresForSlide.getSlides.get_Item(idx).getNotesSlideManager
                .getNotesSlide != null
            ) {
              val notes = srcPresForSlide.getSlides.get_Item(idx).getNotesSlideManager
                .getNotesSlide
              if (notes != null) {
                // Ensure the destination slide has a notes slide
                if (
                  newPres.getSlides.get_Item(0).getNotesSlideManager
                    .getNotesSlide == null
                ) {
                  newPres.getSlides.get_Item(0).getNotesSlideManager.addNotesSlide()
                }

                // Copy note shapes
                val destNotes = newPres.getSlides.get_Item(0).getNotesSlideManager.getNotesSlide
                for (i <- 0 until notes.getShapes.size())
                  destNotes.getShapes.addClone(notes.getShapes.get_Item(i))
              }
            }

            newPres.save(outBaos, saveFormat)

            // Clean up resources
            srcPresForSlide.dispose()
            newPres.dispose()
          } catch {
            case e: Exception =>
              // Make sure resources are cleaned up if an exception occurs
              srcPresForSlide.dispose()
              newPres.dispose()
              throw e
          }
        } else {
          // Original approach: Remove all slides except the target one
          // NOT recommended - only kept for backward compatibility
          val pres = new Presentation(new ByteArrayInputStream(content.data))
          try {
            // Remove every slide except the target one (iterate backwards).
            var i = pres.getSlides.size() - 1
            while (i >= 0) {
              if (i != idx) pres.getSlides.removeAt(i)
              i -= 1
            }

            pres.save(outBaos, saveFormat)
          } finally
            pres.dispose()
        }

        // Get slide title with null safety
        val title = Option(srcPres.getSlides.get_Item(idx).getName)
          .map(_.toString)
          .filter(_.nonEmpty)
          .getOrElse(s"Slide ${idx + 1}")

        val fc = FileContent(
          outBaos.toByteArray,
          outputMimeType
        )
        DocChunk(fc, title, idx, total)
      }
    } finally
      srcPres.dispose()
  }
}
