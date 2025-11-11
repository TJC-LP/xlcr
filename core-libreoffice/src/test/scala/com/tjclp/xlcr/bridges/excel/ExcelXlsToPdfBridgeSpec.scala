package com.tjclp.xlcr
package bridges.excel

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll}

import config.LibreOfficeConfig
import types.MimeType._
import types.Priority

/**
 * Tests for ExcelXlsToPdfLibreOfficeBridge.
 * Converts legacy Excel (.xls) files to PDF using LibreOffice.
 *
 * Note: We don't have easy XLS generation, so this tests basic configuration only.
 */
class ExcelXlsToPdfBridgeSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    if (!LibreOfficeConfig.isAvailable()) {
      info(s"Skipping all tests: LibreOffice not available - ${LibreOfficeConfig.availabilityStatus()}")
      cancel()
    }
  }

  override def afterAll(): Unit = {
    // Don't shutdown LibreOffice here - shared across tests
  }

  "ExcelXlsToPdfLibreOfficeBridge" should "have DEFAULT priority" in {
    ExcelXlsToPdfLibreOfficeBridge.priority shouldBe Priority.DEFAULT
  }

  // Note: Full conversion tests would require legacy XLS document generation
  // or test files. Can be added later if needed.
}
