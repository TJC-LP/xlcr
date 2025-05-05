package com.tjclp.xlcr
package pipeline.steps

import bridges.{BridgeConfig, BridgeRegistry}
import models.FileContent
import pipeline.PipelineStep
import types.MimeType

/** Convert any supported document into the target mime type using the appropriate bridge.
  * 
  * @tparam O The target output MimeType
  */
trait BridgeStep[O <: MimeType]
    extends PipelineStep[FileContent[MimeType], FileContent[O]] {

  /** The target MIME type to convert to */
  val targetMime: O
  
  /** Optional configuration for the conversion */
  val config: Option[BridgeConfig] = None

  /** Run the conversion step
    * 
    * @param input The input file content to convert
    * @return The converted file content
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

/** Factory for creating BridgeStep instances with configuration */
object BridgeStep {
  /** Create a BridgeStep with the specified target MIME type and configuration
    * 
    * @param targetMime The target MIME type to convert to
    * @param config Optional configuration for the conversion
    * @tparam O The target output MimeType
    * @return A BridgeStep instance
    */
  def apply[O <: MimeType](targetMime: O, config: Option[BridgeConfig] = None): BridgeStep[O] = {
    new BridgeStep[O] {
      override val targetMime: O = targetMime
      override val config: Option[BridgeConfig] = config
    }
  }
}
