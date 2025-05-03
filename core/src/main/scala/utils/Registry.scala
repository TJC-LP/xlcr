package com.tjclp.xlcr
package utils

import types.Priority

import org.slf4j.LoggerFactory

import java.util.ServiceLoader
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

/** A generic, thread-safe registry for discovering and managing prioritized components via SPI.
  *
  * @tparam K The type of the key used for lookup (e.g., MimeType, (MimeType, MimeType))
  * @tparam V The type of the value being registered (must extend Prioritized)
  * @tparam P The type of the SPI Provider interface (e.g., BridgeProvider, SplitterProvider)
  * @tparam I The type of the Info object returned by the provider (e.g., BridgeInfo, SplitterInfo)
  * @tparam VC The class type of V, used for ClassTag (typically the same as V but without type parameters)
  */
trait Registry[K, V <: Prioritized, P, I, VC] {
  // Abstract members to be implemented by concrete registries
  /** The Class object for the SPI Provider interface. */
  protected def providerClass: Class[P]

  /** The ClassTag for the Value type V. Needed for some operations. */
  implicit protected def valueClassTag: ClassTag[VC]

  /** Extracts the iterable of Info objects from a Provider instance. */
  protected def extractProviderInfo(provider: P): Iterable[I]

  /** Extracts the key (K) from an Info object (I). */
  protected def getKey(info: I): K

  /** Extracts the value (V) from an Info object (I). */
  protected def getValue(info: I): V

  /** An optional function to check if one key is a subtype of another. */
  protected def keySubtypeCheck: Option[(K, K) => Boolean] = None

  /** A name for this registry, used for logging. */
  protected def registryName: String

  private val logger =
    LoggerFactory.getLogger(s"com.tjclp.xlcr.utils.${registryName}")

  /** The underlying thread-safe priority registry instance. */
  protected lazy val registry: PriorityRegistry[K, V] = {
    logger.info(s"Initializing $registryName using ServiceLoader...")
    val reg = new PriorityRegistry[K, V](keySubtypeCheck)
    val loader = ServiceLoader.load(providerClass)

    loader.iterator().asScala.foreach { provider =>
      logger
        .info(s"Loading components from provider: ${provider.getClass.getName}")
      try {
        extractProviderInfo(provider).foreach { info =>
          val key = getKey(info)
          val value = getValue(info)
          logger.debug(
            s"Registering ${value.getClass.getSimpleName} for key $key with priority ${value.priority}"
          )
          reg.register(key, value)
        }
      } catch {
        case e: Throwable =>
          logger.error(
            s"Failed to load components from provider ${provider.getClass.getName}: ${e.getMessage}",
            e
          )
      }
    }
    logger.info(
      s"$registryName initialization complete. Registered ${reg.size} keys."
    )
    reg
  }

  /** Explicitly trigger the lazy initialization. */
  def init(): Unit = {
    registry.size // Accessing the lazy val triggers initialization
    () // Return Unit
  }

  /** Explicitly add an entry to the registry.
    * This version takes a generic type parameter to ensure type safety.
    */
  def addEntry[VT <: V](key: K, value: VT): Unit = {
    logger.info(
      s"Explicitly registering ${value.getClass.getSimpleName} for key $key with priority ${value.priority}"
    )
    registry.register(key, value)
  }

  /** Get the highest priority value for a key. */
  def get(key: K): Option[V] = registry.get(key)

  /** Get all values registered for a key, sorted by priority (highest first). */
  def getAll(key: K): List[V] = registry.getAll(key)

  /** Get the highest priority value for a key, considering subtypes if configured. */
  def getWithSubtypes(key: K): Option[V] = registry.getWithSubtypes(key)

  /** Get all values registered for a key and its supertypes, sorted by priority. */
  def getAllWithSubtypes(key: K): List[V] = registry.getAllWithSubtypes(key)

  /** Check if a key exists in the registry. */
  def contains(key: K): Boolean = registry.contains(key)

  /** Get all registered keys. */
  def keys: Set[K] = registry.keys

  /** Get all registered values across all keys. */
  def values: Set[V] = registry.values

  /** Get all entries (key -> highest priority value) in the registry. */
  def entries: Map[K, V] = registry.entries

  /** Get the number of keys in the registry. */
  def size: Int = registry.size

  /** Clear the registry. */
  def clear(): Unit = registry.clear()

  /** Diagnostic method to list all registered entries with their priorities. */
  def listRegisteredEntries(): Seq[(K, String, Priority)] = {
    // Iterate through all keys and their highest priority value
    entries
      .map { case (key, value) =>
        (key, value.getClass.getSimpleName, value.priority)
      }
      .toSeq
      .sortBy(t =>
        (t._1.toString, -t._3.value)
      ) // Sort by key, then priority desc
  }

  /** Diagnostic method to list all implementations for a specific key. */
  def listForKey(key: K): Seq[(String, Priority)] = {
    getAll(key).map(v => (v.getClass.getSimpleName, v.priority))
  }
}
