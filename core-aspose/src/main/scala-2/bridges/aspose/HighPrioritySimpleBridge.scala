package com.tjclp.xlcr
package bridges.aspose

import bridges.SimpleBridge
import types.{MimeType, Priority}
import utils.Prioritized

/**
 * A convenience trait that combines SimpleBridge with HighPriorityBridge for Aspose bridges.
 * This simplifies the extension syntax by providing both SimpleBridge functionality
 * and the ASPOSE priority in a single trait.
 * 
 * @tparam I The input MimeType
 * @tparam O The output MimeType
 */
trait HighPrioritySimpleBridge[I <: MimeType, O <: MimeType] extends SimpleBridge[I, O] with Prioritized {
  
  /**
   * Set priority to ASPOSE for all implementing bridges
   */
  override def priority: Priority = Priority.ASPOSE
}