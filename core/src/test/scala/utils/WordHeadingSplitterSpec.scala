package com.tjclp.xlcr
package utils

import models.FileContent
import types.MimeType

import org.apache.poi.xwpf.usermodel.XWPFDocument

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.ByteArrayOutputStream

class WordHeadingSplitterSpec extends AnyFlatSpec with Matchers {

  it should "split a docx by Heading1 paragraphs" in {
    val docBytes: Array[Byte] = {
      val doc = new XWPFDocument()

      val h1 = doc.createParagraph(); h1.setStyle("Heading1"); h1.createRun().setText("Intro")
      val p1 = doc.createParagraph(); p1.createRun().setText("Lorem ipsum 1")

      val h2 = doc.createParagraph(); h2.setStyle("Heading1"); h2.createRun().setText("Details")
      val p2 = doc.createParagraph(); p2.createRun().setText("Lorem ipsum 2")

      val baos = new ByteArrayOutputStream()
      doc.write(baos)
      doc.close(); baos.toByteArray
    }

    val fc = FileContent(docBytes, MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument)
    val chunks = DocumentSplitter.split(fc, SplitConfig(strategy = Some(SplitStrategy.Heading)))

    chunks should have length 2
    chunks.map(_.label) shouldBe Seq("Intro", "Details")
    all(chunks.map(_.content.mimeType)) shouldBe MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument
  }
}
