package com.tjclp.xlcr.cli

import com.tjclp.xlcr.core.XlcrTransforms
import com.tjclp.xlcr.libreoffice.LibreOfficeTransforms
import com.tjclp.xlcr.transform.*
import com.tjclp.xlcr.types.*

import zio.*

/**
 * Unified transform dispatcher with automatic fallback.
 *
 * This object provides the default behavior for the xlcr CLI: it tries Aspose first (higher
 * quality, more features), then falls back to LibreOffice, and finally falls back to XLCR Core
 * (built-in POI/Tika transforms).
 *
 * If Aspose is not included at build time (no license detected), BackendWiring stubs immediately
 * return UnsupportedConversion, so the fallback is instant. If Aspose IS included but a specific
 * product isn't licensed at runtime, ResourceError triggers the same fallback.
 *
 * Priority order:
 *   1. Aspose (preferred - better quality, more conversions) 2. LibreOffice (fallback - open
 *      source, fewer features) 3. XLCR Core (final fallback - built-in, no external dependencies)
 *
 * Usage:
 * {{{
 * import com.tjclp.xlcr.cli.UnifiedTransforms
 *
 * // Automatically uses best available backend
 * val result = UnifiedTransforms.convert(content, Mime.pdf)
 * val fragments = UnifiedTransforms.split(content)
 * }}}
 */
object UnifiedTransforms:

  // ===========================================================================
  // Conversion dispatch with fallback
  // ===========================================================================

  /**
   * Convert content to a target MIME type.
   *
   * Tries Aspose first, falls back to LibreOffice if Aspose doesn't support the conversion or lacks
   * a license for the required product, then falls back to XLCR Core if LibreOffice doesn't support
   * it.
   *
   * @param input
   *   The input content to convert
   * @param to
   *   The target MIME type
   * @return
   *   The converted content or UnsupportedConversion error
   */
  def convert(
    input: Content[Mime],
    to: Mime,
    options: ConvertOptions = ConvertOptions()
  ): ZIO[Any, TransformError, Content[Mime]] =
    BackendWiring.asposeConvert(input, to, options).catchSome {
      case _: UnsupportedConversion | _: ResourceError =>
        ZIO.when(!options.isDefault)(
          ZIO.logWarning(
            s"Falling back to LibreOffice which ignores options: ${options.nonDefaultSummary}"
          )
        ) *> LibreOfficeTransforms.convert(input, to, options)
    }.catchSome {
      case _: UnsupportedConversion =>
        ZIO.when(!options.isDefault)(
          ZIO.logWarning(
            s"Falling back to XLCR Core which ignores options: ${options.nonDefaultSummary}"
          )
        ) *> XlcrTransforms.convert(input, to)
    }

  /**
   * Check if a conversion is supported by any backend.
   */
  def canConvert(from: Mime, to: Mime): Boolean =
    BackendWiring.asposeCanConvert(from, to) ||
      LibreOfficeTransforms.canConvert(from, to) ||
      XlcrTransforms.canConvert(from, to)

  // ===========================================================================
  // Splitter dispatch with fallback
  // ===========================================================================

  /**
   * Split content into fragments.
   *
   * Tries Aspose first, falls back to LibreOffice if Aspose doesn't support splitting for this MIME
   * type or lacks a license, then falls back to XLCR Core.
   *
   * @param input
   *   The input content to split
   * @return
   *   Chunk of dynamic fragments or UnsupportedConversion error
   */
  def split(
    input: Content[Mime],
    options: ConvertOptions = ConvertOptions()
  ): ZIO[Any, TransformError, Chunk[DynamicFragment]] =
    BackendWiring.asposeSplit(input, options).catchSome {
      case _: UnsupportedConversion | _: ResourceError =>
        ZIO.when(!options.isDefault)(
          ZIO.logWarning(
            s"Falling back to LibreOffice which ignores split options: ${options.nonDefaultSummary}"
          )
        ) *> LibreOfficeTransforms.split(input)
    }.catchSome {
      case _: UnsupportedConversion =>
        ZIO.when(!options.isDefault)(
          ZIO.logWarning(
            s"Falling back to XLCR Core which ignores split options: ${options.nonDefaultSummary}"
          )
        ) *> XlcrTransforms.split(input)
    }

  /**
   * Check if splitting is supported by any backend.
   */
  def canSplit(mime: Mime): Boolean =
    BackendWiring.asposeCanSplit(mime) ||
      LibreOfficeTransforms.canSplit(mime) ||
      XlcrTransforms.canSplit(mime)
end UnifiedTransforms
