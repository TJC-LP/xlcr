package com.tjclp.xlcr
package bridges.powerpoint

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll}

import config.LibreOfficeConfig
import types.MimeType._
import types.Priority

/**
 * Tests for PowerPointPptToPdfLibreOfficeBridge.
 * Converts legacy PowerPoint (.ppt) files to PDF using LibreOffice.
 *
 * Note: We don't have easy PPT generation, so this tests basic configuration only.
 */
class PowerPointPptToPdfBridgeSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    if (!LibreOfficeConfig.isAvailable()) {
      info(s"Skipping all tests: LibreOffice not available - ${LibreOfficeConfig.availabilityStatus()}")
      cancel()
    }
  }

  override def afterAll(): Unit = {
    // Don't shutdown - shared OfficeManager
  }

  "PowerPointPptToPdfLibreOfficeBridge" should "have DEFAULT priority" in {
    PowerPointPptToPdfLibreOfficeBridge.priority shouldBe Priority.DEFAULT
  }

  // Note: Full conversion tests would require legacy PPT document generation
  // or test files. Can be added later if needed.
}
