package com.tjclp.xlcr
package bridges

import models.FileContent
import types.MimeType

/** Scala 2 version of SimpleBridge.
  * Extends Bridge with the model type being the input FileContent.
  */
trait SimpleBridge[I <: MimeType, O <: MimeType]
    extends Bridge[FileContent[I], I, O]
    with BaseSimpleBridge[I, O]
