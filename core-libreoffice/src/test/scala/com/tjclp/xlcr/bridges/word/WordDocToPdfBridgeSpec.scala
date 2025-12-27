package com.tjclp.xlcr
package bridges.word

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }

import config.LibreOfficeConfig
import types.Priority

/**
 * Tests for WordDocToPdfLibreOfficeBridge. Converts legacy Word (.doc) files to PDF using
 * LibreOffice.
 *
 * Note: We don't have easy DOC generation, so this tests basic configuration only.
 */
class WordDocToPdfBridgeSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach
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

  "WordDocToPdfLibreOfficeBridge" should "have DEFAULT priority" in {
    WordDocToPdfLibreOfficeBridge.priority shouldBe Priority.DEFAULT
  }

  // Note: Full conversion tests would require legacy DOC document generation
  // or test files. Can be added later if needed.
}
