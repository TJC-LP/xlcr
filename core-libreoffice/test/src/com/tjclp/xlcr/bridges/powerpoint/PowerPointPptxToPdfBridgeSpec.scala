package com.tjclp.xlcr
package bridges.powerpoint

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }

import config.LibreOfficeConfig
import models.FileContent
import test.DocumentGenerators
import types.MimeType._
import types.Priority

/**
 * Tests for PowerPointPptxToPdfLibreOfficeBridge. Converts PowerPoint 2007+ (.pptx) files to PDF
 * using LibreOffice.
 */
class PowerPointPptxToPdfBridgeSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach
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

  "PowerPointPptxToPdfLibreOfficeBridge" should "have DEFAULT priority" in {
    PowerPointPptxToPdfLibreOfficeBridge.priority shouldBe Priority.DEFAULT
  }

  it should "convert minimal PPTX to PDF" in {
    val pptxBytes = DocumentGenerators.createMinimalPptx()
    val input     = FileContent(pptxBytes, ApplicationVndOpenXmlFormatsPresentationmlPresentation)

    val output = PowerPointPptxToPdfLibreOfficeBridge.convert(input, None)

    output.mimeType shouldBe ApplicationPdf
    output.data.length should be > 0

    // Verify PDF signature
    val header = new String(output.data.take(4))
    header shouldBe "%PDF"
  }

  it should "convert PPTX with multiple slides to PDF" in {
    val pptxBytes = DocumentGenerators.createPptxWithMultipleSlides()
    val input     = FileContent(pptxBytes, ApplicationVndOpenXmlFormatsPresentationmlPresentation)

    val output = PowerPointPptxToPdfLibreOfficeBridge.convert(input, None)

    output.mimeType shouldBe ApplicationPdf
    output.data.length should be > 0

    // Verify PDF signature
    val header = new String(output.data.take(4))
    header shouldBe "%PDF"
  }

  it should "handle multiple sequential conversions" in {
    val pptxBytes = DocumentGenerators.createMinimalPptx()

    // Do 3 conversions in sequence
    (1 to 3).foreach { i =>
      val input  = FileContent(pptxBytes, ApplicationVndOpenXmlFormatsPresentationmlPresentation)
      val output = PowerPointPptxToPdfLibreOfficeBridge.convert(input, None)

      output.mimeType shouldBe ApplicationPdf
      output.data.length should be > 0
    }

    assert(LibreOfficeConfig.isRunning())
  }
}
