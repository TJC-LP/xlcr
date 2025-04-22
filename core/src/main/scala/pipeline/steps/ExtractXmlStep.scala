package com.tjclp.xlcr.pipeline.steps

import com.tjclp.xlcr.pipeline.PipelineStep
import com.tjclp.xlcr.models.FileContent
import com.tjclp.xlcr.types.MimeType
import com.tjclp.xlcr.bridges.{Bridge, BridgeRegistry}
import com.tjclp.xlcr.UnsupportedConversionException

/**
 * Convert any supported document into an XML representation using the Tika
 * `* -> application/xml` bridge.
 */
object ExtractXmlStep extends PipelineStep[FileContent[MimeType], FileContent[MimeType]] {

  private val targetMime: MimeType = MimeType.ApplicationXml

  override def run(input: FileContent[MimeType]): FileContent[MimeType] =
    BridgeRegistry.findBridge(input.mimeType, targetMime) match {
      case Some(bridge: Bridge[_, i, o]) =>
        bridge.convert(input.asInstanceOf[FileContent[i]]).asInstanceOf[FileContent[MimeType]]
      case None => throw UnsupportedConversionException(input.mimeType.mimeType, targetMime.mimeType)
    }
}
