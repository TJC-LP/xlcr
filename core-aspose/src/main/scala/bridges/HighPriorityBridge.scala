package com.tjclp.xlcr
package bridges

import scala.annotation.nowarn

// No need to import Bridge since we're in the same package
// (avoids warning about hidden imports)
import models.Model
import types.{ MimeType, Prioritized, Priority }
import utils.aspose.AsposeLicense

/**
 * Trait for Aspose bridges to directly set priority to HIGH. This replaces the need for a
 * PrioritizedBridge wrapper in BridgeRegistry.
 *
 * @tparam M
 *   The internal model type
 * @tparam I
 *   The input MimeType
 * @tparam O
 *   The output MimeType
 */
@nowarn("cat=deprecation")
trait HighPriorityBridge[M <: Model, I <: MimeType, O <: MimeType]
    extends Bridge[M, I, O]
    with Prioritized {
  AsposeLicense.initializeIfNeeded()

  /**
   * Set priority to HIGH for all implementing bridges
   */
  override def priority: Priority = Priority.HIGH
}
