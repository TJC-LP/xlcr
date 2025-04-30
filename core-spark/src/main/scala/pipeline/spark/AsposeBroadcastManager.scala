package com.tjclp.xlcr
package pipeline.spark

import utils.aspose.AsposeLicense

import org.apache.spark.broadcast.Broadcast
import org.apache.spark.sql.SparkSession
import org.slf4j.LoggerFactory

import java.util.concurrent.atomic.AtomicBoolean
import scala.util.Try

// Registries for converters & splitters
import bridges.BridgeRegistry
import bridges.aspose.AsposeBridgeRegistry
import utils.aspose.AsposeSplitterRegistry

/**
 * Manages broadcasting of Aspose license bytes to Spark workers
 * and ensures license initialization on each worker JVM.
 * 
 * This class leverages the existing AsposeLicense implementation
 * and adds cluster-aware broadcasting for Spark environments.
 */
object AsposeBroadcastManager extends Serializable {
  
  private val logger = LoggerFactory.getLogger(getClass)
  
  // Prevents multiple broadcasts in the same driver
  private val driverInitialized = new AtomicBoolean(false)
  
  // Prevents multiple initializations in the same worker JVM
  @transient private lazy val workerInitialized = new AtomicBoolean(false)
  
  // Cache for the broadcast variable
  @transient private var licenseBroadcast: Option[Broadcast[Map[String, Array[Byte]]]] = None
  
  // License status cache for quick lineage access
  private var asposeLicenseStatus: Map[String, String] = Map.empty

  // Ensure core converters are registered only once per JVM
  private val coreRegistered = new AtomicBoolean(false)

  // Ensure Aspose converters/splitters are registered only once per JVM
  private val asposeRegistered = new AtomicBoolean(false)
  
  /**
   * Initializes license broadcasting on the driver
   * and ensures licenses are loaded on the driver.
   */
  def initBroadcast(spark: SparkSession): Unit = {
    if (driverInitialized.compareAndSet(false, true)) {
      logger.debug("Initializing Aspose license broadcast on driver")
      
      // First, initialize licenses on the driver using existing mechanism
      Try(AsposeLicense.initializeIfNeeded()).recover { case ex =>
        logger.warn(s"Aspose license initialization had issues: ${ex.getMessage}")
      }
      
      // Collect license bytes for broadcasting
      val licenseBytes = collectLicenseBytes()
      
      if (licenseBytes.nonEmpty) {
        logger.info(s"Broadcasting ${licenseBytes.size} Aspose license files to workers")
        licenseBroadcast = Some(spark.sparkContext.broadcast(licenseBytes))
      } else {
        logger.info("No Aspose licenses found to broadcast")
      }

      // Always compute license status (may still be disabled)
      updateLicenseStatus()

      // Ensure default (non-Aspose) converters are available on the driver
      registerCoreConverters()

      // Register Aspose converters & splitters if licenses are available
      registerAsposeComponentsIfEnabled()
    }
  }
  
  /**
   * Ensures licenses are initialized on the worker
   * Safe to call from any worker UDF
   */
  def ensureInitialized(): Unit = {
    // Thread-safe, once-per-worker initialization
    if (workerInitialized.compareAndSet(false, true)) {
      licenseBroadcast.foreach { broadcast =>
        logger.debug("Initializing Aspose licenses on worker")
        val licenseMap = broadcast.value

        Try {
          // Apply the licenses on this worker
          applyLicenses(licenseMap)
          updateLicenseStatus()
        }.recover { case ex =>
          logger.warn(s"Failed to initialize Aspose licenses on worker: ${ex.getMessage}")
          asposeLicenseStatus = Map("status" -> "error", "error" -> ex.getMessage)
        }
      }

      // Ensure core converters are available on the worker JVM
      registerCoreConverters()

      // Register Aspose converters & splitters if we have licenses
      registerAsposeComponentsIfEnabled()
    }
  }
  
  /**
   * Get current license status for lineage tracking
   */
  def getLicenseStatus: Map[String, String] = asposeLicenseStatus
  
  /**
   * Check if any Aspose licenses are available
   */
  def isEnabled: Boolean = asposeLicenseStatus.get("status") match {
    case Some("licensed") | Some("evaluation") => true
    case _ => false
  }
  
