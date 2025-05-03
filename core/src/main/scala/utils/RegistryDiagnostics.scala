package com.tjclp.xlcr
package utils

import bridges.BridgeRegistry
import splitters.{DocumentSplitter, SplitterRegistry}
import types.{MimeType, Priority}
import org.slf4j.LoggerFactory

/** Utility for diagnosing registration and selection issues in the bridge and splitter registries.
  * This is especially useful for debugging which implementations are being selected and why.
  */
object RegistryDiagnostics {
  private val logger = LoggerFactory.getLogger(getClass)

  /** Log all registered bridges with their priorities */
  def logAllBridges(): Unit = {
    val bridges = BridgeRegistry.listRegisteredBridges()

    logger.info(s"--- Bridge Registry (${bridges.size} entries) ---")
    logger.info("Registered bridges by priority:")

    bridges.groupBy(_._4).toSeq.sortBy(-_._1.value).foreach {
      case (priority, items) =>
        logger.info(s"  ${priority.toString}: ${items.size} bridges")
        items.foreach { case (inMime, outMime, implName, _) =>
          logger.info(f"    $inMime -> $outMime: $implName")
        }
    }
  }

  /** Log all registered splitters with their priorities */
  def logAllSplitters(): Unit = {
    val splitters = SplitterRegistry.listRegisteredSplitters()
    
    logger.info(s"--- Splitter Registry (${splitters.size} entries) ---")
    logger.info("Registered splitters by priority:")
    
    splitters.groupBy(_._3).toSeq.sortBy(-_._1.value).foreach {
      case (priority, items) =>
        logger.info(s"  ${priority.toString}: ${items.size} splitters")
        items.foreach { case (mime, implName, _) =>
          logger.info(f"    $mime: $implName")
        }
    }
  }

  /** Log all splitters registered for a specific MIME type */
  def logSplittersForMime(mime: MimeType): Unit = {
    val splitters = SplitterRegistry.findAllSplittersLegacy(mime)

    logger.info(s"Splitters for $mime (${splitters.size} total):")
    splitters.foreach { splitter =>
      logger.info(f"  ${splitter.getClass.getSimpleName}: ${splitter.priority}")
    }

    // Log which one would be selected
    SplitterRegistry.findSplitterLegacy(mime) match {
      case Some(selected) =>
        logger.info(
          s"Selected splitter: ${selected.getClass.getSimpleName} (${selected.priority})"
        )
      case None =>
        logger.info(s"No splitter would be selected for $mime")
    }
  }

  /** Log all bridges registered for a specific MIME type conversion */
  def logBridgesForConversion(inMime: MimeType, outMime: MimeType): Unit = {
    val bridges = BridgeRegistry.findAllBridgesLegacy(inMime, outMime)

    logger.info(s"Bridges for $inMime -> $outMime (${bridges.size} total):")
    bridges.zipWithIndex.foreach { case (bridge, index) =>
      logger.info(f"  ${index + 1}. ${bridge.getClass.getSimpleName} (${bridge.priority})")
    }

    // Log which one would be selected
    BridgeRegistry.findBridgeLegacy(inMime, outMime) match {
      case Some(selected) =>
        logger.info(s"Selected bridge: ${selected.getClass.getSimpleName} (${selected.priority})")
      case None =>
        logger.info(s"No bridge would be selected for $inMime -> $outMime")
    }
  }

  /** Log the registration status for a specific implementation class */
  def logImplementationStatus(implName: String): Unit = {
    logger.info(s"--- Implementation Status for '$implName' ---")
    
    // Check bridge registry
    val matchingBridges = BridgeRegistry.listRegisteredBridges().filter(_._3.contains(implName))
    if (matchingBridges.nonEmpty) {
      logger.info(s"Found ${matchingBridges.size} bridges matching '$implName':")
      matchingBridges.foreach { case (inMime, outMime, name, priority) =>
        logger.info(f"  $name: $inMime -> $outMime (priority: $priority)")
      }
    } else {
      logger.info(s"No bridges found matching '$implName'")
    }

    // Check splitter registry
    val matchingSplitters = SplitterRegistry.listRegisteredSplitters().filter(_._2.contains(implName))
    if (matchingSplitters.nonEmpty) {
      logger.info(s"Found ${matchingSplitters.size} splitters matching '$implName':")
      matchingSplitters.foreach { case (mime, name, priority) =>
        logger.info(f"  $name: $mime (priority: $priority)")
      }
    } else {
      logger.info(s"No splitters found matching '$implName'")
    }
  }
  
  /** Initialize both registries to ensure they're loaded */
  def initAllRegistries(): Unit = {
    logger.info("Initializing all registries...")
    BridgeRegistry.init()
    SplitterRegistry.init()
  }
}
