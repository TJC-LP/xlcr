package com.tjclp.xlcr
package bridges.word

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }

import config.LibreOfficeConfig
import models.FileContent
import test.DocumentGenerators
import types.MimeType._
import types.Priority

/**
 * Tests for WordDocxToPdfLibreOfficeBridge. Converts Word 2007+ (.docx) files to PDF using
 * LibreOffice.
 */
class WordDocxToPdfBridgeSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach
    with BeforeAndAfterAll {

  override def beforeAll(): Unit =
    if (!LibreOfficeConfig.isAvailable()) {
      info(
        s"Skipping all tests: LibreOffice not available - ${LibreOfficeConfig.availabilityStatus()}"
      )
      cancel()
    }

  override def afterAll(): Unit = {
    // Don't shutdown - shared OfficeManager
  }

  "WordDocxToPdfLibreOfficeBridge" should "have DEFAULT priority" in {
    WordDocxToPdfLibreOfficeBridge.priority shouldBe Priority.DEFAULT
  }

  it should "convert minimal DOCX to PDF" in {
    val docxBytes = DocumentGenerators.createMinimalDocx()
    val input     = FileContent(docxBytes, ApplicationVndOpenXmlFormatsWordprocessingmlDocument)

    val output = WordDocxToPdfLibreOfficeBridge.convert(input, None)

    output.mimeType shouldBe ApplicationPdf
    output.data.length should be > 0

    // Verify PDF signature
    val header = new String(output.data.take(4))
    header shouldBe "%PDF"
  }

  it should "convert DOCX with formatting to PDF" in {
    val docxBytes = DocumentGenerators.createDocxWithFormatting()
    val input     = FileContent(docxBytes, ApplicationVndOpenXmlFormatsWordprocessingmlDocument)

    val output = WordDocxToPdfLibreOfficeBridge.convert(input, None)

    output.mimeType shouldBe ApplicationPdf
    output.data.length should be > 0

    // Verify PDF signature
    val header = new String(output.data.take(4))
    header shouldBe "%PDF"
  }

  it should "handle multiple sequential conversions" in {
    val docxBytes = DocumentGenerators.createMinimalDocx()

    // Do 3 conversions in sequence
    (1 to 3).foreach { i =>
      val input  = FileContent(docxBytes, ApplicationVndOpenXmlFormatsWordprocessingmlDocument)
      val output = WordDocxToPdfLibreOfficeBridge.convert(input, None)

      output.mimeType shouldBe ApplicationPdf
      output.data.length should be > 0
    }

    assert(LibreOfficeConfig.isRunning())
  }
}
