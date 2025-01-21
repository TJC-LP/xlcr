package com.tjclp.xlcr
package bridges

import models.Model
import types.{Mergeable, MimeType}

trait MergeableSymmetricBridge[M <: Model & Mergeable[M], T <: MimeType] extends SymmetricBridge[M, T]