  /**
   * Collect license bytes from files and environment
   */
  private def collectLicenseBytes(): Map[String, Array[Byte]] = {
    val licenseMap = scala.collection.mutable.Map.empty[String, Array[Byte]]
    
    // Try to get license bytes for each product
    AsposeLicense.Product.values.foreach { product =>
      // Try from environment variables
      val envBytes = Try {
        Option(System.getenv(product.envVar))
          .filter(_.nonEmpty)
          .map(java.util.Base64.getDecoder.decode)
      }.toOption.flatten
      
      // Try from files
      val fileBytes = Try {
        val file = new java.io.File(System.getProperty("user.dir"), product.licFile)
        if (file.isFile && file.canRead) {
          Some(java.nio.file.Files.readAllBytes(file.toPath))
        } else {
          // Try from classpath
          Option(getClass.getResourceAsStream(s"/${product.licFile}"))
            .map(is => is.readAllBytes())
        }
      }.toOption.flatten
      
      // Add to the map
      envBytes.orElse(fileBytes).foreach { bytes =>
        licenseMap.put(product.name, bytes)
      }
    }
    
    // Also check for total license
    val totalEnvBytes = Try {
      Option(System.getenv("ASPOSE_TOTAL_LICENSE_B64"))
        .filter(_.nonEmpty)
        .map(java.util.Base64.getDecoder.decode)
    }.toOption.flatten
    
    val totalFileBytes = Try {
      val file = new java.io.File(System.getProperty("user.dir"), "Aspose.Java.Total.lic")
      if (file.isFile && file.canRead) {
        Some(java.nio.file.Files.readAllBytes(file.toPath))
      } else {
        Option(getClass.getResourceAsStream("/Aspose.Java.Total.lic"))
          .map(is => is.readAllBytes())
      }
    }.toOption.flatten
    
    totalEnvBytes.orElse(totalFileBytes).foreach { bytes =>
      licenseMap.put("Total", bytes)
    }
    
    licenseMap.toMap
  }
  
  /**
   * Apply the license bytes on the current JVM
   */
  private def applyLicenses(licenseMap: Map[String, Array[Byte]]): Unit = {
    // First check for Total license
    licenseMap.get("Total").foreach { bytes =>
      AsposeLicense.Product.values.foreach { product =>
        Try(product(bytes)).recover { case ex =>
          logger.warn(s"Failed to apply Total license to ${product.name}: ${ex.getMessage}")
        }
      }
    }
    
    // Apply individual licenses if we don't have Total
    if (!licenseMap.contains("Total")) {
      AsposeLicense.Product.values.foreach { product =>
        licenseMap.get(product.name).foreach { bytes =>
          Try(product(bytes)).recover { case ex =>
            logger.warn(s"Failed to apply ${product.name} license: ${ex.getMessage}")
          }
        }
      }
    }
  }
  
  /**
   * Update the license status cache
   */
  private def updateLicenseStatus(): Unit = {
    // Check which products are licensed
    val productStatusMap = AsposeLicense.Product.values.map { product =>
      val productName = s"aspose${product.name}"
      val isLicensed = checkProductLicense(product)
      productName -> (if (isLicensed) "licensed" else "evaluation")
    }.toMap
    
    // Overall status is licensed if any product is licensed
    val overallStatus = if (productStatusMap.values.exists(_ == "licensed")) "licensed" else "evaluation"
    asposeLicenseStatus = productStatusMap + ("status" -> overallStatus)
  }

  /* -------------------------------------------------------------- */
  /* Registration helpers                                           */
  /* -------------------------------------------------------------- */

  /** Register the built-in converters and bridges once per JVM. */
  private def registerCoreConverters(): Unit = {
    if (coreRegistered.compareAndSet(false, true)) {
      logger.debug("Registering core BridgeRegistry converters (once per JVM)")
      Try(BridgeRegistry.init()).failed.foreach { ex =>
        logger.warn("Failed to initialise BridgeRegistry", ex)
      }
    }
  }

  /** Register Aspose-powered bridges and splitters once per JVM when enabled. */
  private def registerAsposeComponentsIfEnabled(): Unit = {
    if (isEnabled && asposeRegistered.compareAndSet(false, true)) {
      logger.info("Aspose detected – registering Aspose bridges & splitters")
      Try(AsposeBridgeRegistry.registerAll()).failed.foreach { ex =>
        logger.warn("Failed to register Aspose bridges", ex)
      }
      Try(AsposeSplitterRegistry.registerAll()).failed.foreach { ex =>
        logger.warn("Failed to register Aspose splitters", ex)
      }
    }
  }
  
  /**
   * Check if a specific product has a license
   */
  private def checkProductLicense(product: AsposeLicense.Product): Boolean = {
    // Check environment variables and license files
    val hasEnvLicense = Option(System.getenv(product.envVar)).exists(_.nonEmpty)
    val hasTotalEnvLicense = Option(System.getenv("ASPOSE_TOTAL_LICENSE_B64")).exists(_.nonEmpty)
    
    val userDir = new java.io.File(System.getProperty("user.dir"))
    val licenseFile = new java.io.File(userDir, product.licFile)
    val totalLicenseFile = new java.io.File(userDir, "Aspose.Java.Total.lic")
    
    val hasFileInUserDir = licenseFile.isFile && licenseFile.canRead
    val hasTotalFileInUserDir = totalLicenseFile.isFile && totalLicenseFile.canRead
    
    // Check classpath resources
    val hasClasspathLicense = Option(getClass.getResourceAsStream(s"/${product.licFile}")).isDefined
    val hasTotalClasspathLicense = Option(getClass.getResourceAsStream("/Aspose.Java.Total.lic")).isDefined
    
    hasEnvLicense || hasFileInUserDir || hasClasspathLicense || 
    hasTotalEnvLicense || hasTotalFileInUserDir || hasTotalClasspathLicense
  }
}