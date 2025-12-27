package com.tjclp.xlcr
package config

import java.io.File
import java.util.concurrent.atomic.AtomicReference

import scala.util.{ Failure, Success, Try }

import org.jodconverter.core.office.OfficeManager
import org.jodconverter.local.LocalConverter
import org.jodconverter.local.office.LocalOfficeManager
import org.slf4j.LoggerFactory

/**
 * Thread-safe LibreOffice configuration and lifecycle manager.
 *
 * Manages the JODConverter OfficeManager instance for document conversions. The manager handles
 * LibreOffice process lifecycle, connection pooling, and graceful shutdown.
 *
 * Features:
 *   - Lazy initialization on first use
 *   - Configurable LibreOffice installation path
 *   - Process pooling for concurrent conversions
 *   - Automatic cleanup on JVM shutdown
 *   - Thread-safe singleton pattern
 */
object LibreOfficeConfig {

  private val logger     = LoggerFactory.getLogger(getClass)
  private val managerRef = new AtomicReference[Option[OfficeManager]](None)

  // Configuration properties
  private val LibreOfficeHomeEnvVar  = "LIBREOFFICE_HOME"
  private val DefaultLibreOfficeHome = "/Applications/LibreOffice.app/Contents"

  // Performance tuning
  private val MaxTasksPerProcess   = 200
  private val TaskExecutionTimeout = 120000L // 2 minutes
  private val TaskQueueTimeout     = 30000L  // 30 seconds

  /**
   * Check if LibreOffice is available without initializing the OfficeManager. This is useful for
   * pre-flight checks and backend discovery.
   *
   * @return
   *   true if LibreOffice installation can be found, false otherwise
   */
  def isAvailable(): Boolean =
    detectLibreOfficeHome().isDefined

  /**
   * Get detailed availability status with human-readable message.
   *
   * @return
   *   Status message indicating availability and location
   */
  def availabilityStatus(): String =
    detectLibreOfficeHome() match {
      case Some(home) =>
        if (home.exists() && home.isDirectory) {
          s"Available at ${home.getAbsolutePath}"
        } else {
          s"Configured but not found at ${home.getAbsolutePath}"
        }
      case None =>
        "Not found - set LIBREOFFICE_HOME or install in standard location"
    }

  /**
   * Detect LibreOffice home directory without throwing exceptions. Checks environment variable
   * first, then platform-specific defaults.
   *
   * @return
   *   Some(File) if LibreOffice found, None otherwise
   */
  def detectLibreOfficeHome(): Option[File] = {
    // Check environment variable first
    val homeFromEnv = Option(System.getenv(LibreOfficeHomeEnvVar))
      .filter(_.nonEmpty)
      .map(new File(_))
      .filter(f => f.exists() && f.isDirectory)

    if (homeFromEnv.isDefined) {
      return homeFromEnv
    }

    // Try platform-specific defaults
    val osName = System.getProperty("os.name").toLowerCase
    val defaultPath = if (osName.contains("mac")) {
      DefaultLibreOfficeHome
    } else if (osName.contains("win")) {
      "C:\\Program Files\\LibreOffice"
    } else {
      // Linux
      "/usr/lib/libreoffice"
    }

    val defaultDir = new File(defaultPath)
    if (defaultDir.exists() && defaultDir.isDirectory) {
      Some(defaultDir)
    } else {
      None
    }
  }

  /**
   * Gets or initializes the OfficeManager instance. Thread-safe lazy initialization with automatic
   * shutdown hook registration.
   *
   * @return
   *   The initialized OfficeManager
   * @throws RuntimeException
   *   if LibreOffice cannot be initialized
   */
  def getOfficeManager(): OfficeManager =
    managerRef.get() match {
      case Some(manager) => manager
      case None =>
        synchronized {
          managerRef.get() match {
            case Some(manager) => manager
            case None =>
              val manager = initializeOfficeManager()
              managerRef.set(Some(manager))
              registerShutdownHook(manager)
              manager
          }
        }
    }

