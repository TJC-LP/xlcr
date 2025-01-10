package com.tjclp.xlcr
package bridges

import models.{FileContent, Model}
import types.MimeType

import java.nio.file.Path
import scala.annotation.tailrec
import scala.reflect.ClassTag

/**
 * A Bridge is responsible for (de-)serializing a Model to/from a mime type.
 */
trait Bridge[M <: Model, I <: MimeType, O <: MimeType](using
                                                       mTag: ClassTag[M],
                                                       iTag: ClassTag[I],
                                                       oTag: ClassTag[O]
                                                      ) {
  /**
   * Parse a file path into our intermediate model by reading the bytes.
   */
  def parse(path: Path): M = {
    this.parse(FileContent.fromPath(path).asInstanceOf[FileContent[I]])
  }

  def convert(input: FileContent[I]): FileContent[O] =
    this.render(this.parse(input))

  /**
   * @param input bytes to convert to a model
   */
  def parse(input: FileContent[I]): M = throw new NotImplementedError(
    s"Parse is not supported for model type ${mTag.runtimeClass.getSimpleName} " +
      s"and MimeType ${iTag.runtimeClass.getSimpleName}"
  )

  /**
   * Chains this bridge with another bridge sharing the same model type M
   * to create a new bridge that can convert between their mime types.
   */
  def chain[O2 <: MimeType](using o2Tag: ClassTag[O2])(that: Bridge[M, _, O2]): Bridge[M, I, O2] = new Bridge[M, I, O2] {
    override def parse(input: FileContent[I]): M = Bridge.this.parse(input)

    override def render(model: M): FileContent[O2] = that.render(model)
  }

  /**
   * Convert the model into raw bytes for the given output mimeType.
   */
  def render(model: M): FileContent[O] = throw new NotImplementedError(
    s"Render is not supported for model type ${mTag.runtimeClass.getSimpleName} " +
      s"and MimeType ${oTag.runtimeClass.getSimpleName}"
  )
}
