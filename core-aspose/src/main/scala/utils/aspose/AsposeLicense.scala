package com.tjclp.xlcr
package utils.aspose

import com.aspose.email.License as EmailLicense
import com.aspose.words.License
import org.slf4j.LoggerFactory

import java.io.InputStream

/**
  * AsposeLicense is a utility to initialize Aspose licenses for Words, Email, etc.
  * The license file is assumed to be on the classpath as "Aspose.Total.Java.lic".
  */
object AsposeLicense {
  private val logger = LoggerFactory.getLogger(getClass)
  private var initialized: Boolean = false

  /**
    * Try to set the Aspose license (for both Words and Email).
    * This should be called once at application startup.
    */
  def initializeIfNeeded(): Unit = synchronized {
    if (!initialized) {
      try {
        val licFileName = "Aspose.Total.Java.lic"
        val maybeStream = Option(getClass.getResourceAsStream(s"/$licFileName"))
        maybeStream match {
          case Some(stream) =>
            setWordsLicense(stream)
            // Since the same license file typically covers multiple Aspose products:
            stream.close() // re-open for the next license if needed
            val secondStream = getClass.getResourceAsStream(s"/$licFileName")
            setEmailLicense(secondStream)
            secondStream.close()
            logger.info("Aspose licenses applied successfully.")
          case None =>
            logger.warn(s"License file '$licFileName' not found on the classpath. Running in evaluation mode.")
        }
        initialized = true
      } catch {
        case ex: Exception =>
          logger.error("Failed to set Aspose license, running in evaluation mode.", ex)
      }
    }
  }

  private def setWordsLicense(stream: InputStream): Unit = {
    val wordsLicense = new License()
    wordsLicense.setLicense(stream)
  }

  private def setEmailLicense(stream: InputStream): Unit = {
    val emailLicense = new EmailLicense()
    emailLicense.setLicense(stream)
  }
}