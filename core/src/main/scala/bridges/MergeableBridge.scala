package com.tjclp.xlcr
package bridges

import models.{FileContent, Model}
import types.{Mergeable, MimeType}

/**
 * A MergeableBridge is a Bridge that can merge its model type,
 * ensuring type-safety for merge operations.
 */
trait MergeableBridge[M <: Model & Mergeable[M], I <: MimeType, O <: MimeType] extends Bridge[M, I, O]:
  /**
   * Merges source content into target content by parsing both to the model type,
   * performing the merge, then rendering back to the output type.
   */
  def merge(source: FileContent[I], target: FileContent[O]): FileContent[O] =
    val sourceModel = parseInput(source)
    val targetModel = parseOutput(target)
    render(targetModel.merge(sourceModel))

  /**
   * Tests if we can merge these two file contents
   */
  def canMerge(source: FileContent[I], target: FileContent[O]): Boolean = true