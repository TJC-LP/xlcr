package com.tjclp.xlcr
package bridges.powerpoint

import java.io.ByteArrayOutputStream

import scala.util.Using

import com.aspose.pdf.{ Document => PdfDocument }
import org.scalatest.BeforeAndAfterAll

import base.BridgeSpec
import models.FileContent
import types.MimeType.{
  ApplicationPdf,
  ApplicationVndMsPowerpoint,
  ApplicationVndOpenXmlFormatsPresentationmlPresentation
}
import utils.aspose.AsposeLicense

class PdfToPowerPointAsposeBridgeSpec extends BridgeSpec with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    super.beforeAll()
    AsposeLicense.initializeIfNeeded()
  }

  /**
   * Helper to create a simple test PDF with specified number of pages
   */
  private def createTestPdf(pageCount: Int = 1): Array[Byte] =
    Using.Manager { use =>
      val pdfDocument = new PdfDocument()
      use(new utils.resource.ResourceWrappers.DisposableWrapper(pdfDocument))

      for (i <- 1 to pageCount) {
        val page = pdfDocument.getPages.add()
        // Add simple text to the page
        val textFragment = new com.aspose.pdf.TextFragment(s"Page $i")
        page.getParagraphs.add(textFragment)
      }

      val outputStream = use(new ByteArrayOutputStream())
      pdfDocument.save(outputStream)
      outputStream.toByteArray
    }.get

  "PdfToPptxAsposeBridge" should "convert PDF to PPTX" in {
    // Create a simple 2-page PDF
    val pdfBytes = createTestPdf(pageCount = 2)
    val input    = FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)

    // Convert PDF to PPTX
    val result = PdfToPptxAsposeBridge.convert(input)

    // Verify the result
    result.mimeType shouldBe ApplicationVndOpenXmlFormatsPresentationmlPresentation

    // PPTX files should start with PK (ZIP signature)
    new String(result.data.take(2)) should be("PK")

    // Minimum size check to ensure it's a valid PPTX
    result.data.length should be > 1000
  }

  it should "handle single page PDF" in {
    val pdfBytes = createTestPdf(pageCount = 1)
    val input    = FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)

    // Convert to PPTX
    val result = PdfToPptxAsposeBridge.convert(input)
    result.mimeType shouldBe ApplicationVndOpenXmlFormatsPresentationmlPresentation

    // Verify it's a valid PowerPoint file
    result.data.length should be > 1000
    new String(result.data.take(2)) should be("PK")
  }

  it should "handle multi-page PDF" in {
    val pdfBytes = createTestPdf(pageCount = 5)
    val input    = FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)

    // Convert to PPTX - each page should become a slide
    val result = PdfToPptxAsposeBridge.convert(input)
    result.mimeType shouldBe ApplicationVndOpenXmlFormatsPresentationmlPresentation

    // Verify it's a valid PowerPoint file
    result.data.length should be > 1000
    new String(result.data.take(2)) should be("PK")
  }

  "PdfToPptAsposeBridge" should "convert PDF to PPT" in {
    val pdfBytes = createTestPdf(pageCount = 2)
    val input    = FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)

    // Convert PDF to PPT
    val result = PdfToPptAsposeBridge.convert(input)

    // Verify the result
    result.mimeType shouldBe ApplicationVndMsPowerpoint

    // PPT files have OLE2 header (starts with specific bytes)
    result.data.length should be > 1000
  }

  it should "handle empty PDF gracefully" in {
    // Create a PDF with no pages
    val emptyPdfBytes = Using.Manager { use =>
      val pdfDocument = new PdfDocument()
      use(new utils.resource.ResourceWrappers.DisposableWrapper(pdfDocument))

      val outputStream = use(new ByteArrayOutputStream())
      pdfDocument.save(outputStream)
      outputStream.toByteArray
    }.get

    val input = FileContent[ApplicationPdf.type](emptyPdfBytes, ApplicationPdf)

    // Should still produce a valid PPT, even if it has no slides
    val result = PdfToPptAsposeBridge.convert(input)
    result.mimeType shouldBe ApplicationVndMsPowerpoint
    result.data.length should be > 0
  }
}
