package com.tjclp.xlcr
package utils

import models.FileContent
import types.MimeType

import org.apache.poi.xslf.usermodel.XMLSlideShow

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import scala.jdk.CollectionConverters._

class PowerPointSlideSplitter extends DocumentSplitter[MimeType] {

  override def split(
      content: FileContent[MimeType],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {

    if (cfg.strategy != SplitStrategy.Slide ||
        content.mimeType != MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation)
      return Seq(DocChunk(content, "presentation", 0, 1))

    val src = new XMLSlideShow(new ByteArrayInputStream(content.data))
    val slides = src.getSlides.asScala.toList
    val total  = slides.size
    src.close()

    slides.zipWithIndex.map { case (slide, idx) =>
      val dest = new XMLSlideShow()
      dest.createSlide().importContent(slide)
      val baos = new ByteArrayOutputStream()
      dest.write(baos)
      dest.close()

      val fc = FileContent(baos.toByteArray, content.mimeType)
      val title = Option(slide.getTitle).filter(_.nonEmpty).getOrElse(s"Slide ${idx + 1}")
      DocChunk(fc, title, idx, total)
    }
  }
}
