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

  private def initIfNeeded(): Unit = if (initDone.compareAndSet(false, true)) {
    // 1) Core converters ---------------------------------------------------
    logger.debug("[core‑spark] Initialising core BridgeRegistry …")
    Try(BridgeRegistry.init()).failed.foreach { e =>
      logger.error("Failed to initialise BridgeRegistry", e)
    }

    // 2) Optional Aspose integration --------------------------------------
    val asposeEnabled = {
      def truthy(s: String): Boolean =
        s != null && (s.equalsIgnoreCase("true") ||
          s.equalsIgnoreCase("yes") || s == "1")

      truthy(sys.props.get("xlcr.aspose.enabled").orNull) ||
      truthy(sys.env.getOrElse("XLCR_ASPOSE_ENABLED", null))
    }

    if (asposeEnabled) {
      logger.info(
        "[core‑spark] Aspose integration enabled – registering Aspose bridges and splitters …"
      )

      Try(AsposeBridgeRegistry.registerAll()).failed.foreach { e =>
        logger.warn("Failed to register Aspose bridges", e)
      }

      Try(AsposeSplitterRegistry.registerAll()).failed.foreach { e =>
        logger.warn("Failed to register Aspose splitters", e)
      }

      // Best‑effort attempt to apply licenses so we avoid watermarks/eval‑mode.
      Try(com.tjclp.xlcr.utils.aspose.AsposeLicense.initializeIfNeeded()).failed
        .foreach { e =>
          logger.debug(
            "Aspose license could not be initialised (may run in evaluation mode)",
            e
          )
        }
    } else {
      logger.debug(
        "[core‑spark] Aspose integration disabled – using core converters / splitters only"
      )
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
