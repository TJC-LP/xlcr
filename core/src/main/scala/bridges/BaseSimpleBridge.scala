package com.tjclp.xlcr
package bridges

import models.FileContent
import types.MimeType

/** BaseSimpleBridge provides a simplified version of the Bridge trait for common use cases
  * where the model is just the input FileContent itself.
  *
  * It's extended by the version-specific SimpleBridge traits to handle Scala 2/3 differences.
  *
  * @tparam I The input MimeType
  * @tparam O The output MimeType
  */
trait BaseSimpleBridge[I <: MimeType, O <: MimeType] {
  type M = FileContent[I]
}
