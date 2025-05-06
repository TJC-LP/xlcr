package com.tjclp.xlcr
package types

/**
 * Trait for objects that have a priority. Implementations can declare their priority level to
 * indicate preference when multiple implementations for the same functionality exist.
 */
trait Prioritized {

  /**
   * The priority of this implementation. Higher values indicate higher priority.
   */
  def priority: Priority
}
