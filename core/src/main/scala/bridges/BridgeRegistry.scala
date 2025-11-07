package com.tjclp.xlcr
package bridges

import scala.reflect.ClassTag

import models.Model
import registration.Registry
import spi.{ BridgeInfo, BridgeProvider }
import types.{ MimeType, Priority }

/**
 * BridgeRegistry manages Bridges between mime types, discovered via SPI. It extends the generic
 * Registry trait with strong typing.
 */
object BridgeRegistry
    extends Registry[
      (MimeType, MimeType),                             // K: Key is a tuple of MimeTypes
      Bridge[_ <: Model, _ <: MimeType, _ <: MimeType], // V: Value is a Bridge
      BridgeProvider,                                   // P: SPI Provider type
      BridgeInfo[_ <: MimeType, _ <: MimeType]          // I: Info object type
    ] {

  private val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  // Implement abstract members from Registry trait
  override protected def registryName: String = "BridgeRegistry"
  override protected def providerClass: Class[BridgeProvider] =
    classOf[BridgeProvider]
  // Provide ClassTag for Bridge type V
  override implicit protected def valueClassTag: ClassTag[Bridge[_, _, _]] =
    ClassTag(classOf[Bridge[_, _, _]])

  override protected def extractProviderInfo(
    provider: BridgeProvider
  ): Iterable[BridgeInfo[_ <: MimeType, _ <: MimeType]] =
    provider.getBridges

  override protected def getKey(
    info: BridgeInfo[_ <: MimeType, _ <: MimeType]
  ): (MimeType, MimeType) =
    (info.inMime, info.outMime)

  override protected def getValue(
    info: BridgeInfo[_ <: MimeType, _ <: MimeType]
  ): Bridge[_ <: Model, _ <: MimeType, _ <: MimeType] =
    info.bridge

  // Override keySubtypeCheck to provide MIME type subtype logic
  override protected def keySubtypeCheck
    : Option[((MimeType, MimeType), (MimeType, MimeType)) => Boolean] = Some {
    case (
          (requestInMime, requestOutMime),
          (registeredInMime, registeredOutMime)
        ) =>
      // Check if the registered input mime is a wildcard or matches the requested input
      (registeredInMime == MimeType.Wildcard ||
        (requestInMime.baseType == registeredInMime.baseType &&
          requestInMime.subType == registeredInMime.subType)) &&
      // And check if the output mime types match exactly
      requestOutMime.baseType == registeredOutMime.baseType &&
      requestOutMime.subType == registeredOutMime.subType
  }

  /**
   * Explicitly register a bridge dynamically with full type safety. Useful for bridges that depend
   * on runtime configuration.
   *
   * @tparam M
   *   The model type used by the bridge
   * @tparam I
   *   The input mime type
   * @tparam O
   *   The output mime type
   * @param inMime
   *   The input mime type
   * @param outMime
   *   The output mime type
   * @param bridge
   *   The bridge implementation
   */
  def register[M <: Model, I <: MimeType, O <: MimeType](
    inMime: I,
    outMime: O,
    bridge: Bridge[M, I, O]
  ): Unit =
    super.register((inMime, outMime), bridge)

  /**
   * Find the appropriate bridge for converting between mime types, preserving type information.
   * Returns Some(bridge) if found, otherwise None. If multiple bridges are registered, the one with
   * the highest priority is returned.
   *
   * @tparam I
   *   The input mime type
   * @tparam O
   *   The output mime type
   * @param inMime
   *   The input mime type
   * @param outMime
   *   The output mime type
   * @return
   *   An optional bridge that can convert from inMime to outMime
   */
  def findBridge[I <: MimeType, O <: MimeType](
    inMime: I,
    outMime: O
  ): Option[Bridge[_ <: Model, I, O]] =
    // First try with subtypes
    super
      .getWithSubtypes((inMime, outMime))
      .map(_.asInstanceOf[Bridge[_ <: Model, I, O]])
      .orElse {
        // If still not found, explicitly try with wildcard input mime type
        super
          .get((MimeType.Wildcard, outMime))
          .map(_.asInstanceOf[Bridge[_ <: Model, I, O]])
      }

  /**
   * Find all bridges registered for the given mime types, in priority order. Includes exact
   * matches, subtype matches, and wildcard matches.
   *
   * @tparam I
   *   The input mime type
   * @tparam O
   *   The output mime type
   * @param inMime
   *   The input mime type
   * @param outMime
   *   The output mime type
   * @return
   *   A list of bridges that can convert from inMime to outMime
   */
  def findAllBridges[I <: MimeType, O <: MimeType](
    inMime: I,
    outMime: O
  ): List[Bridge[_ <: Model, I, O]] = {
    // Get all matches including subtypes
    val allMatches = super.getAllWithSubtypes((inMime, outMime))

    // Get explicit wildcard matches if they weren't already included
    val wildcardMatches =
      if (
        inMime != MimeType.Wildcard &&
        !allMatches
          .exists(b => super.keys.contains((MimeType.Wildcard, outMime)))
      )
        super.getAll((MimeType.Wildcard, outMime))
      else List.empty

    // Combine, sort by priority, and cast to the required type
    (allMatches ++ wildcardMatches).distinct
      .sortBy(b => -b.priority.value)
      .map(_.asInstanceOf[Bridge[_ <: Model, I, O]])
  }

  /**
   * Find a bridge with optional backend filtering.
   *
   * @tparam I
   *   The input mime type
   * @tparam O
   *   The output mime type
   * @param inMime
   *   The input mime type
   * @param outMime
   *   The output mime type
   * @param backendPreference
   *   Optional backend name filter (aspose, libreoffice, core, tika)
   * @return
   *   An optional bridge that can convert from inMime to outMime, filtered by backend if specified
   */
  def findBridgeWithBackend[I <: MimeType, O <: MimeType](
    inMime: I,
    outMime: O,
    backendPreference: Option[String]
  ): Option[Bridge[_ <: Model, I, O]] = {
    backendPreference match {
      case None =>
        // No backend preference, use default behavior
        findBridge(inMime, outMime)
      case Some(backend) =>
        // Get all bridges and filter by backend pattern
        val allBridges = findAllBridges(inMime, outMime)
        val filtered = allBridges.filter(bridge => matchesBackend(bridge, backend))

        if (filtered.nonEmpty) {
          logger.info(s"Selected ${filtered.head.getClass.getSimpleName} based on backend preference: $backend")
          filtered.headOption
        } else {
          logger.warn(s"No bridge found for backend '$backend' for conversion $inMime -> $outMime. Available backends: ${allBridges.map(getBackendName).mkString(", ")}")
          None
        }
    }
  }

  /**
   * Determine which backend a bridge belongs to based on its class name and package.
   */
  private def getBackendName(bridge: Bridge[_, _, _]): String = {
    val className = bridge.getClass.getName.toLowerCase
    if (className.contains("aspose")) "aspose"
    else if (className.contains("libreoffice")) "libreoffice"
    else if (className.contains("tika")) "tika"
    else "core"
  }

  /**
   * Check if a bridge matches the specified backend preference.
   */
  private def matchesBackend(bridge: Bridge[_, _, _], backend: String): Boolean = {
    val backendName = getBackendName(bridge)
    backendName.equalsIgnoreCase(backend)
  }

  /**
   * Check if we can merge between these mime types
   *
   * @tparam I
   *   The input mime type
   * @tparam O
   *   The output mime type
   * @param input
   *   The input mime type
   * @param output
   *   The output mime type
   * @return
   *   True if a mergeable bridge exists for these mime types
   */
  def supportsMerging[I <: MimeType, O <: MimeType](
    input: I,
    output: O
  ): Boolean =
    // Use explicit type parameters to avoid ambiguity
    findMergeableBridge[I, O](input, output).isDefined

  /**
   * Find a bridge that supports merging between these mime types Checks exact matches, subtype
   * matches, and wildcard matches.
   *
   * @tparam I
   *   The input mime type
   * @tparam O
   *   The output mime type
   * @param input
   *   The input mime type
   * @param output
   *   The output mime type
   * @return
   *   An optional mergeable bridge that can convert from input to output
   */
  def findMergeableBridge[I <: MimeType, O <: MimeType](
    input: I,
    output: O
  ): Option[MergeableBridge[_ <: Model, I, O]] =
    // Use explicit type parameters to avoid ambiguity
    findBridge[I, O](input, output).collect {
      case b: MergeableBridge[_, _, _] =>
        b.asInstanceOf[MergeableBridge[_ <: Model, I, O]]
    }

  /** Diagnostic method to list registered bridges (uses trait method). */
  def listBridges(): Seq[(MimeType, MimeType, String, Priority)] =
    super.listEntries().map { case ((in, out), name, prio) =>
      (in, out, name, prio)
    }
}
