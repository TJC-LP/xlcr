package com.tjclp.xlcr
package bridges

// No need to import SimpleBridge since we're in the same package
// (avoids warning about hidden imports)
import types.{ MimeType, Prioritized, Priority }
import utils.aspose.AsposeLicense

/**
 * A convenience trait that combines SimpleBridge with HighPriorityBridge for Aspose bridges. This
 * simplifies the extension syntax by providing both SimpleBridge functionality and the HIGH
 * priority in a single trait.
 *
 * @tparam I
 *   The input MimeType
 * @tparam O
 *   The output MimeType
 */
trait HighPrioritySimpleBridge[I <: MimeType, O <: MimeType]
    extends SimpleBridge[I, O]
    with Prioritized {
  AsposeLicense.initializeIfNeeded()

  /**
   * Set priority to HIGH for all implementing bridges
   */
  override def priority: Priority = Priority.HIGH
}
