package com.tjclp.xlcr.v2.cli

import zio.{ Chunk, ZIO }

import com.tjclp.xlcr.v2.aspose.AsposeTransforms
import com.tjclp.xlcr.v2.core.XlcrTransforms
import com.tjclp.xlcr.v2.libreoffice.LibreOfficeTransforms
import com.tjclp.xlcr.v2.transform.{ TransformError, UnsupportedConversion }
import com.tjclp.xlcr.v2.types.{ Content, ConvertOptions, DynamicFragment, Mime }

/**
 * Unified transform dispatcher with automatic fallback.
 *
 * This object provides the default behavior for the xlcr CLI: it tries Aspose first (higher
 * quality, more features), then falls back to LibreOffice, and finally falls back to XLCR Core
 * (built-in POI/Tika transforms).
 *
 * Priority order:
 *   1. Aspose (preferred - better quality, more conversions) 2. LibreOffice (fallback - open
 *      source, fewer features) 3. XLCR Core (final fallback - built-in, no external dependencies)
 *
 * Usage:
 * {{{
 * import com.tjclp.xlcr.v2.cli.UnifiedTransforms
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
   * Tries Aspose first, falls back to LibreOffice if Aspose doesn't support the conversion, then
   * falls back to XLCR Core if LibreOffice doesn't support it.
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
    AsposeTransforms.convert(input, to, options).catchSome {
      case _: UnsupportedConversion =>
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
    AsposeTransforms.canConvert(from, to) ||
      LibreOfficeTransforms.canConvert(from, to) ||
      XlcrTransforms.canConvert(from, to)

  // ===========================================================================
  // Splitter dispatch with fallback
  // ===========================================================================

  /**
   * Split content into fragments.
   *
   * Tries Aspose first, falls back to LibreOffice if Aspose doesn't support splitting for this MIME
   * type, then falls back to XLCR Core.
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
    AsposeTransforms.split(input, options).catchSome {
      case _: UnsupportedConversion =>
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
    AsposeTransforms.canSplit(mime) ||
      LibreOfficeTransforms.canSplit(mime) ||
      XlcrTransforms.canSplit(mime)
