package com.tjclp.xlcr
package bridges

import com.tjclp.xlcr.spi.{BridgeInfo, BridgeProvider}
import types.{MimeType, Priority}
import utils.PriorityRegistry

import org.slf4j.LoggerFactory
import java.util.ServiceLoader
import scala.jdk.CollectionConverters._

/** BridgeRegistry manages a set of registered Bridges between mime types.
  * It uses ServiceLoader to discover BridgeProvider implementations at runtime.
  */
object BridgeRegistry {
  private val logger = LoggerFactory.getLogger(getClass)

  /** Thread‑safe registry from (inMime, outMime) -> Priority-ordered list of Bridges */
  private lazy val registry: PriorityRegistry[(MimeType, MimeType), Bridge[_, _, _]] = {
    logger.info("Initializing BridgeRegistry using ServiceLoader...")
    val reg = new PriorityRegistry[(MimeType, MimeType), Bridge[_, _, _]]()
    val loader = ServiceLoader.load(classOf[BridgeProvider])

    loader.iterator().asScala.foreach { provider =>
      logger.info(s"Loading bridges from provider: ${provider.getClass.getName}")
      try {
        provider.getBridges.foreach { info =>
          registerBridgeInfo(reg, info)
        }
      } catch {
        case e: Throwable =>
          logger.error(s"Failed to load bridges from provider ${provider.getClass.getName}: ${e.getMessage}", e)
      }
    }
    logger.info("BridgeRegistry initialization complete.")
    reg
  }

  // Helper to register a single BridgeInfo
  private def registerBridgeInfo(reg: PriorityRegistry[(MimeType, MimeType), Bridge[_, _, _]], info: BridgeInfo): Unit = {
    logger.debug(
      s"Registering ${info.bridge.getClass.getSimpleName} for ${info.inMime} -> ${info.outMime} with priority ${info.bridge.priority}"
    )
    reg.register((info.inMime, info.outMime), info.bridge)
  }

  /**
   * Explicitly register a bridge dynamically.
   * Useful for bridges that depend on runtime configuration.
   */
  def register(inMime: MimeType, outMime: MimeType, bridge: Bridge[_, _, _]): Unit = {
    registerBridgeInfo(registry, BridgeInfo(inMime, outMime, bridge))
  }

  /**
   * Explicitly trigger the lazy initialization. Useful in contexts where
   * automatic class loading might not occur early enough (like some test setups).
   */
  def init(): Unit = {
    // Accessing the lazy val triggers initialization
    registry.size
  }

  /** Find the appropriate bridge for converting between mime types.
    * Returns Some(bridge) if found, otherwise None.
    * If multiple bridges are registered, the one with the highest priority is returned.
    */
  def findBridge(
      inMime: MimeType,
      outMime: MimeType
  ): Option[Bridge[_, _, _]] = {
    registry.get((inMime, outMime))
  }

  /** Find all bridges registered for the given mime types, in priority order.
    */
  def findAllBridges(
      inMime: MimeType,
      outMime: MimeType
  ): List[Bridge[_, _, _]] = {
    registry.getAll((inMime, outMime))
  }

  /** Check if we can merge between these mime types
    */
  def supportsMerging(input: MimeType, output: MimeType): Boolean = {
    findMergeableBridge(input, output).isDefined
  }

  /** Find a bridge that supports merging between these mime types
    */
  def findMergeableBridge(
      input: MimeType,
      output: MimeType
  ): Option[MergeableBridge[_, _, _]] = {
    findBridge(input, output) match {
      case Some(b: MergeableBridge[_, _, _]) => Some(b)
      case _                                 => None
    }
  }

  /** Diagnostic method to list all registered bridges with their priorities.
    * Useful for debugging and logging.
    */
  def listRegisteredBridges(): Seq[(MimeType, MimeType, String, Priority)] = {
    registry.entries
      .map { case ((inMime, outMime), bridge) =>
        (inMime, outMime, bridge.getClass.getSimpleName, bridge.priority)
      }
      .toSeq
      .sortBy(t => (t._1.toString, t._2.toString))
  }
}