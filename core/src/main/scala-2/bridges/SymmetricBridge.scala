package com.tjclp.xlcr
package bridges

import models.Model
import parsers.Parser
import renderers.Renderer
import types.MimeType

import scala.reflect.ClassTag

/**
 * A convenience trait for the scenario where input and output
 * mime types are identical.
 *
 * @tparam M The internal model type
 * @tparam T The same mime type for input and output
 */
trait SymmetricBridge[M <: Model, T <: MimeType] extends Bridge[M, T, T] {
  implicit val mTag: ClassTag[M]
  implicit val tTag: ClassTag[T]
  
  /**
   * A single parser for T => M
   */
  protected def parser: Parser[T, M]

  /**
   * A single renderer for M => T
   */
  protected def renderer: Renderer[M, T]

  // Satisfy the Bridge's abstract methods:
  override protected def inputParser: Parser[T, M] = parser

  override protected def outputParser: Option[Parser[T, M]] = Some(parser)

  override protected def inputRenderer: Option[Renderer[M, T]] = Some(renderer)

  override protected def outputRenderer: Renderer[M, T] = renderer
}