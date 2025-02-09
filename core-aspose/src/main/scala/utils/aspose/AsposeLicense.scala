package com.tjclp.xlcr
package utils.aspose

import com.aspose.email.License as EmailLicense
import com.aspose.words.License as WordsLicense
import com.aspose.cells.License as CellsLicense
import com.aspose.slides.License as SlidesLicense
import org.slf4j.LoggerFactory
import java.io.{FileInputStream, InputStream}

/**
  * AsposeLicense is a utility for loading Aspose licenses for Words, Email, Cells, Slides, etc.
  *
  * By default, it tries to load "Aspose.Total.Java.lic" from the classpath. However, you can
  * also manually load from a user-provided file path using the methods below.
  */
object AsposeLicense {
  private val logger = LoggerFactory.getLogger(getClass)
  private var initialized: Boolean = false

  /**
    * Initialize by auto-loading "Aspose.Total.Java.lic" from the classpath, if found.
    * This is often used if the user hasn't specified any license path at runtime.
    * If a license was already loaded, it won't load again.
    */
  def initializeIfNeeded(): Unit = synchronized {
    if (!initialized) {
      try {
        val licFileName = "Aspose.Total.Java.lic"
        val maybeStream = Option(getClass.getResourceAsStream(s"/$licFileName"))
        if maybeStream.isDefined then
          loadLicenseForAllStream(maybeStream.get)
          logger.info("Aspose 'total' license loaded from classpath resource.")
        else
          logger.warn(s"No default license found on classpath. Running in evaluation mode.")

        initialized = true
      } catch {
        case ex: Exception =>
          logger.error("Failed to set Aspose license from classpath, running in evaluation mode.", ex)
      }
    }
  }

  /**
    * Load a single license file for all products, using a file path.
    */
  def loadLicenseForAll(licensePath: String): Unit = synchronized {
    try {
      val fis = new FileInputStream(licensePath)
      loadLicenseForAllStream(fis)
      fis.close()
      logger.info(s"Successfully loaded Aspose total license from: $licensePath")
      initialized = true
    } catch {
      case ex: Exception =>
        logger.error(s"Failed to load Aspose total license from path: $licensePath", ex)
    }
  }

  /**
    * Helper to set a single license for Words, Email, Cells, Slides from an InputStream.
    */
  private def loadLicenseForAllStream(stream: InputStream): Unit =
    try
      // We must reset the stream for each product if reading from the same source,
      // so it's easiest to read from the file multiple times. For a single InputStream,
      // we replicate its bytes in memory or re-open. Alternatively, we can store them in a byte array.
      // For demonstration, we'll do a quick in-memory approach:
      val allBytes = stream.readAllBytes()

      // Words
      val wLic = new WordsLicense()
      wLic.setLicense(new java.io.ByteArrayInputStream(allBytes))

      // Email
      val eLic = new EmailLicense()
      eLic.setLicense(new java.io.ByteArrayInputStream(allBytes))

      // Cells
      val cLic = new CellsLicense()
      cLic.setLicense(new java.io.ByteArrayInputStream(allBytes))

      // Slides
      val sLic = new SlidesLicense()
      sLic.setLicense(new java.io.ByteArrayInputStream(allBytes))

    catch
      case ex: Exception =>
        logger.error("Failed loading license for all Aspose products from stream.", ex)

  /**
    * Load only the Aspose.Words license from a file path
    */
  def loadWordsLicense(licensePath: String): Unit = synchronized {
    try {
      val fis = new FileInputStream(licensePath)
      val wLic = new WordsLicense()
      wLic.setLicense(fis)
      fis.close()
      logger.info(s"Aspose.Words license loaded from: $licensePath")
    } catch {
      case ex: Exception =>
        logger.error(s"Failed to load Aspose.Words license from $licensePath", ex)
    }
  }

  /**
    * Load only the Aspose.Cells license from a file path
    */
  def loadCellsLicense(licensePath: String): Unit = synchronized {
    try {
      val fis = new FileInputStream(licensePath)
      val cLic = new CellsLicense()
      cLic.setLicense(fis)
      fis.close()
      logger.info(s"Aspose.Cells license loaded from: $licensePath")
    } catch {
      case ex: Exception =>
        logger.error(s"Failed to load Aspose.Cells license from $licensePath", ex)
    }
  }

  /**
    * Load only the Aspose.Email license from a file path
    */
  def loadEmailLicense(licensePath: String): Unit = synchronized {
    try {
      val fis = new FileInputStream(licensePath)
      val eLic = new EmailLicense()
      eLic.setLicense(fis)
      fis.close()
      logger.info(s"Aspose.Email license loaded from: $licensePath")
    } catch {
      case ex: Exception =>
        logger.error(s"Failed to load Aspose.Email license from $licensePath", ex)
    }
  }

  /**
    * Load only the Aspose.Slides license from a file path
    */
  def loadSlidesLicense(licensePath: String): Unit = synchronized {
    try {
      val fis = new FileInputStream(licensePath)
      val sLic = new SlidesLicense()
      sLic.setLicense(fis)
      fis.close()
      logger.info(s"Aspose.Slides license loaded from: $licensePath")
    } catch {
      case ex: Exception =>
        logger.error(s"Failed to load Aspose.Slides license from $licensePath", ex)
    }
  }

}