package com.tjclp.xlcr
package bridges.powerpoint

import com.aspose.slides.{ Presentation, SaveFormat }
import org.scalatest.BeforeAndAfterAll

import base.BridgeSpec
import models.FileContent
import types.MimeType.{
  ApplicationVndMsPowerpoint,
  ApplicationVndOpenXmlFormatsPresentationmlPresentation,
  TextHtml
}
import utils.aspose.AsposeLicense

import java.io.ByteArrayOutputStream

class PowerPointToHtmlAsposeBridgeSpec extends BridgeSpec with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    super.beforeAll()
    AsposeLicense.initializeIfNeeded()
  }

  /**
   * Helper method to create a simple test PPTX presentation
   */
  private def createTestPptx(): Array[Byte] = {
    val presentation = new Presentation()
    try {
      // Add a slide with text
      val slide = presentation.getSlides.get_Item(0)
      val shape = slide.getShapes.addAutoShape(
        com.aspose.slides.ShapeType.Rectangle,
        100,
        100,
        400,
        200
      )
      val textFrame = shape.getTextFrame
      textFrame.setText("Test Slide Content")

      val outputStream = new ByteArrayOutputStream()
      presentation.save(outputStream, SaveFormat.Pptx)
      outputStream.toByteArray
    } finally {
      if (presentation != null) presentation.dispose()
    }
  }

  /**
   * Helper method to create a simple test PPT presentation
   */
  private def createTestPpt(): Array[Byte] = {
    val presentation = new Presentation()
    try {
      // Add a slide with text
      val slide = presentation.getSlides.get_Item(0)
      val shape = slide.getShapes.addAutoShape(
        com.aspose.slides.ShapeType.Rectangle,
        100,
        100,
        400,
        200
      )
      val textFrame = shape.getTextFrame
      textFrame.setText("Test PPT Slide Content")

      val outputStream = new ByteArrayOutputStream()
      presentation.save(outputStream, SaveFormat.Ppt)
      outputStream.toByteArray
    } finally {
      if (presentation != null) presentation.dispose()
    }
  }

  "PptxToHtmlAsposeBridge" should "convert PPTX to HTML" in {
    val pptxBytes = createTestPptx()
    val input = FileContent[ApplicationVndOpenXmlFormatsPresentationmlPresentation.type](
      pptxBytes,
      ApplicationVndOpenXmlFormatsPresentationmlPresentation
    )

    // Convert PPTX to HTML
    val result = PptxToHtmlAsposeBridge.convert(input)

    // Verify the result
    result.mimeType shouldBe TextHtml

    // HTML should contain expected content
    val htmlContent = new String(result.data, "UTF-8")
    htmlContent should include("html")
    htmlContent.toLowerCase should include("test slide content")

    // Minimum size check to ensure it's valid HTML
    result.data.length should be > 100
  }

  it should "handle empty PPTX" in {
    val presentation = new Presentation()
    try {
      val outputStream = new ByteArrayOutputStream()
      presentation.save(outputStream, SaveFormat.Pptx)
      val emptyPptx = outputStream.toByteArray

      val input = FileContent[ApplicationVndOpenXmlFormatsPresentationmlPresentation.type](
        emptyPptx,
        ApplicationVndOpenXmlFormatsPresentationmlPresentation
      )

      // Should still produce valid HTML
      val result = PptxToHtmlAsposeBridge.convert(input)
      result.mimeType shouldBe TextHtml
      result.data.length should be > 0

      val htmlContent = new String(result.data, "UTF-8")
      htmlContent should include("html")
    } finally {
      if (presentation != null) presentation.dispose()
    }
  }

  "PptToHtmlAsposeBridge" should "convert PPT to HTML" in {
    val pptBytes = createTestPpt()
    val input = FileContent[ApplicationVndMsPowerpoint.type](
      pptBytes,
      ApplicationVndMsPowerpoint
    )

    // Convert PPT to HTML
    val result = PptToHtmlAsposeBridge.convert(input)

    // Verify the result
    result.mimeType shouldBe TextHtml

    // HTML should contain expected content
    val htmlContent = new String(result.data, "UTF-8")
    htmlContent should include("html")
    htmlContent.toLowerCase should include("test ppt slide content")

    // Minimum size check to ensure it's valid HTML
    result.data.length should be > 100
  }

  "Round-trip conversion" should "work for HTML -> PPTX -> HTML" in {
    // Original HTML
    val originalHtml = """<!DOCTYPE html>
                         |<html>
                         |<head><title>Round Trip Test</title></head>
                         |<body>
                         |  <h1>Test Heading</h1>
                         |  <p>Test paragraph for round-trip conversion.</p>
                         |</body>
                         |</html>""".stripMargin

    // Convert HTML -> PPTX
    val htmlInput = FileContent[TextHtml.type](originalHtml.getBytes("UTF-8"), TextHtml)
    val pptxResult = HtmlToPptxAsposeBridge.convert(htmlInput)

    // Verify PPTX was created
    pptxResult.mimeType shouldBe ApplicationVndOpenXmlFormatsPresentationmlPresentation

    // Convert PPTX -> HTML
    val htmlResult = PptxToHtmlAsposeBridge.convert(pptxResult)

    // Verify HTML was created
    htmlResult.mimeType shouldBe TextHtml

    val resultHtml = new String(htmlResult.data, "UTF-8")
    resultHtml should include("html")

    // Note: We can't expect exact HTML match due to Aspose's formatting,
    // but we should have valid HTML output
    resultHtml.length should be > 0
  }
}
