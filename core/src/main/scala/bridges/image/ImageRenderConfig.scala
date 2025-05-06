package com.tjclp.xlcr
package bridges.image

import bridges.BridgeConfig
import renderers.RendererConfig
import types.MimeType

/**
 * Generic config for any "binary → raster image" conversion.
 *
 * This configuration provides unified settings for all image rendering operations, including
 * auto-tuning quality/dimensions to meet size constraints.
 *
 * @param maxBytes
 *   Maximum size in bytes for the final image
 * @param maxWidthPx
 *   Maximum width in pixels for the rendered image
 * @param maxHeightPx
 *   Maximum height in pixels for the rendered image
 * @param initialDpi
 *   Initial DPI for rendering (higher DPI = higher quality but larger images)
 * @param initialQuality
 *   Initial quality factor for JPEG rendering (0.0-1.0, higher is better quality but larger size)
 * @param autoTune
 *   Whether to automatically tune rendering parameters to meet size constraints
 * @param maxAttempts
 *   Maximum number of rendering attempts for auto-tuning
 */
final case class ImageRenderConfig(
  maxBytes: Long = 3L << 20, // 3 MiB
  maxWidthPx: Int = 2000,
  maxHeightPx: Int = 2000,
  initialDpi: Int = 300,
  initialQuality: Float = 0.85f,
  autoTune: Boolean = true, // iterate dpi/quality until <= maxBytes
  maxAttempts: Int = 5,
  pageIdx: Int = 0
) extends BridgeConfig
    with RendererConfig

/** Companion object for ImageRenderConfig with conversion utilities */
object ImageRenderConfig {

  /** Safely convert an Option[RendererConfig] to an Option[ImageRenderConfig] */
  def fromRendererConfig(
    configOpt: Option[RendererConfig]
  ): Option[ImageRenderConfig] =
    configOpt
      .flatMap {
        case cfg: ImageRenderConfig => Some(cfg)
        case _                      => None
      }
      .orElse(Some(ImageRenderConfig()))
}
