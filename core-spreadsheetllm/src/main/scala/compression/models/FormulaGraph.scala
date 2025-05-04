package com.tjclp.xlcr
package compression.models

import scala.collection.mutable

/** Represents a graph of formula dependencies
  *
  * @param relationships Map from cell coordinates to the cells it references
  */
case class FormulaGraph(
    relationships: Map[(Int, Int), Set[(Int, Int)]]
) {
  // Create the inverse mapping (cells referenced by other cells)
  lazy val inverseRelationships: Map[(Int, Int), Set[(Int, Int)]] = {
    val result = mutable.Map[(Int, Int), mutable.Set[(Int, Int)]]()

    for ((source, targets) <- relationships; target <- targets) {
      val sources = result.getOrElseUpdate(target, mutable.Set.empty)
      sources.add(source)
    }

    result.map { case (k, v) => k -> v.toSet }.toMap
  }

  /** Get all cells that are connected to the given cell through formulas in either direction */
  def getConnectedCells(cell: (Int, Int)): Set[(Int, Int)] = {
    val visited = mutable.Set[(Int, Int)]()
    val toVisit = mutable.Queue[(Int, Int)](cell)

    while (toVisit.nonEmpty) {
      val current = toVisit.dequeue()
      if (!visited.contains(current)) {
        visited.add(current)

        // Add cells referenced by current cell
        val targets = relationships.getOrElse(current, Set.empty)
        for (target <- targets if !visited.contains(target)) {
          toVisit.enqueue(target)
        }

        // Add cells that reference current cell
        val sources = inverseRelationships.getOrElse(current, Set.empty)
        for (source <- sources if !visited.contains(source)) {
          toVisit.enqueue(source)
        }
      }
    }

    visited.toSet - cell
  }

  /** Find all strongly connected components (clusters of cells that reference each other) */
  def findConnectedComponents(): List[Set[(Int, Int)]] = {
    val allCells = relationships.keySet ++ inverseRelationships.keySet
    val visited = mutable.Set[(Int, Int)]()
    val components = mutable.ListBuffer[Set[(Int, Int)]]()

    for (cell <- allCells if !visited.contains(cell)) {
      val component = getConnectedCells(cell) + cell
      components += component
      visited ++= component
    }

    components.toList
  }
}
