package com.tjclp.xlcr
package utils.aspose

import models.FileContent
import types.MimeType
import utils.{DocChunk, DocumentSplitter, SplitConfig, SplitStrategy}

import com.aspose.slides.SaveFormat

/** Splits a binary *.ppt presentation into individual one‑slide documents.
  */
object PowerPointSlideAsposeSplitter
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