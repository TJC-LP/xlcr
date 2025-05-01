package com.tjclp.xlcr
package bridges

import types.MimeType
import models.FileContent
import scala.reflect.ClassTag

/**
 * Scala 3 version of SimpleBridge.
 * Extends Bridge with the model type being the input FileContent.
 */
trait SimpleBridge[I <: MimeType, O <: MimeType]
  (using ClassTag[FileContent[I]], ClassTag[I], ClassTag[O])
  extends Bridge[FileContent[I], I, O] with BaseSimpleBridge[I, O]
