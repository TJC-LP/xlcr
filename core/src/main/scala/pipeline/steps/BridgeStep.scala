package com.tjclp.xlcr
package pipeline.steps

import bridges.BridgeRegistry
import models.FileContent
import pipeline.PipelineStep
import types.MimeType

/** Convert any supported document into *plain text* using the catch‑all Tika
  * bridge that is registered for `* -> text/plain`.
  */
trait BridgeStep[O <: MimeType]
    extends PipelineStep[FileContent[MimeType], FileContent[O]] {

  val targetMime: O

  override def run(input: FileContent[MimeType]): FileContent[O] =
    BridgeRegistry.findBridge(input.mimeType, targetMime) match {
      case Some(bridge) => bridge.convert(input)
      case _ =>
        throw UnsupportedConversionException(
          input.mimeType.mimeType,
          targetMime.mimeType
        )
    }
}
