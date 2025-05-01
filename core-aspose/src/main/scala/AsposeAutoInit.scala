package com.tjclp.xlcr

import bridges.aspose.AsposeBridgeRegistry
import utils.aspose.{AsposeLicense, AsposeSplitterRegistry}
import org.slf4j.LoggerFactory

/**
 * Provides automatic initialization of Aspose components.
 * This initialization happens when the class is loaded.
 * 
 * If Aspose licenses are detected, it will automatically register
 * Aspose bridges and splitters with the appropriate registries.
 */
object AsposeAutoInit {
  private val logger = LoggerFactory.getLogger(getClass)
  private val initialized = new java.util.concurrent.atomic.AtomicBoolean(false)
  
  /**
   * Initialize Aspose components if not already initialized.
   * This is thread-safe and only executes once per JVM.
   */
  def initializeIfNeeded(): Unit = {
    if (initialized.compareAndSet(false, true)) {
      logger.info("Checking for Aspose license availability")
      
      // First try to initialize Aspose licenses
      val asposeAvailable = try {
        AsposeLicense.initializeIfNeeded()
        true
      } catch {
        case e: Throwable =>
          logger.warn(s"Aspose license initialization failed: ${e.getMessage}")
          false
      }
      
      // Register Aspose components if licenses are available
      if (asposeAvailable) {
        logger.info("Aspose licenses detected - registering Aspose bridges and splitters")
        try {
          AsposeBridgeRegistry.registerAll()
          logger.info("Aspose bridges registered")
          
          AsposeSplitterRegistry.registerAll()
          logger.info("Aspose splitters registered")
        } catch {
          case e: Throwable =>
            logger.error(s"Failed to register Aspose components: ${e.getMessage}", e)
        }
      } else {
        logger.info("No Aspose licenses detected - skipping Aspose component registration")
      }
    }
  }
  
  // Auto-initialize when the class is loaded
  initializeIfNeeded()
}