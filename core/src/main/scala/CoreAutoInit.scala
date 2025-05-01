package com.tjclp.xlcr

import bridges.BridgeRegistry
import org.slf4j.LoggerFactory

/**
 * Provides automatic initialization of core XLCR components.
 * This initialization happens when the class is loaded.
 */
object CoreAutoInit {
  private val logger = LoggerFactory.getLogger(getClass)
  private val initialized = new java.util.concurrent.atomic.AtomicBoolean(false)
  
  /**
   * Initialize core XLCR components if not already initialized.
   * This is thread-safe and only executes once per JVM.
   */
  def initializeIfNeeded(): Unit = {
    if (initialized.compareAndSet(false, true)) {
      logger.info("Initializing core XLCR components")
      
      // Initialize core bridges
      try {
        BridgeRegistry.init()
        logger.info("Core BridgeRegistry initialized")
      } catch {
        case e: Throwable =>
          logger.error(s"Failed to initialize core BridgeRegistry: ${e.getMessage}")
      }
    }
  }
  
  // Auto-initialize when the class is loaded
  initializeIfNeeded()
}