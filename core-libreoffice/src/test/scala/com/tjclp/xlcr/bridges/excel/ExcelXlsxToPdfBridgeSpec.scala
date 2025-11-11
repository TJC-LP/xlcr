package com.tjclp.xlcr
package bridges.excel

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll}

import config.LibreOfficeConfig
import models.FileContent
import test.DocumentGenerators
import types.MimeType._
import types.Priority

/**
 * Tests for ExcelXlsxToPdfLibreOfficeBridge.
 * Converts Excel 2007+ (.xlsx) files to PDF using LibreOffice.
 */
class ExcelXlsxToPdfBridgeSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    if (!LibreOfficeConfig.isAvailable()) {
      info(s"Skipping all tests: LibreOffice not available - ${LibreOfficeConfig.availabilityStatus()}")
      cancel()
    }
  }

  override def afterAll(): Unit = {
    // Don't shutdown LibreOffice here - it's shared across tests
    // and will be cleaned up by shutdown hook
  }

  "ExcelXlsxToPdfLibreOfficeBridge" should "have DEFAULT priority" in {
    ExcelXlsxToPdfLibreOfficeBridge.priority shouldBe Priority.DEFAULT
  }

  it should "convert minimal XLSX to PDF" in {
    val xlsxBytes = DocumentGenerators.createMinimalXlsx()
    val input = FileContent(xlsxBytes, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)

    val output = ExcelXlsxToPdfLibreOfficeBridge.convert(input, None)

    output.mimeType shouldBe ApplicationPdf
    output.data.length should be > 0

    // Verify PDF signature
    val header = new String(output.data.take(4))
    header shouldBe "%PDF"
  }

  it should "convert XLSX with formulas to PDF" in {
    val xlsxBytes = DocumentGenerators.createXlsxWithFormulas()
    val input = FileContent(xlsxBytes, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)

    val output = ExcelXlsxToPdfLibreOfficeBridge.convert(input, None)

    output.mimeType shouldBe ApplicationPdf
    output.data.length should be > 0

    // Verify PDF signature
    val header = new String(output.data.take(4))
    header shouldBe "%PDF"
  }

  it should "handle multiple sequential conversions" in {
    val xlsxBytes = DocumentGenerators.createMinimalXlsx()

    // Do 3 conversions in sequence
    (1 to 3).foreach { i =>
      val input = FileContent(xlsxBytes, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)
      val output = ExcelXlsxToPdfLibreOfficeBridge.convert(input, None)

      output.mimeType shouldBe ApplicationPdf
      output.data.length should be > 0
    }

    // LibreOffice should still be running
    assert(LibreOfficeConfig.isRunning())
  }
}
