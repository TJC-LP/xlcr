package com.tjclp.xlcr
package utils

import types.Priority

import org.slf4j.LoggerFactory

import java.util.ServiceLoader
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

/** A streamlined, thread-safe registry for discovering and managing prioritized components via SPI.
  *
  * @tparam K The key type used for lookup (e.g., MimeType or (MimeType, MimeType))
  * @tparam V The component type being registered (must extend Prioritized)
  * @tparam P The SPI Provider interface type
  * @tparam I The component Info object type returned by the provider
  */
trait Registry[K, V <: Prioritized, P, I] {
  // Abstract members that concrete registries must implement
  /** The Class object for the SPI Provider interface. */
  protected def providerClass: Class[P]

  /** The ClassTag for the Value type V. */
  implicit protected def valueClassTag: ClassTag[_]

  /** Extracts Info objects from a Provider instance. */
  protected def extractProviderInfo(provider: P): Iterable[I]

  /** Extracts the key from an Info object. */
  protected def getKey(info: I): K

  /** Extracts the value from an Info object. */
  protected def getValue(info: I): V

  /** Optional function to check if one key is a subtype of another. */
  protected def keySubtypeCheck: Option[(K, K) => Boolean] = None

  /** Name for this registry, used for logging. */
  protected def registryName: String

  private val logger = LoggerFactory.getLogger(s"com.tjclp.xlcr.utils.${registryName}")

  /** The underlying thread-safe priority registry instance. */
  protected lazy val registry: PriorityRegistry[K, V] = {
    logger.info(s"Initializing $registryName using ServiceLoader...")
    val reg = new PriorityRegistry[K, V](keySubtypeCheck)
    
    // Load and register all components from ServiceLoader providers
    try {
      val loader = ServiceLoader.load(providerClass)
      loader.iterator().asScala.foreach { provider =>
        logger.info(s"Loading components from provider: ${provider.getClass.getName}")
        try {
          extractProviderInfo(provider).foreach { info =>
            val key = getKey(info)
            val value = getValue(info)
            logger.debug(s"Registering ${value.getClass.getSimpleName} for key $key with priority ${value.priority}")
            reg.register(key, value)
          }
        } catch {
          case e: Throwable =>
            logger.error(s"Failed to load components from provider ${provider.getClass.getName}: ${e.getMessage}", e)
        }
      }
    } catch {
      case e: Throwable =>
        logger.error(s"Failed to initialize registry: ${e.getMessage}", e)
    }
    
    logger.info(s"$registryName initialization complete. Registered ${reg.size} keys.")
    reg
  }

  /** Explicitly trigger the lazy initialization. */
  def init(): Unit = {
    registry.size // Force initialization
    () // Return Unit
  }

  /** Register a component with the given key. */
  def register[VT <: V](key: K, value: VT): Unit = {
    logger.info(s"Registering ${value.getClass.getSimpleName} for key $key with priority ${value.priority}")
    registry.register(key, value)
  }

  /** Core lookup methods - find the highest priority value for a key. */
  def get(key: K): Option[V] = registry.get(key)
  def getWithSubtypes(key: K): Option[V] = registry.getWithSubtypes(key)

  /** Get all values registered for a key, sorted by priority (highest first). */
  def getAll(key: K): List[V] = registry.getAll(key)
  def getAllWithSubtypes(key: K): List[V] = registry.getAllWithSubtypes(key)

  /** Basic registry information. */
  def contains(key: K): Boolean = registry.contains(key)
  def keys: Set[K] = registry.keys
  def size: Int = registry.size

  /** Diagnostic method to list all registered entries with their priorities. */
  def listEntries(): Seq[(K, String, Priority)] = {
    registry.entries
      .map { case (key, value) => (key, value.getClass.getSimpleName, value.priority) }
      .toSeq
      .sortBy(t => (t._1.toString, -t._3.value)) // Sort by key, then priority desc
  }
}
