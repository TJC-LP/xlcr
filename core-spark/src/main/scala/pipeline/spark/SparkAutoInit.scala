package com.tjclp.xlcr
package pipeline.spark

import org.apache.spark.sql.SparkSession
import org.slf4j.LoggerFactory

/**
 * Provides automatic initialization of Spark XLCR components.
 * Unlike CoreAutoInit and AsposeAutoInit, this doesn't run on class load
 * since it requires an active SparkSession.
 * 
 * Use this in Spark applications to ensure all components are properly initialized.
 */
object SparkAutoInit {
  private val logger = LoggerFactory.getLogger(getClass)
  private val initialized = new java.util.concurrent.atomic.AtomicBoolean(false)
  
  /**
   * Initialize Spark XLCR components if not already initialized.
   * This is thread-safe and only executes once per SparkSession.
   * 
   * This should be called at the start of any Spark application that uses XLCR.
   * 
   * @param spark The active SparkSession
   */
  def initializeIfNeeded(spark: SparkSession): Unit = {
    if (initialized.compareAndSet(false, true)) {
      logger.info("Initializing Spark XLCR components")
      
      // Initialize Core components (should be done already via static init)
      CoreAutoInit.initializeIfNeeded()
      
      // Initialize Aspose broadcast if available
      try {
        AsposeBroadcastManager.initBroadcast(spark)
        logger.info("Aspose broadcast initialized")
      } catch {
        case e: Throwable =>
          logger.warn(s"Aspose broadcast initialization failed: ${e.getMessage}")
      }
    }
  }
}