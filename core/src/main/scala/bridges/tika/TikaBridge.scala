package com.tjclp.xlcr
package bridges.tika

import bridges.Bridge
import models.tika.TikaModel
import models.{FileContent, Model}
import parsers.tika.TikaParser
import renderers.tika.TikaRenderer
import types.MimeType

import com.tjclp.xlcr.{BridgeError, ParserError, RendererError}

import scala.reflect.ClassTag

/**
 * TikaBridge ties together a TikaParser and a TikaRenderer
 * for the same input and output T (MimeType).
 *
 * I = input mime type
 * O = output mime type
 */
trait TikaBridge[O <: MimeType]
  extends Bridge[TikaModel[O], MimeType, O] {

  protected def parser: TikaParser[MimeType, O]

  protected def renderer: TikaRenderer[O]

  // Bridge overrides
  override protected def inputParser: TikaParser[MimeType, O] = parser

  override protected def outputRenderer: TikaRenderer[O] = renderer
}