package com.tjclp.xlcr
package bridges

import models.{FileContent, Model}
import types.{Mergeable, MimeType}

/**
 * A MergeableBridge is a Bridge that can merge its model type,
 * ensuring type-safety for merge operations.
 * 
 * Scala 2 version extends BaseMergeableBridge and handles implicits explicitly.
 */
trait MergeableBridge[M <: Model with Mergeable[M], I <: MimeType, O <: MimeType] 
  extends Bridge[M, I, O] with BaseMergeableBridge[M, I, O]