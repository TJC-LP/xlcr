package com.tjclp.xlcr
package splitters

import models.FileContent
import types.MimeType

import org.apache.pdfbox.pdmodel.{PDDocument, PDPage}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.ByteArrayOutputStream

/**
  * Basic sanity checks for the new DocumentSplitter infrastructure.
  */
class DocumentSplitterSpec extends AnyFlatSpec with Matchers {

  it should "split a two‑page PDF into two separate chunks" in {
    // -------------------------------------------------------------------
    // Build an in‑memory two‑page PDF so we don't need binary fixtures.
    // -------------------------------------------------------------------
    val pdfBytes: Array[Byte] = {
      val doc = new PDDocument()
      doc.addPage(new PDPage())
      doc.addPage(new PDPage())
      val baos = new ByteArrayOutputStream()
      doc.save(baos)
      doc.close()
      baos.toByteArray
    }

    val fc = FileContent(pdfBytes, MimeType.ApplicationPdf)

    val chunks = DocumentSplitter.split(fc, SplitConfig(strategy = Some(SplitStrategy.Page)))

    chunks should have length 2
    all(chunks.map(_.content.mimeType)) shouldBe MimeType.ApplicationPdf

    chunks.foreach { chunk =>
      val doc = PDDocument.load(chunk.content.data)
      try doc.getNumberOfPages shouldBe 1
      finally doc.close()
    }
  }
}
