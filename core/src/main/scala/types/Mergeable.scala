package com.tjclp.xlcr
package types

/**
 * A generic trait for models that can merge with another instance of the same type.
 *
 * @tparam Self the concrete model type
 */
trait Mergeable[Self] {
  /**
   * Merge this instance with another instance of the same type.
   *
   * @param other another instance to merge
   * @return a new instance representing the merged result
   */
  def merge(other: Self): Self

  /**
   * By default, merges with the given strategy by delegating to `merge`.
   *
   * Override to implement strategy-based merging if desired.
   */
  def mergeWith(other: Self, strategy: MergeStrategy): Self = merge(other)

  /**
   * An optional trait for customizing merge behavior.
   */
  trait MergeStrategy
}