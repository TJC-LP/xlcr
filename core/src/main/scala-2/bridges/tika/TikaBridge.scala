package com.tjclp.xlcr
package bridges.tika

import bridges.{Bridge, LowPriorityBridge}
import models.tika.TikaModel
import parsers.tika.TikaParser
import renderers.tika.TikaRenderer
import types.{MimeType, Priority}

import scala.reflect.ClassTag

/**
 * TikaBridge ties together a TikaParser and a TikaRenderer
 * for the same input and output T (MimeType).
 *
 * I = input mime type
 * O = output mime type
 */
trait TikaBridge[O <: MimeType]
  extends LowPriorityBridge[TikaModel[O], MimeType, O] {
  
  implicit val mTag: ClassTag[TikaModel[O]] = implicitly[ClassTag[TikaModel[O]]]
  implicit val iTag: ClassTag[MimeType] = implicitly[ClassTag[MimeType]]
  implicit val oTag: ClassTag[O] = implicitly[ClassTag[O]]

  protected def parser: TikaParser[MimeType, O]

  protected def renderer: TikaRenderer[O]

  // Bridge overrides
  override protected def inputParser: TikaParser[MimeType, O] = parser

  override protected def outputRenderer: TikaRenderer[O] = renderer
}