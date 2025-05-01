package com.tjclp.xlcr
package bridges

import models.Model
import types.{Mergeable, MimeType}

trait MergeableSymmetricBridge[M <: Model with Mergeable[M], T <: MimeType]
    extends SymmetricBridge[M, T]
    with MergeableBridge[M, T, T]
