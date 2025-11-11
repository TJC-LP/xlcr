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
 * Tests for ExcelXlsmToPdfLibreOfficeBridge.
 * Converts Excel macro-enabled (.xlsm) files to PDF using LibreOffice.
 */
class ExcelXlsmToPdfBridgeSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    if (!LibreOfficeConfig.isAvailable()) {
      info(s"Skipping all tests: LibreOffice not available - ${LibreOfficeConfig.availabilityStatus()}")
      cancel()
    }
  }

  override def afterAll(): Unit = {
    // Don't shutdown LibreOffice here - shared across tests
  }

  "ExcelXlsmToPdfLibreOfficeBridge" should "have DEFAULT priority" in {
    ExcelXlsmToPdfLibreOfficeBridge.priority shouldBe Priority.DEFAULT
  }

  it should "convert XLSM-formatted data to PDF" in {
    // Use XLSX data as XLSM is structurally similar
    val xlsmBytes = DocumentGenerators.createMinimalXlsx()
    val input = FileContent(xlsmBytes, ApplicationVndMsExcelSheetMacroEnabled)

    val output = ExcelXlsmToPdfLibreOfficeBridge.convert(input, None)

    output.mimeType shouldBe ApplicationPdf
    output.data.length should be > 0

    // Verify PDF signature
    val header = new String(output.data.take(4))
    header shouldBe "%PDF"
  }

}

