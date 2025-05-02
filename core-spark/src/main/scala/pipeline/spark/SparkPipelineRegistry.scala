package com.tjclp.xlcr
package pipeline.spark

import bridges.BridgeRegistry

import org.slf4j.LoggerFactory

import scala.collection.concurrent.TrieMap

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

  /** Register a pipeline step instance. */
  def register(step: SparkStep): Unit = {
    logger.info(s"Registering spark pipeline step ${step.name}")
    steps.update(step.name, step)
  }

  /** Fetch a step by name or throw if it doesn't exist. */
  def get(id: String): SparkStep = {
    steps.getOrElse(
      id,
      throw new NoSuchElementException(s"Spark step '$id' not found")
    )
  }

  /** List all registered step names. */
  def list: Seq[String] = {
    steps.keys.toSeq.sorted
  }
}
