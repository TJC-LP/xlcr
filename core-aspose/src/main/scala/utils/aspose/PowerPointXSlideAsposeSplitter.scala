package com.tjclp.xlcr
package utils.aspose

import models.FileContent
import types.MimeType
import utils.{DocChunk, DocumentSplitter, SplitConfig, SplitStrategy}

import com.aspose.slides.SaveFormat

/** Splits a PPTX (*.pptx) presentation into individual one‑slide documents.
  */
object PowerPointXSlideAsposeSplitter
    extends HighPrioritySplitter[MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation.type] {

  override def split(
      content: FileContent[MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation.type],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    BasePowerPointSlideAsposeSplitter.splitPresentation(
      content, 
      cfg, 
      SaveFormat.Pptx, 
      MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation
    )
  }
}