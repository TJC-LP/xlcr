package com.tjclp.xlcr
package bridges

import types.MimeType
import models.FileContent
import scala.reflect.ClassTag

/**
 * Scala 2 version of SimpleBridge.
 * Extends Bridge with the model type being the input FileContent.
 */
trait SimpleBridge[I <: MimeType, O <: MimeType] extends Bridge[FileContent[I], I, O] with BaseSimpleBridge[I, O] {
  // These will be filled in by concrete implementations
  implicit val mTag: ClassTag[FileContent[I]] = implicitly[ClassTag[FileContent[I]]]
}