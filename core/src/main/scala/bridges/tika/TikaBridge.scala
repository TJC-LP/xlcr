package com.tjclp.xlcr
package bridges.tika

import bridges.LowPriorityBridge
import models.tika.TikaModel
import parsers.tika.TikaParser
import renderers.tika.TikaRenderer
import types.MimeType

/** TikaBridge ties together a TikaParser and a TikaRenderer
  * for the same input and output T (MimeType).
  * 
  * TikaBridge is designed to act as a catch-all bridge that can handle
  * any input mime type and convert it to a specific output format.
  * When registered with MimeType.Wildcard, it serves as a fallback
  * when no specific bridge is found for an input->output pair.
  *
  * I = input mime type (can be any mime type)
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
