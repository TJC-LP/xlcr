package com.tjclp.xlcr
package bridges

import models.Model
import types.{MimeType, Priority}

/**
 * Common Priority Bridge traits that can be used in both Scala 2 and 3.
 * These traits provide a convenient way to specify the priority for a bridge.
 */

/**
 * Trait for bridges with HIGH priority (commercial solutions like Aspose).
 */
trait HighPriorityTrait {
  /**
   * Set priority to HIGH for all implementing bridges
   */
  def priority: Priority = Priority.HIGH
}

/**
 * Trait for bridges with DEFAULT priority (core implementations).
 */
trait DefaultPriorityTrait {
  /**
   * Set priority to DEFAULT for all implementing bridges
   */
  def priority: Priority = Priority.DEFAULT
}

/**
 * Trait for bridges with LOW priority (fallback implementations).
 */
trait LowPriorityTrait {
  /**
   * Set priority to LOW for all implementing bridges
   */
  def priority: Priority = Priority.LOW
}