package com.tjclp.xlcr
package bridges

import models.{Model, FileContent}
import types.MimeType

trait SymmetricBridge[M <: Model, T <: MimeType] extends Bridge[M, T, T]:

  override def parseOutput(output: FileContent[T]): M = parseInput(output)
