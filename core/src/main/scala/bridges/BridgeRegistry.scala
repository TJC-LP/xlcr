package com.tjclp.xlcr
package bridges

import models.Model
import spi.{BridgeInfo, BridgeProvider}
import types.{MimeType, Priority}
import utils.{Prioritized, Registry}

import scala.reflect.ClassTag

/** BridgeRegistry manages Bridges between mime types, discovered via SPI.
  * It extends the generic Registry trait with strong typing.
  */
object BridgeRegistry extends Registry[
  (MimeType, MimeType), // K: Key is a tuple of MimeTypes
  Bridge[_ <: Model, _ <: MimeType, _ <: MimeType], // V: Value is a Bridge
  BridgeProvider, // P: SPI Provider type
  BridgeInfo[_ <: MimeType, _ <: MimeType], // I: Info object type
  Bridge[_, _, _] // VC: The raw class type for ClassTag without type parameters
] {

  // Implement abstract members from Registry trait
  override protected def registryName: String = "BridgeRegistry"
  override protected def providerClass: Class[BridgeProvider] = classOf[BridgeProvider]
  // Provide ClassTag for Bridge type V
  override implicit protected def valueClassTag: ClassTag[Bridge[_, _, _]] =
    ClassTag(classOf[Bridge[_, _, _]])

  override protected def extractProviderInfo(provider: BridgeProvider): Iterable[BridgeInfo[_ <: MimeType, _ <: MimeType]] =
    provider.getBridges

  override protected def getKey(info: BridgeInfo[_ <: MimeType, _ <: MimeType]): (MimeType, MimeType) =
    (info.inMime, info.outMime)

  override protected def getValue(info: BridgeInfo[_ <: MimeType, _ <: MimeType]): Bridge[_ <: Model, _ <: MimeType, _ <: MimeType] =
    info.bridge

  // Override keySubtypeCheck to provide MIME type subtype logic
  override protected def keySubtypeCheck: Option[((MimeType, MimeType), (MimeType, MimeType)) => Boolean] = Some {
    case ((requestInMime, requestOutMime), (registeredInMime, registeredOutMime)) =>
      // Check if the registered input mime is a wildcard or matches the requested input
      (registeredInMime == MimeType.Wildcard ||
       (requestInMime.baseType == registeredInMime.baseType &&
        requestInMime.subType == registeredInMime.subType)) &&
      // And check if the output mime types match exactly
      requestOutMime.baseType == registeredOutMime.baseType &&
      requestOutMime.subType == registeredOutMime.subType
  }

  /** Explicitly register a bridge dynamically with full type safety.
    * Useful for bridges that depend on runtime configuration.
    * 
    * @tparam M The model type used by the bridge
    * @tparam I The input mime type
    * @tparam O The output mime type
    * @param inMime The input mime type
    * @param outMime The output mime type
    * @param bridge The bridge implementation
    */
  def register[M <: Model, I <: MimeType, O <: MimeType](
      inMime: I,
      outMime: O,
      bridge: Bridge[M, I, O]
  ): Unit = {
    addEntry((inMime, outMime), bridge)
  }

  /** Find the appropriate bridge for converting between mime types, preserving type information.
    * Returns Some(bridge) if found, otherwise None.
    * If multiple bridges are registered, the one with the highest priority is returned.
    * 
    * @tparam I The input mime type
    * @tparam O The output mime type
    * @param inMime The input mime type
    * @param outMime The output mime type
    * @return An optional bridge that can convert from inMime to outMime
    */
  def findBridge[I <: MimeType, O <: MimeType](
      inMime: I,
      outMime: O
  ): Option[Bridge[_ <: Model, I, O]] = {
    // First try with subtypes
    getWithSubtypes((inMime, outMime)).map(_.asInstanceOf[Bridge[_ <: Model, I, O]]).orElse {
      // If still not found, explicitly try with wildcard input mime type
      get((MimeType.Wildcard, outMime)).map(_.asInstanceOf[Bridge[_ <: Model, I, O]])
    }
  }

  /** A convenience method for pattern matching that guarantees it's exhaustive for Scala 2
    * This addresses the warnings about non-exhaustive pattern matching in Scala 2
    * 
    * @tparam I The input mime type
    * @tparam O The output mime type
    * @tparam A The result type of the matched or unmatched functions
    * @param inMime The input mime type
    * @param outMime The output mime type
    * @param matched Function to call if a bridge is found
    * @param unmatched Function to call if no bridge is found
    * @return The result of either matched or unmatched
    */
  def findBridgeForMatching[I <: MimeType, O <: MimeType, A](
      inMime: I,
      outMime: O
  )(matched: Bridge[_ <: Model, I, O] => A, unmatched: => A): A = {
    // Use explicit type parameters to avoid ambiguity
    findBridge[I, O](inMime, outMime) match {
      case Some(bridge) => matched(bridge)
      case None         => unmatched
    }
  }

  /** Find all bridges registered for the given mime types, in priority order.
    * Includes exact matches, subtype matches, and wildcard matches.
    * 
    * @tparam I The input mime type
    * @tparam O The output mime type
    * @param inMime The input mime type
    * @param outMime The output mime type
    * @return A list of bridges that can convert from inMime to outMime
    */
  def findAllBridges[I <: MimeType, O <: MimeType](
      inMime: I,
      outMime: O
  ): List[Bridge[_ <: Model, I, O]] = {
    // Get all matches including subtypes
    val allMatches = getAllWithSubtypes((inMime, outMime))

    // Get explicit wildcard matches if they weren't already included
    val wildcardMatches =
      if (inMime != MimeType.Wildcard && 
          !allMatches.exists(b => keys.contains((MimeType.Wildcard, outMime))))
        getAll((MimeType.Wildcard, outMime))
      else List.empty

    // Combine, sort by priority, and cast to the required type
    (allMatches ++ wildcardMatches).distinct
      .sortBy(b => -b.priority.value)
      .map(_.asInstanceOf[Bridge[_ <: Model, I, O]])
  }

  /** Check if we can merge between these mime types
    * 
    * @tparam I The input mime type
    * @tparam O The output mime type
    * @param input The input mime type
    * @param output The output mime type
    * @return True if a mergeable bridge exists for these mime types
    */
  def supportsMerging[I <: MimeType, O <: MimeType](
      input: I,
      output: O
  ): Boolean = {
    // Use explicit type parameters to avoid ambiguity
    findMergeableBridge[I, O](input, output).isDefined
  }

  /** Find a bridge that supports merging between these mime types
    * Checks exact matches, subtype matches, and wildcard matches.
    * 
    * @tparam I The input mime type
    * @tparam O The output mime type
    * @param input The input mime type
    * @param output The output mime type
    * @return An optional mergeable bridge that can convert from input to output
    */
  def findMergeableBridge[I <: MimeType, O <: MimeType](
      input: I,
      output: O
  ): Option[MergeableBridge[_ <: Model, I, O]] = {
    // Use explicit type parameters to avoid ambiguity
    findBridge[I, O](input, output) match {
      case Some(b: MergeableBridge[_, _, _]) => Some(b.asInstanceOf[MergeableBridge[_ <: Model, I, O]])
      case _                                 => None
    }
  }

  /** A convenience method for pattern matching that guarantees it's exhaustive for Scala 2
    * This addresses the warnings about non-exhaustive pattern matching in Scala 2
    * 
    * @tparam I The input mime type
    * @tparam O The output mime type
    * @tparam A The result type of the matched or unmatched functions
    * @param input The input mime type
    * @param output The output mime type
    * @param matched Function to call if a mergeable bridge is found
    * @param unmatched Function to call if no mergeable bridge is found
    * @return The result of either matched or unmatched
    */
  def findMergeableBridgeForMatching[I <: MimeType, O <: MimeType, A](
      input: I,
      output: O
  )(matched: MergeableBridge[_ <: Model, I, O] => A, unmatched: => A): A = {
    // Use explicit type parameters to avoid ambiguity
    findMergeableBridge[I, O](input, output) match {
      case Some(bridge) => matched(bridge)
      case None => unmatched
    }
  }

  /** Diagnostic method to list registered bridges (uses trait method). */
  def listRegisteredBridges(): Seq[(MimeType, MimeType, String, Priority)] = {
    listRegisteredEntries().map { case ((in, out), name, prio) => (in, out, name, prio) }
  }
  
  /** Legacy method for backward compatibility with code that doesn't use type parameters */
  def findBridgeLegacy(inMime: MimeType, outMime: MimeType): Option[Bridge[_, _, _]] = {
    findBridge[MimeType, MimeType](inMime, outMime).map(_.asInstanceOf[Bridge[_, _, _]])
  }
  
  /** Legacy method for backward compatibility with code that doesn't use type parameters */
  def findAllBridgesLegacy(inMime: MimeType, outMime: MimeType): List[Bridge[_, _, _]] = {
    findAllBridges[MimeType, MimeType](inMime, outMime).map(_.asInstanceOf[Bridge[_, _, _]])
  }
  
  /** Legacy method for backward compatibility with code that doesn't use type parameters */
  def findMergeableBridgeLegacy(input: MimeType, output: MimeType): Option[MergeableBridge[_, _, _]] = {
    findMergeableBridge[MimeType, MimeType](input, output).map(_.asInstanceOf[MergeableBridge[_, _, _]])
  }
  
  /** Legacy method for backward compatibility with code that doesn't use type parameters */
  def supportsMergingLegacy(input: MimeType, output: MimeType): Boolean = {
    supportsMerging[MimeType, MimeType](input, output)
  }
}
