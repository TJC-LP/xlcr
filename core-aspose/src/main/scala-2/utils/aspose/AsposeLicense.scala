package com.tjclp.xlcr
package utils.aspose

import org.slf4j.LoggerFactory
import java.io.{File, FileInputStream, InputStream}

/**
  * AsposeLicense is a utility for loading Aspose licenses for Words, Email, Cells, Slides, etc.
  *
  * By default, it tries to load license files from:
  * 1. Current working directory (where the command is run)
  * 2. Classpath (if not found in working directory)
  * 
  * However, you can also manually load from a user-provided file path using the methods below.
  */
object AsposeLicense {
  private val logger = LoggerFactory.getLogger(getClass)
  private var initialized: Boolean = false
  
  // License file names
  private val totalLicFileName = "Aspose.Java.Total.lic"
  private val wordsLicFileName = "Aspose.Java.Words.lic"
  private val cellsLicFileName = "Aspose.Java.Cells.lic"
  private val emailLicFileName = "Aspose.Java.Email.lic"
  private val slidesLicFileName = "Aspose.Java.Slides.lic"
  
  /**
   * Find a license file by checking current working directory, then classpath
   * @param fileName The license file name to look for
   * @return Option containing the file or input stream if found
   */
  private def findLicenseFile(fileName: String): Option[Either[File, InputStream]] = {
    // First check current working directory
    val workingDir = System.getProperty("user.dir")
    val licFile = new File(s"$workingDir/$fileName")
    
    if (licFile.exists() && licFile.isFile && licFile.canRead) {
      Some(Left(licFile))
    } else {
      // Then check classpath
      val maybeStream = Option(getClass.getResourceAsStream(s"/$fileName"))
      maybeStream.map(stream => Right(stream))
    }
  }

