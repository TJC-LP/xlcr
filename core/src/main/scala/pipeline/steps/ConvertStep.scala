package com.tjclp.xlcr.pipeline.steps

import com.tjclp.xlcr.pipeline.PipelineStep
import com.tjclp.xlcr.models.FileContent
import com.tjclp.xlcr.types.MimeType
import com.tjclp.xlcr.bridges.{Bridge, BridgeRegistry}
import com.tjclp.xlcr.{UnsupportedConversionException}

/**
 * A thin adapter that delegates to an existing `Bridge` held inside
 * [[BridgeRegistry]].  It lets us use a bridge as a composable pipeline
 * step without changing any of the bridge code.
 */
final case class ConvertStep(toMime: MimeType) extends PipelineStep[FileContent[MimeType], FileContent[MimeType]] {

  override def run(input: FileContent[MimeType]): FileContent[MimeType] = {
    BridgeRegistry.findBridge(input.mimeType, toMime) match {
      case Some(bridge: Bridge[_, i, o]) =>
        bridge
          .convert(input.asInstanceOf[FileContent[i]])
          .asInstanceOf[FileContent[MimeType]]

      case None => throw UnsupportedConversionException(input.mimeType.mimeType, toMime.mimeType)
    }
  }
}
