package com.tjclp.xlcr
package pipeline.spark

import scala.collection.concurrent.TrieMap

import com.tjclp.xlcr.bridges.BridgeRegistry
import com.tjclp.xlcr.bridges.aspose.AsposeBridgeRegistry
import com.tjclp.xlcr.utils.aspose.AsposeSplitterRegistry

import org.slf4j.LoggerFactory

import scala.util.Try
import java.util.concurrent.atomic.AtomicBoolean

/** Central registry for Spark‑based [[SparkPipelineStep]]s.
  *
  * Besides keeping track of the steps themselves, this object also takes care
  * of boot‑strapping the converter (BridgeRegistry) and splitter
  * (DocumentSplitter) infrastructure that those steps rely on.  In particular
  * we:
  *
  *   1. Always call [[BridgeRegistry.init]] so that the core set of converters
  *      is available.
  *   2. Optionally register Aspose‑powered converters and splitters *afterwards*
  *      so they override the core defaults when desired.
  *
  * Aspose integration is enabled when either of the following is set to a
  * value that resolves to logical *true* ("true", "1", or "yes"):
  *
  *   • JVM system property  `xlcr.aspose.enabled`
  *   • Environment variable `XLCR_ASPOSE_ENABLED`
  */
object SparkPipelineRegistry {

  private val logger = LoggerFactory.getLogger(getClass)

  /** Mutable registry of pipeline steps. */
  private val steps = TrieMap.empty[String, SparkStep]

  /* -----------------------------------------------------------------------
   * One‑time initialisation logic (guarded by AtomicBoolean)
   * --------------------------------------------------------------------- */

  private val initDone = new AtomicBoolean(false)
  
  // Track Aspose license status for lineage reporting
  private var asposeLicenseStatus: Map[String, String] = Map.empty
  private var asposeEnabled: Boolean = false

  /**
   * Get the current Aspose license status info for lineage tracking
   * @return A map of product names to license status
   */
  def getAsposeLicenseStatus: Map[String, String] = asposeLicenseStatus
  
  /**
   * Check if Aspose integration is enabled
   * @return true if Aspose is enabled and registered
   */
  def isAsposeEnabled: Boolean = asposeEnabled

  private def initIfNeeded(): Unit = if (initDone.compareAndSet(false, true)) {
    // 1) Core converters ---------------------------------------------------
    logger.debug("[core‑spark] Initialising core BridgeRegistry …")
    Try(BridgeRegistry.init()).failed.foreach { e =>
      logger.error("Failed to initialise BridgeRegistry", e)
    }

    // 2) Optional Aspose integration --------------------------------------
    asposeEnabled = checkAsposeEnabled()

    if (asposeEnabled) {
      // Attempt to apply licenses first
      val licenseResult = Try(com.tjclp.xlcr.utils.aspose.AsposeLicense.initializeIfNeeded())
      
      // Register Aspose components
      logger.info(
        "[core‑spark] Aspose integration enabled – registering Aspose bridges and splitters"
      )

      Try(AsposeBridgeRegistry.registerAll()).failed.foreach { e =>
        logger.warn("Failed to register Aspose bridges", e)
      }

      Try(AsposeSplitterRegistry.registerAll()).failed.foreach { e =>
        logger.warn("Failed to register Aspose splitters", e)
      }

      // Log license status and prepare for lineage tracking
      licenseResult.failed.foreach { e =>
        logger.warn(
          "Aspose license could not be initialized (will run in evaluation mode). " +
          "Documents will have watermarks. Error: " + e.getMessage, 
          e
        )
        asposeLicenseStatus = Map(
          "status" -> "evaluation",
          "error" -> e.getMessage
        )
      }
      
      if (licenseResult.isSuccess) {
        // Track which specific Aspose products are licensed
        val productStatusMap = com.tjclp.xlcr.utils.aspose.AsposeLicense.Product.values.map { product =>
          val productName = s"aspose${product.name}"
          val hasLicense = checkProductLicense(product)
          productName -> (if (hasLicense) "licensed" else "evaluation")
        }.toMap
        
        asposeLicenseStatus = productStatusMap + ("status" -> "licensed")
      }
    } else {
      logger.debug(
        "[core‑spark] Aspose integration disabled – using core converters / splitters only. " +
        "To enable Aspose and avoid watermarks, set XLCR_ASPOSE_ENABLED=true and provide license " +
        "via environment variables or license files."
      )
      asposeLicenseStatus = Map("status" -> "disabled")
    }
  }
  
