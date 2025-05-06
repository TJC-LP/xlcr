package com.tjclp.xlcr
package bridges

import models.{ FileContent, Model }
import types.{ Mergeable, MimeType }

/**
 * A BaseMergeableBridge is a Bridge that can merge its model type, ensuring type-safety for merge
 * operations.
 *
 * This is a common implementation for both Scala 2 and 3.
 */
trait BaseMergeableBridge[M <: Model with Mergeable[M], I <: MimeType, O <: MimeType]
    extends BaseBridge[M, I, O] {

  /**
   * Merges source content into target content by parsing both to the model type, performing the
   * merge, then rendering back to the output type.
   *
   * @param source
   *   The source file content to merge
   * @param target
   *   The target file content to merge into
   * @param config
   *   Optional bridge-specific configuration
   * @return
   *   The merged file content
   */
  override def convertWithDiff(
    source: FileContent[I],
    target: FileContent[O],
    config: Option[BridgeConfig] = None
  ): FileContent[O] = {
    val sourceModel = parseInput(source)
    val targetModel = parseOutput(target)
    render(targetModel.merge(sourceModel))
  }

  /**
   * Tests if we can merge these two file contents
   */
  def canMerge(source: FileContent[I], target: FileContent[O]): Boolean = true
}
