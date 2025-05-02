package com.tjclp.xlcr.pipeline.steps

import com.tjclp.xlcr.pipeline.PipelineStep
import com.tjclp.xlcr.models.FileContent
import com.tjclp.xlcr.types.MimeType
import com.tjclp.xlcr.bridges.{Bridge, BridgeRegistry}
import com.tjclp.xlcr.UnsupportedConversionException

/**
 * Convert any supported document into *plain text* using the catch‑all Tika
 * bridge that is registered for `* -> text/plain`.
 */
object ExtractTextStep extends PipelineStep[FileContent[MimeType], FileContent[MimeType]] {

  private val targetMime: MimeType = MimeType.TextPlain

  override def run(input: FileContent[MimeType]): FileContent[MimeType] =
    BridgeRegistry.findBridgeForMatching(input.mimeType, targetMime)(
      bridge => bridge.convert(input).asInstanceOf[FileContent[MimeType]],
      throw UnsupportedConversionException(input.mimeType.mimeType, targetMime.mimeType)
    )
}
