package com.tjclp.xlcr
package splitters
package powerpoint

import models.FileContent
import types.MimeType

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.ByteArrayOutputStream

class PowerPointSlideSplitterSpec extends AnyFlatSpec with Matchers {

  it should "split a pptx into per‑slide files" in {
    val pptBytes: Array[Byte] = {
      val ss = new XMLSlideShow()

      // Need a slide layout; get master
      ss.createSlide()
      ss.createSlide()
      val baos = new ByteArrayOutputStream()
      ss.write(baos)
      ss.close()
      baos.toByteArray
    }

    val fc = FileContent(
      pptBytes,
      MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation
    )
    val chunks = DocumentSplitter.split(
      fc,
      SplitConfig(strategy = Some(SplitStrategy.Slide))
    )

    chunks should have length 2
    all(
      chunks.map(_.content.mimeType)
    ) shouldBe MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation

    chunks.foreach { chunk =>
      val ss =
        new XMLSlideShow(new java.io.ByteArrayInputStream(chunk.content.data))
      try ss.getSlides.size() shouldBe 1
      finally ss.close()
    }
  }
}
