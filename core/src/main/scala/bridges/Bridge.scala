package com.tjclp.xlcr
package bridges

import models.{FileContent, Model}
import types.{Mergeable, MimeType}

import java.nio.file.Path
import scala.reflect.ClassTag

/**
 * A Bridge is responsible for (de-)serializing a Model to/from mime types.
 * It supports parsing from both input and output mime types, and rendering to output mime type.
 */
trait Bridge[M <: Model, I <: MimeType, O <: MimeType](using
                                                       mTag: ClassTag[M],
                                                       iTag: ClassTag[I],
                                                       oTag: ClassTag[O]
                                                      ) {
  /**
   * Parse a file path into our intermediate model by reading the bytes.
   */
  def parseInput(path: Path): M = {
    this.parseInput(FileContent.fromPath(path).asInstanceOf[FileContent[I]])
  }

  def convert(input: FileContent[I]): FileContent[O] =
    this.render(this.parseInput(input))

  /**
   * Parse input mime type content into model
   */
  def parseInput(input: FileContent[I]): M = throw new NotImplementedError(
    s"Parse is not supported for model type ${mTag.runtimeClass.getSimpleName} " +
      s"and MimeType ${iTag.runtimeClass.getSimpleName}"
  )

  /**
   * Convert the model into raw bytes for the output mimeType.
   */
  def render(model: M): FileContent[O] = throw new NotImplementedError(
    s"Render is not supported for model type ${mTag.runtimeClass.getSimpleName} " +
      s"and MimeType ${oTag.runtimeClass.getSimpleName}"
  )

  /**
   * Chains this bridge with another bridge sharing the same model type M
   * to create a new bridge that can convert between their mime types.
   * The new bridge inherits parsing capabilities from both bridges.
   */
  def chain[O2 <: MimeType](that: Bridge[M, _, O2])(using o2Tag: ClassTag[O2]): Bridge[M, I, O2] = new Bridge[M, I, O2] {
    override def parseInput(input: FileContent[I]): M = Bridge.this.parseInput(input)

    override def parseOutput(output: FileContent[O2]): M = that.parseOutput(output)

    override def render(model: M): FileContent[O2] = that.render(model)
  }

  /**
   * Parse output mime type content into model
   */
  def parseOutput(output: FileContent[O]): M = throw new NotImplementedError(
    s"Parse is not supported for model type ${mTag.runtimeClass.getSimpleName} " +
      s"and MimeType ${oTag.runtimeClass.getSimpleName}"
  )

  /**
   * Attempt a type-safe merge of two file contents that share the same model type & mime type,
   * requiring that the model extends Mergeable. The result is a single merged FileContent.
   *
   * @param source The 'new' or 'incoming' file content
   * @param target The 'existing' file content
   * @return FileContent[O] representing the merged result
   */
  def convertWithDiff(
                       source: FileContent[I],
                       target: FileContent[O]
                     )(implicit ev: M <:< Mergeable[M]): FileContent[O] = {
    val sourceModel: M = parseInput(source)
    val targetModel: M = parseOutput(target)
    val merged: M = targetModel.merge(sourceModel)
    render(merged)
  }
}
