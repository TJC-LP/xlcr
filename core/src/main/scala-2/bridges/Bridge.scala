package com.tjclp.xlcr
package bridges

import models.{FileContent, Model}
import parsers.Parser
import renderers.Renderer
import types.MimeType

import scala.reflect.ClassTag

/** A Bridge ties together a Parser and a Renderer to transform
  * FileContent[I] -> Model -> FileContent[O].
  *
  * Scala 2 version of Bridge extends the common BaseBridge and
  * handles implicit parameters with explicit definitions.
  *
  * @tparam M The internal model type
  * @tparam I The input MimeType
  * @tparam O The output MimeType
  */
trait Bridge[M <: Model, I <: MimeType, O <: MimeType]
    extends BaseBridge[M, I, O] {
  // Scala 2 requires these to be defined explicitly
  implicit val mTag: ClassTag[M] = implicitly[ClassTag[M]]
  implicit val iTag: ClassTag[I] = implicitly[ClassTag[I]]
  implicit val oTag: ClassTag[O] = implicitly[ClassTag[O]]

  /** Attempt to chain this bridge with another, resulting in a new
    * Bridge from I -> the other bridge's output type.
    */
  def chain[O2 <: MimeType](
      that: Bridge[M, _, O2]
  )(implicit o2Tag: ClassTag[O2]): Bridge[M, I, O2] = {
    new Bridge[M, I, O2] {
      override implicit val mTag: ClassTag[M] = Bridge.this.mTag
      override implicit val iTag: ClassTag[I] = Bridge.this.iTag
      override implicit val oTag: ClassTag[O2] = o2Tag

      override def inputParser: Parser[I, M] = Bridge.this.inputParser
      override def outputParser: Option[Parser[O2, M]] = that.outputParser
      override def inputRenderer: Option[Renderer[M, I]] =
        Bridge.this.inputRenderer
      override def outputRenderer: Renderer[M, O2] = that.outputRenderer
    }
  }
}