  /**
   * Creates a LocalConverter instance for document conversions. This is the main API for converting
   * documents using LibreOffice.
   *
   * @return
   *   A configured LocalConverter instance
   */
  def createConverter(): LocalConverter =
    LocalConverter.builder()
      .officeManager(getOfficeManager())
      .build()

  /**
   * Initializes the LibreOffice OfficeManager. Configures process pooling, timeouts, and
   * LibreOffice installation path.
   *
   * @return
   *   The configured and started OfficeManager
   * @throws RuntimeException
   *   if initialization fails
   */
  private def initializeOfficeManager(): OfficeManager = {
    logger.info("Initializing LibreOffice OfficeManager...")

    val officeHome = getLibreOfficeHome()
    logger.info(s"Using LibreOffice installation at: $officeHome")

    Try {
      val builder = LocalOfficeManager.builder()
        .officeHome(officeHome)
        .maxTasksPerProcess(MaxTasksPerProcess)
        .taskExecutionTimeout(TaskExecutionTimeout)
        .taskQueueTimeout(TaskQueueTimeout)

      val manager = builder.build()
      manager.start()
      logger.info("LibreOffice OfficeManager initialized successfully")
      manager
    } match {
      case Success(manager) => manager
      case Failure(ex) =>
        logger.error("Failed to initialize LibreOffice OfficeManager", ex)
        throw new RuntimeException(
          s"Failed to initialize LibreOffice. Please ensure LibreOffice is installed at $officeHome " +
            s"or set the $LibreOfficeHomeEnvVar environment variable to the correct path.",
          ex
        )
    }
  }

  /**
   * Determines the LibreOffice installation directory. Checks environment variable first, then
   * falls back to platform-specific defaults. Throws exception if not found (used during actual
   * initialization).
   *
   * @return
   *   The LibreOffice installation directory
   * @throws RuntimeException
   *   if LibreOffice home cannot be found
   */
  private def getLibreOfficeHome(): File =
    detectLibreOfficeHome() match {
      case Some(home) =>
        logger.info(s"Using LibreOffice home: ${home.getAbsolutePath}")
        home
      case None =>
        val tried = s"Tried: LIBREOFFICE_HOME environment variable and platform default"
        logger.error(s"LibreOffice home not found. $tried")
        throw new RuntimeException(
          s"LibreOffice installation not found. $tried. " +
            s"Please install LibreOffice or set LIBREOFFICE_HOME environment variable."
        )
    }

  /**
   * Registers a JVM shutdown hook to gracefully stop the OfficeManager. This ensures LibreOffice
   * processes are properly terminated on application exit.
   *
   * @param manager
   *   The OfficeManager to stop on shutdown
   */
  private def registerShutdownHook(manager: OfficeManager): Unit =
    Runtime.getRuntime.addShutdownHook(new Thread(() =>
      Try {
        logger.info("Shutting down LibreOffice OfficeManager...")
        manager.stop()
        logger.info("LibreOffice OfficeManager stopped successfully")
      } match {
        case Failure(ex) => logger.error("Error stopping LibreOffice OfficeManager", ex)
        case _           => ()
      }
    ))

  /**
   * Manually stops the OfficeManager and clears the cached instance. Use this for testing or when
   * you need to reinitialize with different settings.
   *
   * Note: This should rarely be needed in production code.
   */
  def shutdown(): Unit = synchronized {
    managerRef.get().foreach { manager =>
      Try {
        logger.info("Manually shutting down LibreOffice OfficeManager...")
        manager.stop()
        logger.info("LibreOffice OfficeManager stopped successfully")
      } match {
        case Failure(ex) => logger.error("Error stopping LibreOffice OfficeManager", ex)
        case _           => ()
      }
      managerRef.set(None)
    }
  }

  /**
   * Checks if the OfficeManager is currently initialized and running.
   *
   * @return
   *   true if the OfficeManager is running, false otherwise
   */
  def isRunning(): Boolean =
    managerRef.get().exists(_.isRunning)
}
