package com.tjclp.xlcr
package utils.aspose

import models.FileContent
import types.MimeType
import utils.{DocChunk, DocumentSplitter, SplitConfig, SplitStrategy}

import com.aspose.slides.{Presentation, SaveFormat}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/** Registers Aspose‑powered [[DocumentSplitter]] implementations with the global
  * [[DocumentSplitter]] registry.
  *
  * Right now we only add slide‑level splitting for legacy binary PowerPoint
  * files (MIME `application/vnd.ms-powerpoint`).  Support for other formats can
  * be added here following the same pattern.
  */
object AsposeSplitterRegistry {

  /** Perform the registration once at application start‑up. */
  def registerAll(): Unit = {
    DocumentSplitter.register(
      MimeType.ApplicationVndMsPowerpoint,
      PowerPointSlideAsposeSplitter
    )
  }

  // ---------------------------------------------------------------------------
  //  Splitter implementations
  // ---------------------------------------------------------------------------

  /** Splits a binary *.ppt presentation into individual one‑slide documents.
    */
  object PowerPointSlideAsposeSplitter
      extends DocumentSplitter[MimeType.ApplicationVndMsPowerpoint.type] {

    override def split(
        content: FileContent[MimeType.ApplicationVndMsPowerpoint.type],
        cfg: SplitConfig
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
          pres.save(outBaos, SaveFormat.Ppt)
          pres.dispose()

          val title = Option(srcPres.getSlides.get_Item(idx).getName)
            .map(_.toString)
            .filter(_.nonEmpty)
            .getOrElse(s"Slide ${idx + 1}")

          val fc = FileContent(
            outBaos.toByteArray,
            MimeType.ApplicationVndMsPowerpoint
          )
          DocChunk(fc, title, idx, total)
        }
      } finally {
        srcPres.dispose()
      }
    }
  }
}
