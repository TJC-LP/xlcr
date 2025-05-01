package com.tjclp.xlcr
package bridges.aspose

import bridges.Bridge
import models.Model
import types.{MimeType, Priority}
import utils.Prioritized

import scala.reflect.ClassTag

/**
 * Trait for Aspose bridges to directly set priority to ASPOSE.
 * This replaces the need for a PrioritizedBridge wrapper in BridgeRegistry.
 * 
 * @tparam M The internal model type
 * @tparam I The input MimeType
 * @tparam O The output MimeType
 */
trait HighPriorityBridge[M <: Model, I <: MimeType, O <: MimeType](using mTag: ClassTag[M], iTag: ClassTag[I], oTag: ClassTag[O]) 
  extends Bridge[M, I, O] with Prioritized {
  
  /**
   * Set priority to HIGH for all implementing bridges
   */
  override def priority: Priority = Priority.HIGH
}