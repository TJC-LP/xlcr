package com.tjclp.xlcr
package bridges

import models.Model
import types.{MimeType, Priority}
import utils.Prioritized

import scala.reflect.ClassTag

/**
 * Trait for core bridges to directly set priority to CORE.
 * This simplifies the bridge registration process by embedding the priority in the bridge type.
 * 
 * @tparam M The internal model type
 * @tparam I The input MimeType
 * @tparam O The output MimeType
 */
trait CorePriorityBridge[M <: Model, I <: MimeType, O <: MimeType] 
  extends Bridge[M, I, O] {
  
  /**
   * Set priority to CORE for all implementing bridges
   */
  override def priority: Priority = Priority.CORE
}