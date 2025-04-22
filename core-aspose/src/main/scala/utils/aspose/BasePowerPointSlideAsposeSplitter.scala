package com.tjclp.xlcr
package utils.aspose

import models.FileContent
import types.MimeType
import utils.{DocChunk, DocumentSplitter, SplitConfig, SplitStrategy}

import com.aspose.slides.{Presentation, SaveFormat}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/** Base implementation for PowerPoint slide splitters.
  * Used by both PPT and PPTX splitters.
  */
object BasePowerPointSlideAsposeSplitter {

  /** Split a PowerPoint presentation into single slides
    * 
    * @param content The file content to split
    * @param cfg The split configuration
    * @param saveFormat The SaveFormat to use (Ppt or Pptx)
    * @param outputMimeType The output MIME type to use
    * @return A sequence of document chunks
    */
  def splitPresentation[M <: MimeType](
      content: FileContent[M],
      cfg: SplitConfig,
      saveFormat: Int,
      outputMimeType: M
  ): Seq[DocChunk[_ <: MimeType]] = {

    // Only run when the caller requested slide‑level splitting.
    if (cfg.strategy != SplitStrategy.Slide)
      return Seq(DocChunk(content, "presentation", 0, 1))

    // Load the source presentation once so we can access slide metadata.
    val srcPres = new Presentation(new ByteArrayInputStream(content.data))

    try {
      val total = srcPres.getSlides.size()

      (0 until total).map { idx =>
        // Reload original bytes for each iteration to get a fresh copy.
        val pres = new Presentation(new ByteArrayInputStream(content.data))

        // Remove every slide except the target one (iterate backwards).
        var i = pres.getSlides.size() - 1
        while (i >= 0) {
          if (i != idx) pres.getSlides.removeAt(i)
          i -= 1
        }

        val outBaos = new ByteArrayOutputStream()
        pres.save(outBaos, saveFormat)
        pres.dispose()

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
    } finally {
      srcPres.dispose()
    }
  }
}