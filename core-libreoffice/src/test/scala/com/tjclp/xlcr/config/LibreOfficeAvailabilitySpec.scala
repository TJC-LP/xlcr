package com.tjclp.xlcr
package config

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Minimal test for LibreOffice availability detection.
 * This is the critical feature - detecting whether LibreOffice is installed.
 */
class LibreOfficeAvailabilitySpec extends AnyFlatSpec with Matchers {

  "LibreOfficeConfig.isAvailable" should "return boolean without throwing exception" in {
    val isAvailable = LibreOfficeConfig.isAvailable()

    // Should return true or false, not throw
    isAvailable should (be(true) or be(false))

    info(s"LibreOffice available: $isAvailable")
  }

  "LibreOfficeConfig.availabilityStatus" should "return non-empty status" in {
    val status = LibreOfficeConfig.availabilityStatus()

    status should not be empty

    info(s"LibreOffice status: $status")
  }

  "LibreOfficeConfig.detectLibreOfficeHome" should "return Option[File]" in {
    val detected = LibreOfficeConfig.detectLibreOfficeHome()

    // Should be Some(file) or None, not throw
    detected match {
      case Some(home) =>
        info(s"LibreOffice detected at: ${home.getAbsolutePath}")
        home.exists() shouldBe true
        home.isDirectory shouldBe true

      case None =>
        info("LibreOffice not detected (install it to run conversion tests)")
    }
  }

  it should "match isAvailable() result" in {
    val detected = LibreOfficeConfig.detectLibreOfficeHome()
    val available = LibreOfficeConfig.isAvailable()

    available shouldBe detected.isDefined
  }
}