  /**
    * Initialize by auto-loading license files from working directory or classpath.
    * This is used if the user hasn't specified any license path at runtime.
    * If a license was already loaded, it won't load again.
    */
  def initializeIfNeeded(): Unit = synchronized {
    if (!initialized) {
      try {
        // First try total license
        val totalLicense = findLicenseFile(totalLicFileName)
        if (totalLicense.isDefined) {
          totalLicense.get match {
            case Left(file) => 
              loadLicenseForAll(file.getAbsolutePath)
              logger.info(s"Aspose 'total' license loaded from working directory: ${file.getAbsolutePath}")
            case Right(stream) => 
              loadLicenseForAllStream(stream)
              logger.info("Aspose 'total' license loaded from classpath resource.")
          }
        } else {
          // Try individual licenses if total license not found
          var anyLoaded = false
          
          // Words
          findLicenseFile(wordsLicFileName).foreach {
            case Left(file) => 
              loadWordsLicense(file.getAbsolutePath)
              anyLoaded = true
            case Right(stream) => 
              loadWordsLicenseStream(stream)
              anyLoaded = true
          }
          
          // Cells
          findLicenseFile(cellsLicFileName).foreach {
            case Left(file) => 
              loadCellsLicense(file.getAbsolutePath)
              anyLoaded = true
            case Right(stream) => 
              loadCellsLicenseStream(stream)
              anyLoaded = true
          }
          
          // Email
          findLicenseFile(emailLicFileName).foreach {
            case Left(file) => 
              loadEmailLicense(file.getAbsolutePath)
              anyLoaded = true
            case Right(stream) => 
              loadEmailLicenseStream(stream)
              anyLoaded = true
          }
          
          // Slides
          findLicenseFile(slidesLicFileName).foreach {
            case Left(file) => 
              loadSlidesLicense(file.getAbsolutePath)
              anyLoaded = true
            case Right(stream) => 
              loadSlidesLicenseStream(stream)
              anyLoaded = true
          }
          
          if (!anyLoaded) {
            logger.warn("No license files found in working directory or classpath. Running in evaluation mode.")
          }
        }

        initialized = true
      } catch {
        case ex: Exception =>
          logger.error("Failed to set Aspose license, running in evaluation mode.", ex)
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
  private def loadLicenseForAllStream(stream: InputStream): Unit = {
    try {
      // We must reset the stream for each product if reading from the same source,
      // so it's easiest to read from the file multiple times. For a single InputStream,
      // we replicate its bytes in memory or re-open. Alternatively, we can store them in a byte array.
      // For demonstration, we'll do a quick in-memory approach:
      val allBytes = stream.readAllBytes()

      // Words
      val wLic = new com.aspose.words.License()
      wLic.setLicense(new java.io.ByteArrayInputStream(allBytes))

      // Email
      val eLic = new com.aspose.email.License()
      eLic.setLicense(new java.io.ByteArrayInputStream(allBytes))

      // Cells
      val cLic = new com.aspose.cells.License()
      cLic.setLicense(new java.io.ByteArrayInputStream(allBytes))

      // Slides
      val sLic = new com.aspose.slides.License()
      sLic.setLicense(new java.io.ByteArrayInputStream(allBytes))
    } catch {
      case ex: Exception =>
        logger.error("Failed loading license for all Aspose products from stream.", ex)
    }
  }

  /**
    * Load only the Aspose.Words license from a file path
    */
  def loadWordsLicense(licensePath: String): Unit = synchronized {
    try {
      val fis = new FileInputStream(licensePath)
      val wLic = new com.aspose.words.License()
      wLic.setLicense(fis)
      fis.close()
      logger.info(s"Aspose.Words license loaded from: $licensePath")
    } catch {
      case ex: Exception =>
        logger.error(s"Failed to load Aspose.Words license from $licensePath", ex)
    }
  }
  
  /**
    * Load only the Aspose.Words license from an input stream
    */
  def loadWordsLicenseStream(stream: InputStream): Unit = synchronized {
    try {
      val wLic = new com.aspose.words.License()
      wLic.setLicense(stream)
      logger.info("Aspose.Words license loaded from stream")
    } catch {
      case ex: Exception =>
        logger.error("Failed to load Aspose.Words license from stream", ex)
    }
  }

  /**
    * Load only the Aspose.Cells license from a file path
    */
  def loadCellsLicense(licensePath: String): Unit = synchronized {
    try {
      val fis = new FileInputStream(licensePath)
      val cLic = new com.aspose.cells.License()
      cLic.setLicense(fis)
      fis.close()
      logger.info(s"Aspose.Cells license loaded from: $licensePath")
    } catch {
      case ex: Exception =>
        logger.error(s"Failed to load Aspose.Cells license from $licensePath", ex)
    }
  }
  
  /**
    * Load only the Aspose.Cells license from an input stream
    */
  def loadCellsLicenseStream(stream: InputStream): Unit = synchronized {
    try {
      val cLic = new com.aspose.cells.License()
      cLic.setLicense(stream)
      logger.info("Aspose.Cells license loaded from stream")
    } catch {
      case ex: Exception =>
        logger.error("Failed to load Aspose.Cells license from stream", ex)
    }
  }

  /**
    * Load only the Aspose.Email license from a file path
    */
  def loadEmailLicense(licensePath: String): Unit = synchronized {
    try {
      val fis = new FileInputStream(licensePath)
      val eLic = new com.aspose.email.License()
      eLic.setLicense(fis)
      fis.close()
      logger.info(s"Aspose.Email license loaded from: $licensePath")
    } catch {
      case ex: Exception =>
        logger.error(s"Failed to load Aspose.Email license from $licensePath", ex)
    }
  }
  
  /**
    * Load only the Aspose.Email license from an input stream
    */
  def loadEmailLicenseStream(stream: InputStream): Unit = synchronized {
    try {
      val eLic = new com.aspose.email.License()
      eLic.setLicense(stream)
      logger.info("Aspose.Email license loaded from stream")
    } catch {
      case ex: Exception =>
        logger.error("Failed to load Aspose.Email license from stream", ex)
    }
  }

  /**
    * Load only the Aspose.Slides license from a file path
    */
  def loadSlidesLicense(licensePath: String): Unit = synchronized {
    try {
      val fis = new FileInputStream(licensePath)
      val sLic = new com.aspose.slides.License()
      sLic.setLicense(fis)
      fis.close()
      logger.info(s"Aspose.Slides license loaded from: $licensePath")
    } catch {
      case ex: Exception =>
        logger.error(s"Failed to load Aspose.Slides license from $licensePath", ex)
    }
  }
  
  /**
    * Load only the Aspose.Slides license from an input stream
    */
  def loadSlidesLicenseStream(stream: InputStream): Unit = synchronized {
    try {
      val sLic = new com.aspose.slides.License()
      sLic.setLicense(stream)
      logger.info("Aspose.Slides license loaded from stream")
    } catch {
      case ex: Exception =>
        logger.error("Failed to load Aspose.Slides license from stream", ex)
    }
  }

}