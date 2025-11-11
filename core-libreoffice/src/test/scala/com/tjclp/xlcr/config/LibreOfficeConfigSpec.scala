package com.tjclp.xlcr
package config

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{ BeforeAndAfterEach, BeforeAndAfterAll }

import java.io.File

/**
 * Tests for LibreOfficeConfig availability detection and configuration.
 */
class LibreOfficeConfigSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  override def afterEach(): Unit = {
    // Don't shutdown between tests - OfficeManager is expensive to initialize
  }

  override def afterAll(): Unit = {
    // Don't shutdown here either - let shutdown hook handle it
    // Shutdown is tested explicitly in dedicated tests
  }

  "LibreOfficeConfig.detectLibreOfficeHome" should "detect LibreOffice from environment variable" in {
    // Only run if LIBREOFFICE_HOME is set
    Option(System.getenv("LIBREOFFICE_HOME")) match {
      case Some(home) if home.nonEmpty =>
        val detected = LibreOfficeConfig.detectLibreOfficeHome()

        detected shouldBe defined
        detected.get.exists() shouldBe true
        detected.get.isDirectory shouldBe true
        detected.get.getAbsolutePath should include(home)

      case _ =>
        info("Skipping: LIBREOFFICE_HOME not set")
    }
  }

  it should "detect LibreOffice from platform default" in {
    // Check if LibreOffice is installed in default location
    val osName = System.getProperty("os.name").toLowerCase
    val expectedPath = if (osName.contains("mac")) {
      "/Applications/LibreOffice.app/Contents"
    } else if (osName.contains("win")) {
      "C:\\Program Files\\LibreOffice"
    } else {
      "/usr/lib/libreoffice"
    }

    val defaultLocation = new File(expectedPath)

    if (defaultLocation.exists() && defaultLocation.isDirectory) {
      val detected = LibreOfficeConfig.detectLibreOfficeHome()

      detected shouldBe defined
      detected.get.getAbsolutePath shouldBe expectedPath
    } else {
      info(s"Skipping: LibreOffice not installed at default location: $expectedPath")
    }
  }

  it should "return None when LibreOffice not found" in {
    // This test assumes LibreOffice is NOT installed at the default location
    // AND LIBREOFFICE_HOME is not set

    val hasEnvVar = Option(System.getenv("LIBREOFFICE_HOME")).exists(_.nonEmpty)

    val osName = System.getProperty("os.name").toLowerCase
    val defaultPath = if (osName.contains("mac")) {
      "/Applications/LibreOffice.app/Contents"
    } else if (osName.contains("win")) {
      "C:\\Program Files\\LibreOffice"
    } else {
      "/usr/lib/libreoffice"
    }
    val hasDefaultInstall = new File(defaultPath).exists()

    if (!hasEnvVar && !hasDefaultInstall) {
      val detected = LibreOfficeConfig.detectLibreOfficeHome()
      detected shouldBe None
    } else {
      info("Skipping: LibreOffice is installed (good!)")
    }
  }

  "LibreOfficeConfig.isAvailable" should "return true when LibreOffice is installed" in {
    val detected = LibreOfficeConfig.detectLibreOfficeHome()
    val isAvailable = LibreOfficeConfig.isAvailable()

    isAvailable shouldBe detected.isDefined

    if (isAvailable) {
      info(s"LibreOffice detected at: ${detected.get.getAbsolutePath}")
    } else {
      info("LibreOffice not detected - install it to run conversion tests")
    }
  }

  it should "match detectLibreOfficeHome result" in {
    val detected = LibreOfficeConfig.detectLibreOfficeHome()
    val isAvailable = LibreOfficeConfig.isAvailable()

    isAvailable shouldBe detected.isDefined
  }

  "LibreOfficeConfig.availabilityStatus" should "provide human-readable status" in {
    val status = LibreOfficeConfig.availabilityStatus()

    status should not be empty

    if (LibreOfficeConfig.isAvailable()) {
      status should include("Available at")
      status should not include("Not found")
    } else {
      status should include("Not found")
    }
  }

  it should "include path when available" in {
    if (LibreOfficeConfig.isAvailable()) {
      val status = LibreOfficeConfig.availabilityStatus()
      val detected = LibreOfficeConfig.detectLibreOfficeHome().get

      status should include(detected.getAbsolutePath)
    } else {
      info("Skipping: LibreOffice not available")
    }
  }

  "LibreOfficeConfig.getOfficeManager" should "initialize when LibreOffice is available" in {
    // Only run if LibreOffice is available
    if (LibreOfficeConfig.isAvailable()) {
      val manager = LibreOfficeConfig.getOfficeManager()

      manager should not be null
      manager.isRunning shouldBe true

      // Clean up
      LibreOfficeConfig.shutdown()
    } else {
      info("Skipping: LibreOffice not available")
    }
  }

  it should "throw RuntimeException when LibreOffice not available" in {
    if (!LibreOfficeConfig.isAvailable()) {
      assertThrows[RuntimeException] {
        LibreOfficeConfig.getOfficeManager()
      }
    } else {
      info("Skipping: LibreOffice IS available (good!)")
    }
  }

  it should "return cached instance on subsequent calls" in {
    if (LibreOfficeConfig.isAvailable()) {
      val manager1 = LibreOfficeConfig.getOfficeManager()
      val manager2 = LibreOfficeConfig.getOfficeManager()

      manager1 should be theSameInstanceAs manager2

      LibreOfficeConfig.shutdown()
    } else {
      info("Skipping: LibreOffice not available")
    }
  }

  "LibreOfficeConfig.isRunning" should "return false before initialization" in {
    LibreOfficeConfig.isRunning() shouldBe false
  }

  it should "return true after initialization" in {
    if (LibreOfficeConfig.isAvailable()) {
      val manager = LibreOfficeConfig.getOfficeManager()

      LibreOfficeConfig.isRunning() shouldBe true

      LibreOfficeConfig.shutdown()
    } else {
      info("Skipping: LibreOffice not available")
    }
  }

  it should "return false after shutdown" in {
    if (LibreOfficeConfig.isAvailable()) {
      val manager = LibreOfficeConfig.getOfficeManager()
      LibreOfficeConfig.isRunning() shouldBe true

      LibreOfficeConfig.shutdown()

      LibreOfficeConfig.isRunning() shouldBe false
    } else {
      info("Skipping: LibreOffice not available")
    }
  }

  "LibreOfficeConfig.shutdown" should "stop the OfficeManager" in {
    if (LibreOfficeConfig.isAvailable()) {
      val manager = LibreOfficeConfig.getOfficeManager()
      manager.isRunning shouldBe true

      LibreOfficeConfig.shutdown()

      LibreOfficeConfig.isRunning() shouldBe false
    } else {
      info("Skipping: LibreOffice not available")
    }
  }

  it should "be idempotent" in {
    if (LibreOfficeConfig.isAvailable()) {
      val manager = LibreOfficeConfig.getOfficeManager()

      LibreOfficeConfig.shutdown()
      LibreOfficeConfig.shutdown() // Second call should not fail
      LibreOfficeConfig.shutdown() // Third call should not fail

      LibreOfficeConfig.isRunning() shouldBe false
    } else {
      info("Skipping: LibreOffice not available")
    }
  }
}
