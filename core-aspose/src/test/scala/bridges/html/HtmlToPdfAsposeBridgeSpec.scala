package com.tjclp.xlcr
package bridges.html

import org.scalatest.BeforeAndAfterAll

import base.BridgeSpec
import models.FileContent
import types.MimeType.{ ApplicationPdf, TextHtml }
import utils.aspose.AsposeLicense

class HtmlToPdfAsposeBridgeSpec extends BridgeSpec with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    super.beforeAll()
    AsposeLicense.initializeIfNeeded()
  }

  "HtmlToPdfAsposeBridge" should "convert HTML to PDF" in {
    // Simple HTML content for testing
    val html = """<!DOCTYPE html>
                 |<html>
                 |<head>
                 |  <title>Test HTML Document</title>
                 |</head>
                 |<body>
                 |  <h1>Hello, World!</h1>
                 |  <p>This is a test HTML document for conversion to PDF.</p>
                 |</body>
                 |</html>""".stripMargin

    val input = FileContent[TextHtml.type](html.getBytes("UTF-8"), TextHtml)

    // Convert HTML to PDF
    val result = HtmlToPdfAsposeBridge.convert(input)

    // Verify the result
    result.mimeType shouldBe ApplicationPdf

    // PDF files should start with %PDF
    new String(result.data.take(4)) should be("%PDF")

    // Minimum size check to ensure it's a valid PDF
    result.data.length should be > 100
  }
}
