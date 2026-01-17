package com.tjclp.xlcr.v2.registry

import scala.collection.concurrent.TrieMap

import org.slf4j.LoggerFactory

import com.tjclp.xlcr.v2.transform.{Conversion, DynamicSplitter, Splitter, Transform}
import com.tjclp.xlcr.v2.types.Mime

/**
 * Thread-safe registry for Transform instances.
 *
 * Transforms are indexed by (inputMime, outputMime) pairs and sorted by priority
 * (higher priority transforms are preferred). The registry supports:
 *
 * - Registration of typed transforms via given instances
 * - Lookup by exact MIME type pair
 * - Retrieval of all edges for path finding
 * - Priority-based selection when multiple transforms match
 */
object TransformRegistry:

  private val logger = LoggerFactory.getLogger(getClass)

  // (inputMime, outputMime) -> List of transforms sorted by priority (highest first)
  private val conversions: TrieMap[(String, String), List[Transform[Mime, Mime]]] = TrieMap.empty

  // inputMime -> List of splitters sorted by priority (highest first)
  private val splitters: TrieMap[String, List[Transform[Mime, Mime]]] = TrieMap.empty

  // inputMime -> List of dynamic splitters sorted by priority (highest first)
  private val dynamicSplitters: TrieMap[String, List[DynamicSplitter[Mime]]] = TrieMap.empty

  /**
   * Register a conversion transform.
   *
   * @param inputMime The input MIME type string
   * @param outputMime The output MIME type string
   * @param transform The transform to register
   */
  def registerConversion[I <: Mime, O <: Mime](
      inputMime: I,
      outputMime: O,
      transform: Transform[I, O]
  ): Unit =
    val key = (inputMime: String, outputMime: String)
    val erased = transform.asInstanceOf[Transform[Mime, Mime]]
    conversions.updateWith(key) {
      case Some(existing) =>
        val updated = (erased :: existing).sortBy(-_.priority)
        logger.debug(s"Updated conversion [$inputMime -> $outputMime] with priority ${transform.priority}, now ${updated.size} transforms")
        Some(updated)
      case None =>
        logger.info(s"Registered conversion [$inputMime -> $outputMime] with priority ${transform.priority}")
        Some(List(erased))
    }

  /**
   * Register a typed splitter.
   *
   * @param inputMime The input MIME type string
   * @param outputMime The output MIME type string (same for all fragments)
   * @param splitter The splitter to register
   */
  def registerSplitter[I <: Mime, O <: Mime](
      inputMime: I,
      outputMime: O,
      splitter: Splitter[I, O]
  ): Unit =
    // Register as a conversion (input -> output) for path finding
    registerConversion(inputMime, outputMime, splitter)

    // Also register in splitters map for direct splitter lookup
    val key = inputMime: String
    val erased = splitter.asInstanceOf[Transform[Mime, Mime]]
    splitters.updateWith(key) {
      case Some(existing) =>
        val updated = (erased :: existing).sortBy(-_.priority)
        logger.debug(s"Updated splitter for [$inputMime] with priority ${splitter.priority}, now ${updated.size} splitters")
        Some(updated)
      case None =>
        logger.info(s"Registered splitter for [$inputMime -> $outputMime] with priority ${splitter.priority}")
        Some(List(erased))
    }

  /**
   * Register a dynamic splitter (output MIME types determined at runtime).
   *
   * @param inputMime The input MIME type string
   * @param splitter The dynamic splitter to register
   */
  def registerDynamicSplitter[I <: Mime](
      inputMime: I,
      splitter: DynamicSplitter[I]
  ): Unit =
    val key = inputMime: String
    val erased = splitter.asInstanceOf[DynamicSplitter[Mime]]
    dynamicSplitters.updateWith(key) {
      case Some(existing) =>
        val updated = (erased :: existing).sortBy(-_.priority)
        logger.debug(s"Updated dynamic splitter for [$inputMime] with priority ${splitter.priority}, now ${updated.size} splitters")
        Some(updated)
      case None =>
        logger.info(s"Registered dynamic splitter for [$inputMime] with priority ${splitter.priority}")
        Some(List(erased))
    }

  /**
   * Find the highest-priority conversion for the given MIME type pair.
   */
  def findConversion(inputMime: Mime, outputMime: Mime): Option[Transform[Mime, Mime]] =
    conversions.get((inputMime: String, outputMime: String)).flatMap(_.headOption)

  /**
   * Find the highest-priority conversion and cast to specific types.
   */
  def findConversionTyped[I <: Mime, O <: Mime](inputMime: Mime, outputMime: Mime): Option[Transform[I, O]] =
    findConversion(inputMime, outputMime).map(_.asInstanceOf[Transform[I, O]])

  /**
   * Find all conversions for the given MIME type pair, sorted by priority.
   */
  def findAllConversions(inputMime: Mime, outputMime: Mime): List[Transform[Mime, Mime]] =
    conversions.getOrElse((inputMime: String, outputMime: String), Nil)

  /**
   * Find the highest-priority splitter for the given input MIME type.
   */
  def findSplitter(inputMime: Mime): Option[Transform[Mime, Mime]] =
    splitters.get(inputMime: String).flatMap(_.headOption)

  /**
   * Find the highest-priority splitter and cast to specific type.
   */
  def findSplitterTyped[I <: Mime](inputMime: Mime): Option[Splitter[I, Mime]] =
    findSplitter(inputMime).map(_.asInstanceOf[Splitter[I, Mime]])

  /**
   * Find the highest-priority dynamic splitter for the given input MIME type.
   */
  def findDynamicSplitter(inputMime: Mime): Option[DynamicSplitter[Mime]] =
    dynamicSplitters.get(inputMime: String).flatMap(_.headOption)

  /**
   * Find the highest-priority dynamic splitter and cast to specific type.
   */
  def findDynamicSplitterTyped[I <: Mime](inputMime: Mime): Option[DynamicSplitter[I]] =
    findDynamicSplitter(inputMime).map(_.asInstanceOf[DynamicSplitter[I]])

  /**
   * Get all registered conversion edges for path finding.
   *
   * @return Set of (inputMime, outputMime) pairs
   */
  def conversionEdges: Set[(Mime, Mime)] =
    conversions.keys.map { case (i, o) => (Mime(i), Mime(o)) }.toSet

  /**
   * Get all registered input MIME types that have conversions.
   */
  def registeredInputMimes: Set[Mime] =
    conversions.keys.map { case (i, _) => Mime(i) }.toSet

  /**
   * Get all registered output MIME types that have conversions.
   */
  def registeredOutputMimes: Set[Mime] =
    conversions.keys.map { case (_, o) => Mime(o) }.toSet

  /**
   * Check if a direct conversion exists between two MIME types.
   */
  def hasDirectConversion(inputMime: Mime, outputMime: Mime): Boolean =
    conversions.contains((inputMime: String, outputMime: String))

  /**
   * Get the number of registered conversions.
   */
  def conversionCount: Int = conversions.values.map(_.size).sum

  /**
   * Get the number of registered splitters.
   */
  def splitterCount: Int = splitters.values.map(_.size).sum

  /**
   * Get the number of registered dynamic splitters.
   */
  def dynamicSplitterCount: Int = dynamicSplitters.values.map(_.size).sum

  /**
   * Clear all registered transforms (mainly for testing).
   */
  def clear(): Unit =
    conversions.clear()
    splitters.clear()
    dynamicSplitters.clear()
    logger.info("TransformRegistry cleared")

  /**
   * Get diagnostic information about the registry.
   */
  def diagnostics: String =
    val sb = new StringBuilder
    sb.append(s"TransformRegistry Status:\n")
    sb.append(s"  Conversions: $conversionCount\n")
    sb.append(s"  Splitters: $splitterCount\n")
    sb.append(s"  Dynamic Splitters: $dynamicSplitterCount\n")
    sb.append(s"\nRegistered conversions:\n")
    conversions.toSeq.sortBy(_._1).foreach { case ((input, output), transforms) =>
      sb.append(s"  $input -> $output (${transforms.size} transforms, top priority: ${transforms.headOption.map(_.priority).getOrElse(0)})\n")
    }
    sb.toString()
