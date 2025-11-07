package com.tjclp.xlcr
package bridges.powerpoint

import org.scalatest.BeforeAndAfterAll

import base.BridgeSpec
import models.FileContent
import types.MimeType.{
  ApplicationVndMsPowerpoint,
  ApplicationVndOpenXmlFormatsPresentationmlPresentation,
  TextHtml
}
import utils.aspose.AsposeLicense

class HtmlToPowerPointAsposeBridgeSpec extends BridgeSpec with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    super.beforeAll()
    AsposeLicense.initializeIfNeeded()
  }

  "HtmlToPptxAsposeBridge" should "convert HTML to PPTX" in {
    // Simple HTML content for testing
    val html = """<!DOCTYPE html>
                 |<html>
                 |<head>
                 |  <title>Test Presentation</title>
                 |</head>
                 |<body>
                 |  <h1>Slide 1: Hello, World!</h1>
                 |  <p>This is a test HTML document for conversion to PowerPoint.</p>
                 |  <ul>
                 |    <li>Point 1</li>
                 |    <li>Point 2</li>
                 |    <li>Point 3</li>
                 |  </ul>
                 |</body>
                 |</html>""".stripMargin

    val input = FileContent[TextHtml.type](html.getBytes("UTF-8"), TextHtml)

    // Convert HTML to PPTX
    val result = HtmlToPptxAsposeBridge.convert(input)

    // Verify the result
    result.mimeType shouldBe ApplicationVndOpenXmlFormatsPresentationmlPresentation

    // PPTX files should start with PK (ZIP signature)
    new String(result.data.take(2)) should be("PK")

    // Minimum size check to ensure it's a valid PPTX
    result.data.length should be > 1000
  }

  it should "handle empty HTML" in {
    val html = """<!DOCTYPE html>
                 |<html>
                 |<head><title>Empty</title></head>
                 |<body></body>
                 |</html>""".stripMargin

    val input = FileContent[TextHtml.type](html.getBytes("UTF-8"), TextHtml)

    // Should still produce a valid PPTX, even if empty
    val result = HtmlToPptxAsposeBridge.convert(input)
    result.mimeType shouldBe ApplicationVndOpenXmlFormatsPresentationmlPresentation
    result.data.length should be > 0
  }

  "HtmlToPptAsposeBridge" should "convert HTML to PPT" in {
    // Simple HTML content for testing
    val html = """<!DOCTYPE html>
                 |<html>
                 |<head>
                 |  <title>Test Presentation</title>
                 |</head>
                 |<body>
                 |  <h1>Slide 1: Hello, World!</h1>
                 |  <p>This is a test HTML document for conversion to PowerPoint PPT format.</p>
                 |</body>
                 |</html>""".stripMargin

    val input = FileContent[TextHtml.type](html.getBytes("UTF-8"), TextHtml)

    // Convert HTML to PPT
    val result = HtmlToPptAsposeBridge.convert(input)

    // Verify the result
    result.mimeType shouldBe ApplicationVndMsPowerpoint

    // PPT files should start with specific header bytes
    // Old PowerPoint format has a specific OLE2 header
    result.data.length should be > 1000
  }

  it should "handle invalid HTML gracefully" in {
    val invalidHtml = "This is not valid HTML at all <div unclosed"

    val input = FileContent[TextHtml.type](invalidHtml.getBytes("UTF-8"), TextHtml)

    // Should attempt conversion and either succeed with best-effort or fail gracefully
    // Aspose.Slides is typically lenient with HTML parsing
    noException should be thrownBy {
      HtmlToPptxAsposeBridge.convert(input)
    }
  }
}
