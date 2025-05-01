package com.tjclp.xlcr
package bridges

import models.{FileContent, Model}
import types.{Mergeable, MimeType}
import scala.reflect.ClassTag

/**
 * A MergeableBridge is a Bridge that can merge its model type,
 * ensuring type-safety for merge operations.
 * 
 * Scala 3 version extends BaseMergeableBridge and handles implicits with `using`.
 */
trait MergeableBridge[M <: Model & Mergeable[M], I <: MimeType, O <: MimeType]
  (using ClassTag[M], ClassTag[I], ClassTag[O])
  extends Bridge[M, I, O] with BaseMergeableBridge[M, I, O]