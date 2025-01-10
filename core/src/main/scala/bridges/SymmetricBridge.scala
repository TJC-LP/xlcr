package com.tjclp.xlcr
package bridges

import models.Model
import types.MimeType

trait SymmetricBridge[M <: Model, T <: MimeType] extends Bridge[M, T, T]
