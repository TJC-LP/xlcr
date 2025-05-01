package com.tjclp.xlcr
package bridges.aspose

import bridges.Bridge
import models.Model
import types.{MimeType, Priority}
import utils.Prioritized

/** Trait for Aspose bridges to directly set priority to HIGH.
  * This replaces the need for a PrioritizedBridge wrapper in BridgeRegistry.
  *
  * @tparam M The internal model type
  * @tparam I The input MimeType
  * @tparam O The output MimeType
  */
trait HighPriorityBridge[M <: Model, I <: MimeType, O <: MimeType]
    extends Bridge[M, I, O]
    with Prioritized {

  /** Set priority to HIGH for all implementing bridges
    */
  override def priority: Priority = Priority.HIGH
}
