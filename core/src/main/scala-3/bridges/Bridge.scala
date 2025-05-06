package com.tjclp.xlcr.bridges

import com.tjclp.xlcr.bridges.BaseBridge
import com.tjclp.xlcr.models.{ FileContent, Model }
import com.tjclp.xlcr.parsers.Parser
import com.tjclp.xlcr.renderers.Renderer
import com.tjclp.xlcr.types.MimeType

import scala.reflect.ClassTag

/**
 * A Bridge ties together a Parser and a Renderer to transform FileContent[I] -> Model ->
 * FileContent[O].
 *
 * Scala 3 version extends the common BaseBridge and handles implicit parameters using the `using`
 * clause.
 *
 * @tparam M
 *   The internal model type
 * @tparam I
 *   The input MimeType
 * @tparam O
 *   The output MimeType
 */
trait Bridge[M <: Model, I <: MimeType, O <: MimeType](
  using
  val mTag: ClassTag[M],
  val iTag: ClassTag[I],
  val oTag: ClassTag[O]
) extends BaseBridge[M, I, O]:

  /**
   * Attempt to chain this bridge with another, resulting in a new Bridge from I -> the other
   * bridge's output type.
   */
  def chain[O2 <: MimeType](that: Bridge[M, _, O2])(using o2Tag: ClassTag[O2]): Bridge[M, I, O2] =
    new Bridge[M, I, O2]:
      override def inputParser: Parser[I, M]             = Bridge.this.inputParser
      override def outputParser: Option[Parser[O2, M]]   = that.outputParser
      override def inputRenderer: Option[Renderer[M, I]] = Bridge.this.inputRenderer
      override def outputRenderer: Renderer[M, O2]       = that.outputRenderer
