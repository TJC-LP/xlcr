package com.tjclp.xlcr
package utils

import bridges.BridgeRegistry
import types.{MimeType, Priority}
import org.slf4j.LoggerFactory

/**
 * Utility for diagnosing registration and selection issues in the bridge and splitter registries.
 * This is especially useful for debugging which implementations are being selected and why.
 */
object RegistryDiagnostics {
  private val logger = LoggerFactory.getLogger(getClass)
  
  /**
   * Log all registered bridges with their priorities
   */
  def logAllBridges(): Unit = {
    val bridges = BridgeRegistry.listRegisteredBridges()
    
    logger.info(s"Total registered bridges: ${bridges.size}")
    logger.info("Registered bridges by priority:")
    
    bridges.groupBy(_._4).toSeq.sortBy(-_._1.value).foreach { case (priority, items) =>
      logger.info(s"  ${priority.toString}: ${items.size} bridges")
      items.foreach { case (inMime, outMime, implName, _) =>
        logger.info(f"    $inMime -> $outMime: $implName")
      }
    }
  }
  
  /**
   * Log all splitters registered for a specific MIME type
   */
  def logSplittersForMime(mime: MimeType): Unit = {
    val splitters = DocumentSplitter.allForMime(mime)
    
    logger.info(s"Splitters for $mime (${splitters.size} total):")
    splitters.foreach { splitter =>
      logger.info(f"  ${splitter.getClass.getSimpleName}: ${splitter.priority}")
    }
    
    // Log which one would be selected
    DocumentSplitter.forMime(mime) match {
      case Some(selected) => 
        logger.info(s"Selected splitter: ${selected.getClass.getSimpleName} (${selected.priority})")
      case None =>
        logger.info(s"No splitter would be selected for $mime")
    }
  }
  
  /**
   * Log all bridges registered for a specific MIME type conversion
   */
  def logBridgesForConversion(inMime: MimeType, outMime: MimeType): Unit = {
    val bridges = BridgeRegistry.findAllBridges(inMime, outMime)
    
    logger.info(s"Bridges for $inMime -> $outMime (${bridges.size} total):")
    bridges.zipWithIndex.foreach { case (bridge, index) =>
      // Since Bridge doesn't directly expose priority, we can only show the order
      logger.info(f"  ${index + 1}. ${bridge.getClass.getSimpleName}")
    }
    
    // Log which one would be selected
    BridgeRegistry.findBridge(inMime, outMime) match {
      case Some(selected) => 
        logger.info(s"Selected bridge: ${selected.getClass.getSimpleName}")
      case None =>
        logger.info(s"No bridge would be selected for $inMime -> $outMime")
    }
  }
  
  /**
   * Log the registration status for a specific implementation class
   */
  def logImplementationStatus(implName: String): Unit = {
    logger.info(s"Searching for implementations matching '$implName'")
    
    // Check bridge registry
    val bridges = BridgeRegistry.listRegisteredBridges()
    val matchingBridges = bridges.filter(_._3.contains(implName))
    
    if (matchingBridges.nonEmpty) {
      logger.info(s"Found ${matchingBridges.size} bridges matching '$implName':")
      matchingBridges.foreach { case (inMime, outMime, name, priority) =>
        logger.info(f"  $name: $inMime -> $outMime (priority: $priority)")
      }
    } else {
      logger.info(s"No bridges found matching '$implName'")
    }
  }
}