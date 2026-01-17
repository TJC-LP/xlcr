package com.tjclp.xlcr.v2.registry

import scala.collection.mutable

import org.slf4j.LoggerFactory

import com.tjclp.xlcr.v2.transform.{Transform, UnsupportedConversion}
import com.tjclp.xlcr.v2.types.Mime

/**
 * Runtime path finder for discovering transform chains between MIME types.
 *
 * Uses BFS (Breadth-First Search) to find the shortest path between two MIME types
 * in the transform graph. When multiple paths exist, prefers paths with:
 * 1. Fewer hops (BFS guarantees this)
 * 2. Higher priority transforms at each step
 *
 * This complements the compile-time CanTransform evidence by providing runtime
 * path discovery for cases where the types are not known at compile time.
 */
object PathFinder:

  private val logger = LoggerFactory.getLogger(getClass)

  /** Cache for discovered paths */
  private val pathCache: mutable.Map[(String, String), Option[Transform[Mime, Mime]]] =
    mutable.Map.empty

  /**
   * Find a transform path from one MIME type to another.
   *
   * Uses BFS to find the shortest path in terms of number of hops.
   * Results are cached for performance.
   *
   * @param from Source MIME type
   * @param to Target MIME type
   * @return Some(Transform) if a path exists, None otherwise
   */
  def findPath(from: Mime, to: Mime): Option[Transform[Mime, Mime]] =
    val cacheKey = (from: String, to: String)

    pathCache.getOrElseUpdate(cacheKey, {
      logger.debug(s"Finding path from $from to $to")

      // Identity transform for same types
      if from == to then
        Some(Transform.identity[Mime])
      // Direct conversion available
      else if TransformRegistry.hasDirectConversion(from, to) then
        TransformRegistry.findConversion(from, to)
      // BFS for multi-hop path
      else
        bfs(from, to)
    })

  /**
   * Get a transform path, failing with UnsupportedConversion if none exists.
   */
  def getPath(from: Mime, to: Mime): Either[UnsupportedConversion, Transform[Mime, Mime]] =
    findPath(from, to).toRight(UnsupportedConversion(from, to))

  /**
   * Find a transform path and cast to specific types.
   * Use this when you know the exact types at call site.
   */
  def findPathTyped[I <: Mime, O <: Mime](from: Mime, to: Mime): Option[Transform[I, O]] =
    findPath(from, to).map(_.asInstanceOf[Transform[I, O]])

  /**
   * Get a transform path with casting to specific types.
   * Use this when you know the exact types at call site.
   */
  def getPathTyped[I <: Mime, O <: Mime](from: Mime, to: Mime): Either[UnsupportedConversion, Transform[I, O]] =
    getPath(from, to).map(_.asInstanceOf[Transform[I, O]])

  /**
   * Check if a path exists between two MIME types.
   */
  def pathExists(from: Mime, to: Mime): Boolean =
    findPath(from, to).isDefined

  /**
   * Get all reachable MIME types from a given starting point.
   *
   * @param from Starting MIME type
   * @param maxHops Maximum number of transformation steps (default: 10)
   * @return Set of reachable MIME types with their minimum hop count
   */
  def reachableFrom(from: Mime, maxHops: Int = 10): Map[Mime, Int] =
    val visited = mutable.Map[String, Int]()
    val queue = mutable.Queue[(Mime, Int)]()

    queue.enqueue((from, 0))
    visited(from: String) = 0

    while queue.nonEmpty do
      val (current, hops) = queue.dequeue()
      if hops < maxHops then
        val edges = TransformRegistry.conversionEdges
        val neighbors = edges.filter(_._1 == current).map(_._2)
        for neighbor <- neighbors do
          if !visited.contains(neighbor: String) then
            visited(neighbor: String) = hops + 1
            queue.enqueue((neighbor, hops + 1))

    visited.map { case (k, v) => (Mime(k), v) }.toMap

  /**
   * Describe the path between two MIME types (for debugging/logging).
   *
   * @return Human-readable path description or "No path found"
   */
  def describePath(from: Mime, to: Mime): String =
    if from == to then
      s"$from (identity)"
    else
      findPathWithSteps(from, to) match
        case Some(steps) =>
          steps.map { case (f, t) => s"$f -> $t" }.mkString(" >>> ")
        case None =>
          s"No path found from $from to $to"

  /**
   * Clear the path cache (useful after registry changes).
   */
  def clearCache(): Unit =
    pathCache.clear()
    logger.debug("PathFinder cache cleared")

  // BFS implementation
  private def bfs(from: Mime, to: Mime): Option[Transform[Mime, Mime]] =
    val edges = TransformRegistry.conversionEdges
    val adjacency = edges.groupBy(_._1).view.mapValues(_.map(_._2).toList).toMap

    // Queue contains (currentNode, path from start)
    val queue = mutable.Queue[(Mime, List[Mime])]()
    val visited = mutable.Set[String]()

    queue.enqueue((from, List(from)))
    visited += (from: String)

    while queue.nonEmpty do
      val (current, path) = queue.dequeue()

      // Get neighbors from adjacency list
      val neighbors = adjacency.getOrElse(current, Nil)

      for neighbor <- neighbors do
        if neighbor == to then
          // Found the target! Build the composed transform
          val fullPath = path :+ to
          logger.debug(s"Found path: ${fullPath.mkString(" -> ")}")
          return buildTransform(fullPath)

        if !visited.contains(neighbor: String) then
          visited += (neighbor: String)
          queue.enqueue((neighbor, path :+ neighbor))

    // No path found
    logger.debug(s"No path found from $from to $to")
    None

  // Build a composed transform from a path
  private def buildTransform(path: List[Mime]): Option[Transform[Mime, Mime]] =
    if path.length < 2 then None
    else
      val transforms = path.sliding(2).map { segment =>
        val from = segment.head
        val to = segment.last
        TransformRegistry.findConversion(from, to).getOrElse(
          throw new IllegalStateException(s"Expected conversion from $from to $to")
        )
      }.toList

      Some(transforms.reduceLeft(_ >>> _))

  // Find path and return the steps (for describePath)
  private def findPathWithSteps(from: Mime, to: Mime): Option[List[(Mime, Mime)]] =
    if from == to then Some(Nil)
    else if TransformRegistry.hasDirectConversion(from, to) then
      Some(List((from, to)))
    else
      bfsWithSteps(from, to)

  private def bfsWithSteps(from: Mime, to: Mime): Option[List[(Mime, Mime)]] =
    val edges = TransformRegistry.conversionEdges
    val adjacency = edges.groupBy(_._1).view.mapValues(_.map(_._2).toList).toMap

    val queue = mutable.Queue[(Mime, List[Mime])]()
    val visited = mutable.Set[String]()

    queue.enqueue((from, List(from)))
    visited += (from: String)

    while queue.nonEmpty do
      val (current, path) = queue.dequeue()
      val neighbors = adjacency.getOrElse(current, Nil)

      for neighbor <- neighbors do
        if neighbor == to then
          val fullPath = path :+ to
          return Some(fullPath.sliding(2).map(s => (s.head, s.last)).toList)

        if !visited.contains(neighbor: String) then
          visited += (neighbor: String)
          queue.enqueue((neighbor, path :+ neighbor))

    None
