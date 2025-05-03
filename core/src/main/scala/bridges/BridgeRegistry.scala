package com.tjclp.xlcr
package bridges

import models.Model
import spi.{BridgeInfo, BridgeProvider}
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
  private lazy val registry: PriorityRegistry[(MimeType, MimeType), Bridge[
    _ <: Model,
    _ <: MimeType,
    _ <: MimeType
  ]] = {
    logger.info("Initializing BridgeRegistry using ServiceLoader...")
    val reg = new PriorityRegistry[
      (MimeType, MimeType),
      Bridge[_ <: Model, _ <: MimeType, _ <: MimeType]
    ]()
    val loader = ServiceLoader.load(classOf[BridgeProvider])

    loader.iterator().asScala.foreach { provider =>
      logger
        .info(s"Loading bridges from provider: ${provider.getClass.getName}")
      try {
        provider.getBridges.foreach { info =>
          registerBridgeInfo(reg, info)
        }
      } catch {
        case e: Throwable =>
          logger.error(
            s"Failed to load bridges from provider ${provider.getClass.getName}: ${e.getMessage}",
            e
          )
      }
    }
    logger.info("BridgeRegistry initialization complete.")
    reg
  }

  // Helper to register a single BridgeInfo
  private def registerBridgeInfo[I <: MimeType, O <: MimeType](
      reg: PriorityRegistry[
        (MimeType, MimeType),
        Bridge[_ <: Model, _ <: MimeType, _ <: MimeType]
      ],
      info: BridgeInfo[I, O]
  ): Unit = {
    logger.debug(
      s"Registering ${info.bridge.getClass.getSimpleName} for ${info.inMime} -> ${info.outMime} with priority ${info.bridge.priority}"
    )
    reg.register((info.inMime, info.outMime), info.bridge)
  }

  /** Explicitly register a bridge dynamically.
    * Useful for bridges that depend on runtime configuration.
    */
  def register[I <: MimeType, O <: MimeType](
      inMime: I,
      outMime: O,
      bridge: Bridge[_ <: Model, I, O]
  ): Unit = {
    registerBridgeInfo(registry, BridgeInfo[I, O](inMime, outMime, bridge))
  }

  /** Explicitly trigger the lazy initialization. Useful in contexts where
    * automatic class loading might not occur early enough (like some test setups).
    */
  def init(): Unit = {
    // Accessing the lazy val triggers initialization
    registry.size
  }

  /** Find the appropriate bridge for converting between mime types.
    * Returns Some(bridge) if found, otherwise None.
    * If multiple bridges are registered, the one with the highest priority is returned.
    * If no exact match is found, tries to find a bridge with a wildcard input mime type.
    */
  def findBridge(
      inMime: MimeType,
      outMime: MimeType
  ): Option[Bridge[_, _, _]] = {
    registry.get((inMime, outMime)).orElse {
      // Try with wildcard input mime type if no exact match is found
      logger.debug(
        s"No exact bridge found for $inMime -> $outMime, trying wildcard match"
      )
      registry.get((MimeType.Wildcard, outMime))
    }
  }

  /** A convenience method for pattern matching that guarantees it's exhaustive for Scala 2
    * This addresses the warnings about non-exhaustive pattern matching in Scala 2
    */
  def findBridgeForMatching[I <: MimeType, O <: MimeType, A](
      inMime: I,
      outMime: O
  )(matched: Bridge[_, I, O] => A, unmatched: => A): A = {
    findBridge(inMime, outMime) match {
      case Some(bridge) => matched(bridge.asInstanceOf[Bridge[_, I, O]])
      case None         => unmatched
    }
  }

  /** Find all bridges registered for the given mime types, in priority order.
    * Includes both exact matches and wildcard matches.
    */
  def findAllBridges(
      inMime: MimeType,
      outMime: MimeType
  ): List[Bridge[_, _, _]] = {
    // Get exact matches
    val exactMatches = registry.getAll((inMime, outMime))

    // Get wildcard matches if any exist
    val wildcardMatches =
      if (inMime != MimeType.Wildcard)
        registry.getAll((MimeType.Wildcard, outMime))
      else List.empty

    // Combine and sort by priority
    (exactMatches ++ wildcardMatches).sortBy(b => -b.priority.value)
  }

  /** Check if we can merge between these mime types
    */
  def supportsMerging(input: MimeType, output: MimeType): Boolean = {
    findMergeableBridge(input, output).isDefined
  }

  /** Find a bridge that supports merging between these mime types
    * Checks both exact matches and wildcard matches.
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

  /** A convenience method for pattern matching that guarantees it's exhaustive for Scala 2
    * This addresses the warnings about non-exhaustive pattern matching in Scala 2
    */
  def findMergeableBridgeForMatching[I <: MimeType, O <: MimeType, A](
      input: I,
      output: O
  )(matched: MergeableBridge[_, I, O] => A, unmatched: => A): A = {
    findMergeableBridge(input, output) match {
      case Some(bridge) =>
        matched(bridge.asInstanceOf[MergeableBridge[_, I, O]])
      case None => unmatched
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
