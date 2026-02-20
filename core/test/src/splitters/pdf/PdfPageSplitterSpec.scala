package com.tjclp.xlcr
package splitters
package pdf

import java.io.ByteArrayOutputStream

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.{ PDDocument, PDPage, PDPageContentStream }
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import models.FileContent
import types.MimeType

class PdfPageSplitterSpec extends AnyFlatSpec with Matchers {

  private def createTestPdf(pageCount: Int): Array[Byte] = {
    val doc = new PDDocument()

    (1 to pageCount).foreach { pageNum =>
      val page = new PDPage()
      doc.addPage(page)

      // Add some content to each page
      val contentStream = new PDPageContentStream(doc, page)
      contentStream.beginText()
      contentStream.setFont(new PDType1Font(FontName.HELVETICA), 12)
      contentStream.newLineAtOffset(100, 700)
      contentStream.showText(s"Page $pageNum of $pageCount")
      contentStream.endText()
      contentStream.close()
    }

    val baos = new ByteArrayOutputStream()
    doc.save(baos)
    doc.close()
    baos.toByteArray
  }

  "PdfPageSplitter" should "split a PDF into individual pages" in {
    val pdfBytes = createTestPdf(5)
    val fc       = FileContent(pdfBytes, MimeType.ApplicationPdf)

    val config = SplitConfig(strategy = Some(SplitStrategy.Page))
    val chunks = DocumentSplitter.split(fc, config)

    chunks.length shouldBe 5
    chunks.map(_.label) shouldBe List("Page 1", "Page 2", "Page 3", "Page 4", "Page 5")

    // Verify each chunk is a valid PDF
    chunks.foreach { chunk =>
      val doc = Loader.loadPDF(chunk.content.data)
      doc.getNumberOfPages shouldBe 1
      doc.close()
    }
  }

  it should "limit pages using chunkRange" in {
    val pdfBytes = createTestPdf(10)
    val fc       = FileContent(pdfBytes, MimeType.ApplicationPdf)

    // Limit to first 3 pages
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page),
      chunkRange = Some(0 until 3)
    )
    val chunks = DocumentSplitter.split(fc, config)

    chunks.length shouldBe 3
    chunks.map(_.label) shouldBe List("Page 1", "Page 2", "Page 3")
    chunks.map(_.index) shouldBe List(0, 1, 2)
  }

  it should "extract middle range of pages" in {
    val pdfBytes = createTestPdf(8)
    val fc       = FileContent(pdfBytes, MimeType.ApplicationPdf)

    // Extract pages 3-6 (0-indexed: 2-5)
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page),
      chunkRange = Some(2 until 6)
    )
    val chunks = DocumentSplitter.split(fc, config)

    chunks.length shouldBe 4
    chunks.map(_.label) shouldBe List("Page 3", "Page 4", "Page 5", "Page 6")
    chunks.map(_.index) shouldBe List(2, 3, 4, 5) // Original indices preserved
  }

  it should "extract single page using chunkRange" in {
    val pdfBytes = createTestPdf(5)
    val fc       = FileContent(pdfBytes, MimeType.ApplicationPdf)

    // Extract only page 4 (index 3)
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page),
      chunkRange = Some(3 until 4)
    )
    val chunks = DocumentSplitter.split(fc, config)

    chunks.length shouldBe 1
    chunks.head.label shouldBe "Page 4"
    chunks.head.index shouldBe 3 // Original index preserved
    chunks.head.total shouldBe 5 // Original total preserved

    // Verify it's the correct page
    val doc = Loader.loadPDF(chunks.head.content.data)
    doc.getNumberOfPages shouldBe 1
    doc.close()
  }

  it should "handle out-of-bounds ranges gracefully" in {
    val pdfBytes = createTestPdf(3)
    val fc       = FileContent(pdfBytes, MimeType.ApplicationPdf)

    // Range extends beyond available pages
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page),
      chunkRange = Some(1 until 10)
    )
    val chunks = DocumentSplitter.split(fc, config)

    chunks.length shouldBe 2
    chunks.map(_.label) shouldBe List("Page 2", "Page 3")
  }

  it should "return single chunk when range is completely out of bounds" in {
    val pdfBytes = createTestPdf(3)
    val fc       = FileContent(pdfBytes, MimeType.ApplicationPdf)

    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page),
      chunkRange = Some(10 until 20)
    )
    val chunks = DocumentSplitter.split(fc, config)

    // Should return single chunk with error due to failure handling
    chunks.length shouldBe 1
    chunks.head.label shouldBe "document"
    chunks.head.total shouldBe 1
    // Original content should be preserved
    chunks.head.content.data shouldBe pdfBytes
  }

  it should "handle empty PDFs gracefully" in {
    val emptyPdf = {
      val doc  = new PDDocument()
      val baos = new ByteArrayOutputStream()
      doc.save(baos)
      doc.close()
      baos.toByteArray
    }

    val fc     = FileContent(emptyPdf, MimeType.ApplicationPdf)
    val config = SplitConfig(strategy = Some(SplitStrategy.Page))
    val chunks = DocumentSplitter.split(fc, config)

    // Should return single chunk with error due to failure handling
    chunks.length shouldBe 1
    chunks.head.label shouldBe "document"
    chunks.head.total shouldBe 1
    // Original content should be preserved
    chunks.head.content.data shouldBe emptyPdf
  }

  it should "maintain correct total count with chunkRange" in {
    val pdfBytes = createTestPdf(20)
    val fc       = FileContent(pdfBytes, MimeType.ApplicationPdf)

    // Extract pages 10-15
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page),
      chunkRange = Some(9 until 15)
    )
    val chunks = DocumentSplitter.split(fc, config)

    chunks.length shouldBe 6
    chunks.foreach { chunk =>
      chunk.total shouldBe 20 // Total reflects original count, not filtered
    }
  }

  it should "work with deprecated pageRange for backward compatibility" in {
    val pdfBytes = createTestPdf(5)
    val fc       = FileContent(pdfBytes, MimeType.ApplicationPdf)

    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page),
      chunkRange = Some(0 until 2)
    )

    // Verify pageRange returns same as chunkRange
    config.pageRange shouldBe config.chunkRange
    config.pageRange shouldBe Some(0 until 2)

    val chunks = DocumentSplitter.split(fc, config)
    chunks.length shouldBe 2
    chunks.map(_.label) shouldBe List("Page 1", "Page 2")
  }
}
