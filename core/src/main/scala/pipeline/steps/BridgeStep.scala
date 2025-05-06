package com.tjclp.xlcr
package pipeline.steps

import bridges.{ BridgeConfig, BridgeRegistry }
import models.FileContent
import pipeline.PipelineStep
import types.MimeType

/**
 * Convert any supported document into the target mime type using the appropriate bridge.
 *
 * @tparam O
 *   The target output MimeType
 */
trait BridgeStep[O <: MimeType]
    extends PipelineStep[FileContent[MimeType], FileContent[O]] {

  /** The target MIME type to convert to */
  val targetMime: O

  /** Optional configuration for the conversion */
  val config: Option[BridgeConfig] = None

  /**
   * Run the conversion step
   *
   * @param input
   *   The input file content to convert
   * @return
   *   The converted file content
   */
  override def run(input: FileContent[MimeType]): FileContent[O] =
    BridgeRegistry.findBridge(input.mimeType, targetMime) match {
      case Some(bridge) => bridge.convert(input, config)
      case _ =>
        throw UnsupportedConversionException(
          input.mimeType.mimeType,
          targetMime.mimeType
        )
    }
}
