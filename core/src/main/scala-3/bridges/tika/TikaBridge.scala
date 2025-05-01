package com.tjclp.xlcr
package bridges.tika

import bridges.{Bridge, LowPriorityBridge}
import models.tika.TikaModel
import parsers.tika.TikaParser
import renderers.tika.TikaRenderer
import types.{MimeType, Priority}

/**
 * TikaBridge ties together a TikaParser and a TikaRenderer
 * for the same input and output T (MimeType).
 *
 * I = input mime type
 * O = output mime type
 */
trait TikaBridge[O <: MimeType]
  extends LowPriorityBridge[TikaModel[O], MimeType, O] {

  protected def parser: TikaParser[MimeType, O]

  protected def renderer: TikaRenderer[O]

  // Bridge overrides
  override def inputParser: TikaParser[MimeType, O] = parser

  override def outputRenderer: TikaRenderer[O] = renderer
}