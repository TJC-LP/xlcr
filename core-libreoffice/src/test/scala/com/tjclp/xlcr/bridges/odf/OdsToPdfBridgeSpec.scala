package com.tjclp.xlcr
package bridges.odf

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll}

import config.LibreOfficeConfig
import models.FileContent
import test.DocumentGenerators
import types.MimeType._
import types.Priority

/**
 * Tests for OdsToPdfLibreOfficeBridge.
 * Converts OpenDocument Spreadsheet (.ods) files to PDF using LibreOffice.
 *
 * ODS is LibreOffice's native format, so this should have excellent compatibility.
 */
class OdsToPdfBridgeSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    if (!LibreOfficeConfig.isAvailable()) {
      info(s"Skipping all tests: LibreOffice not available - ${LibreOfficeConfig.availabilityStatus()}")
      cancel()
    }
  }

  override def afterAll(): Unit = {
    // Don't shutdown - shared OfficeManager
  }

  "OdsToPdfLibreOfficeBridge" should "have DEFAULT priority" in {
    OdsToPdfLibreOfficeBridge.priority shouldBe Priority.DEFAULT
  }

  it should "convert minimal ODS to PDF" in {
    val odsBytes = DocumentGenerators.createMinimalOds()
    val input = FileContent(odsBytes, ApplicationVndOasisOpendocumentSpreadsheet)

    val output = OdsToPdfLibreOfficeBridge.convert(input, None)

    output.mimeType shouldBe ApplicationPdf
    output.data.length should be > 0

    // Verify PDF signature
    val header = new String(output.data.take(4))
    header shouldBe "%PDF"
  }

  it should "handle multiple sequential conversions" in {
    val odsBytes = DocumentGenerators.createMinimalOds()

    // Do 3 conversions in sequence
    (1 to 3).foreach { i =>
      val input = FileContent(odsBytes, ApplicationVndOasisOpendocumentSpreadsheet)
      val output = OdsToPdfLibreOfficeBridge.convert(input, None)

      output.mimeType shouldBe ApplicationPdf
      output.data.length should be > 0
    }

    assert(LibreOfficeConfig.isRunning())
  }
}
