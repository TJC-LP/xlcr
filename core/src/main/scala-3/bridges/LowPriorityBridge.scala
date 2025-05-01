package com.tjclp.xlcr
package bridges

import models.Model
import types.{MimeType, Priority}
import utils.Prioritized

import scala.reflect.ClassTag

/**
 * Trait for bridges with LOW priority, typically used for fallback implementations.
 * This simplifies the bridge registration process by embedding the priority in the bridge type.
 * 
 * @tparam M The internal model type
 * @tparam I The input MimeType
 * @tparam O The output MimeType
 */
trait LowPriorityBridge[M <: Model, I <: MimeType, O <: MimeType](using mTag: ClassTag[M], iTag: ClassTag[I], oTag: ClassTag[O]) 
  extends Bridge[M, I, O] {
  
  /**
   * Set priority to LOW for all implementing bridges
   */
  override def priority: Priority = Priority.LOW
}