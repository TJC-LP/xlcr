package com.tjclp.xlcr
package splitters.powerpoint

import models.FileContent
import splitters.{
  DocChunk,
  HighPrioritySplitter,
  SplitConfig
}
import types.MimeType

import com.aspose.slides.SaveFormat

/** Splits a binary *.ppt presentation into individual one‑slide documents.
  */
object PowerPointPptSlideAsposeSplitter
    extends HighPrioritySplitter[MimeType.ApplicationVndMsPowerpoint.type] {

  override def split(
      content: FileContent[MimeType.ApplicationVndMsPowerpoint.type],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    BasePowerPointSlideAsposeSplitter.splitPresentation(
      content, 
      cfg, 
      SaveFormat.Ppt, 
      MimeType.ApplicationVndMsPowerpoint
    )
  }
}