  /**
   * Check if an Aspose product license is available
   */
  private def checkProductLicense(product: com.tjclp.xlcr.utils.aspose.AsposeLicense.Product): Boolean = {
    // Check for environment variable license
    val hasEnvLicense = Option(System.getenv(product.envVar)).exists(_.nonEmpty)
    
    // Check for license file
    val userDir = new java.io.File(System.getProperty("user.dir"))
    val licenseFile = new java.io.File(userDir, product.licFile)
    val hasFileInUserDir = licenseFile.isFile && licenseFile.canRead
    
    // Check for classpath license
    val hasClasspathLicense = Option(getClass.getResourceAsStream(s"/${product.licFile}")).isDefined
    
    // Check for total license (covers all products)
    val hasTotalEnvLicense = Option(System.getenv("ASPOSE_TOTAL_LICENSE_B64")).exists(_.nonEmpty)
    val totalLicFile = new java.io.File(userDir, "Aspose.Java.Total.lic")
    val hasTotalFileInUserDir = totalLicFile.isFile && totalLicFile.canRead
    val hasTotalClasspathLicense = Option(getClass.getResourceAsStream("/Aspose.Java.Total.lic")).isDefined
    
    hasEnvLicense || hasFileInUserDir || hasClasspathLicense || 
    hasTotalEnvLicense || hasTotalFileInUserDir || hasTotalClasspathLicense
  }
  
  /**
   * Check if Aspose integration should be enabled
   */
  private def checkAsposeEnabled(): Boolean = {
    def truthy(s: String): Boolean =
      s != null && (s.equalsIgnoreCase("true") ||
        s.equalsIgnoreCase("yes") || s == "1")

    // Check explicit enable flags first
    val explicitlyEnabled = 
      truthy(sys.props.get("xlcr.aspose.enabled").orNull) ||
      truthy(sys.env.getOrElse("XLCR_ASPOSE_ENABLED", null))

    // If not explicitly enabled, check for license presence
    if (explicitlyEnabled) true else {
      // Check for Aspose license environment variables
      val envLicenseVars = Seq(
        "ASPOSE_TOTAL_LICENSE_B64",
        "ASPOSE_WORDS_LICENSE_B64",
        "ASPOSE_CELLS_LICENSE_B64",
        "ASPOSE_EMAIL_LICENSE_B64",
        "ASPOSE_SLIDES_LICENSE_B64",
        "ASPOSE_ZIP_LICENSE_B64"
      )
      
      val hasEnvLicense = envLicenseVars.exists { envVar =>
        Option(System.getenv(envVar)).exists(_.nonEmpty)
      }
      
      // Check for license files
      val licenseFiles = Seq(
        "Aspose.Java.Total.lic",
        "Aspose.Java.Words.lic", 
        "Aspose.Java.Cells.lic",
        "Aspose.Java.Email.lic",
        "Aspose.Java.Slides.lic",
        "Aspose.Java.Zip.lic"
      )
      
      val userDir = new java.io.File(System.getProperty("user.dir"))
      val hasLicenseFile = licenseFiles.exists { fileName =>
        val file = new java.io.File(userDir, fileName)
        file.isFile && file.canRead
      } || licenseFiles.exists { fileName =>
        Option(getClass.getResourceAsStream(s"/$fileName")).isDefined
      }
      
      hasEnvLicense || hasLicenseFile
    }
  
  }

  /* -----------------------------------------------------------------------
   * Public API
   * --------------------------------------------------------------------- */

  /** Register a pipeline step instance. */
  def register(step: SparkStep): Unit = {
    initIfNeeded()
    steps.update(step.name, step)
  }

  /** Fetch a step by name or throw if it doesn't exist. */
  def get(id: String): SparkStep = {
    initIfNeeded()
    steps.getOrElse(
      id,
      throw new NoSuchElementException(s"Spark step '$id' not found")
    )
  }

  /** List all registered step names. */
  def list: Seq[String] = {
    initIfNeeded()
    steps.keys.toSeq.sorted
  }
}
