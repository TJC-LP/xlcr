package com.tjclp.xlcr
package splitters.powerpoint

import com.aspose.slides.SaveFormat

import models.FileContent
import splitters.{ DocChunk, HighPrioritySplitter, SplitConfig }
import types.MimeType

/**
 * Splits a PPTX (*.pptx) presentation into individual one‑slide documents.
 */
object PowerPointPptxSlideAsposeSplitter
    extends HighPrioritySplitter[
      MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation.type
    ] {

  override def split(
    content: FileContent[MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation.type],
    cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] =
    BasePowerPointSlideAsposeSplitter.splitPresentation(
      content,
      cfg,
      SaveFormat.Pptx,
      MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation
    )
}
