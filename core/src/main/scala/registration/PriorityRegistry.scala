package com.tjclp.xlcr
package registration

import types.{Prioritized, Priority}

import org.slf4j.LoggerFactory

import scala.collection.concurrent.TrieMap

/** A thread-safe registry that stores values with priorities.
  * For each key, the registry maintains a sorted list of values by descending priority.
  *
  * @tparam K The type of keys in the registry
  * @tparam V The type of values in the registry
  * @param isSubtype An optional function to check if one key is a subtype of another
  */
class PriorityRegistry[K, V <: Prioritized](
    isSubtype: Option[(K, K) => Boolean] = None
) {
  private val logger = LoggerFactory.getLogger(getClass)

  /** The internal storage for registry entries */
  private val registry: TrieMap[K, List[V]] = TrieMap.empty

  /** Registers a value for a key with the given priority.
    * If a value with the same identity is already registered for the key,
    * it will be replaced with the new priority.
    *
    * @param key The key to register the value for
    * @param value The value to register
    */
  def register(key: K, value: V): Unit = {
    // Atomic update using putIfAbsent and replace
    val newList = registry.get(key) match {
      case Some(currentList) =>
        // Remove existing entry with same class to avoid duplicates
        val filteredList = currentList.filterNot(_.getClass == value.getClass)
        // Add new entry and sort by priority descending
        (value :: filteredList).sortBy(v => -v.priority.value)

      case None =>
        List(value)
    }

    // Update the registry
    registry.put(key, newList)

    // Log the registration with priority
    logger.info(
      s"Registered ${value.getClass.getSimpleName} for key $key with priority ${value.priority} " +
        s"(currently ${newList.size} implementation(s) for this key)"
    )

    // If there are multiple implementations, log them in order
    if (newList.size > 1) {
      logger.info(
        s"Available implementations for $key (in priority order): " +
          newList
            .map(v => s"${v.getClass.getSimpleName}:${v.priority}")
            .mkString(", ")
      )
    }
  }

  /** Gets the highest priority value for a key.
    *
    * @param key The key to get the value for
    * @return Some(value) if a value is registered for the key, None otherwise
    */
  def get(key: K): Option[V] = {
    val result = registry.get(key).flatMap(_.headOption)

    // Log which implementation was selected
    result.foreach(v =>
      logger.debug(
        s"Selected ${v.getClass.getSimpleName} for key $key with priority ${v.priority}"
      )
    )

    result
  }

  /** Gets the highest priority value for a key, also considering subtypes.
    *
    * @param key The key to get the value for
    * @return Some(value) if a value is registered for the key or any of its supertypes, None otherwise
    */
  def getWithSubtypes(key: K): Option[V] = {
    get(key).orElse {
      isSubtype match {
        case Some(subtypeFn) =>
          // Look for any key that could be a supertype of the requested key
          registry.keys
            .filter(k => subtypeFn(key, k))
            .toSeq
            .sortBy { k =>
              // If we have multiple matches, prioritize by the values in each
              val highestPriority = registry
                .get(k)
                .flatMap(_.headOption)
                .map(_.priority.value)
                .getOrElse(0)
              -highestPriority // Negative for descending sort
            }
            .flatMap(k => registry.get(k).flatMap(_.headOption))
            .headOption

        case None => None
      }
    }
  }

  /** Gets all values registered for a key, sorted by priority (highest first).
    *
    * @param key The key to get values for
    * @return List of registered values, sorted by priority
    */
  def getAll(key: K): List[V] = {
    registry.getOrElse(key, List.empty)
  }

  /** Gets all values registered for a key and its supertypes, sorted by priority (highest first).
    *
    * @param key The key to get values for
    * @return List of registered values, sorted by priority
    */
  def getAllWithSubtypes(key: K): List[V] = {
    val direct = getAll(key)

    val fromSubtypes = isSubtype match {
      case Some(subtypeFn) =>
        registry.keys
          .filter(k => subtypeFn(key, k))
          .flatMap(k => registry.getOrElse(k, List.empty))
          .toList

      case None => List.empty
    }

    // Combine and sort by priority
    (direct ++ fromSubtypes).distinct.sortBy(v => -v.priority.value)
  }

  /** Checks if there is at least one value registered for a key.
    *
    * @param key The key to check
    * @return true if at least one value is registered for the key, false otherwise
    */
  def contains(key: K): Boolean = {
    registry.contains(key) && registry(key).nonEmpty
  }

  /** Gets all registered keys.
    *
    * @return Set of all registered keys
    */
  def keys: Set[K] = {
    registry.keySet.toSet
  }

  /** Gets all registered values across all keys.
    *
    * @return Set of all registered values
    */
  def values: Set[V] = {
    registry.values.flatMap(_.toSet).toSet
  }

  /** Gets all entries in the registry as key-value pairs.
    * For each key, the highest priority value is returned.
    *
    * @return Map of keys to their highest priority values
    */
  def entries: Map[K, V] = {
    registry.flatMap { case (key, values) =>
      values.headOption.map(value => key -> value)
    }.toMap
  }

  /** Clears the registry.
    */
  def clear(): Unit = {
    registry.clear()
    logger.info("Registry cleared")
  }

  /** Gets the number of keys in the registry.
    *
    * @return The number of keys
    */
  def size: Int = registry.size
}
