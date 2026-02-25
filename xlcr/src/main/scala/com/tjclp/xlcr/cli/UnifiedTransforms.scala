package com.tjclp.xlcr.cli

import com.tjclp.xlcr.cli.Commands.Backend
import com.tjclp.xlcr.core.XlcrTransforms
import com.tjclp.xlcr.libreoffice.LibreOfficeTransforms
import com.tjclp.xlcr.transform.*
import com.tjclp.xlcr.types.*

import zio.*

/**
 * Unified transform dispatcher with automatic fallback and optional backend selection.
 *
 * Default behavior (no backend specified): tries Aspose first (higher quality, more features), then
 * falls back to LibreOffice, and finally falls back to XLCR Core (built-in POI/Tika transforms).
 *
 * When a specific backend is requested, routes directly to that backend with no fallback.
 *
 * If Aspose is not included at build time (no license detected), BackendWiring stubs immediately
 * return UnsupportedConversion, so the fallback is instant. If Aspose IS included but a specific
 * product isn't licensed at runtime, ResourceError triggers the same fallback.
 *
 * Priority order (default):
 *   1. Aspose (preferred - better quality, more conversions) 2. LibreOffice (fallback - open
 *      source, fewer features) 3. XLCR Core (final fallback - built-in, no external dependencies)
 *
 * Usage:
 * {{{
 * import com.tjclp.xlcr.cli.UnifiedTransforms
 *
 * // Automatically uses best available backend
 * val result = UnifiedTransforms.convert(content, Mime.pdf)
 *
 * // Use a specific backend
 * val result = UnifiedTransforms.convert(content, Mime.pdf, backend = Some(Backend.LibreOffice))
 * }}}
 */
object UnifiedTransforms:

  // ===========================================================================
  // Conversion dispatch with fallback
  // ===========================================================================

  /**
   * Convert content to a target MIME type.
   *
   * When `backend` is None, tries Aspose first, falls back to LibreOffice, then to XLCR Core. When
   * a specific backend is given, routes directly with no fallback.
   *
   * @param input
   *   The input content to convert
   * @param to
   *   The target MIME type
   * @param options
   *   Conversion options (password, layout, etc.)
   * @param backend
   *   Optional backend override (None = auto-fallback)
   * @return
   *   The converted content or TransformError
   */
  def convert(
    input: Content[Mime],
    to: Mime,
    options: ConvertOptions = ConvertOptions(),
    backend: Option[Backend] = None
  ): ZIO[Any, TransformError, Content[Mime]] =
    backend match
      case Some(Backend.Aspose)      => BackendWiring.asposeConvert(input, to, options)
      case Some(Backend.LibreOffice) => LibreOfficeTransforms.convert(input, to, options)
      case Some(Backend.Xlcr)        => XlcrTransforms.convert(input, to)
      case None                      => convertWithFallback(input, to, options)

  /**
   * Check if a conversion is supported.
   *
   * When `backend` is None, returns true if ANY backend supports it. When a specific backend is
   * given, checks only that backend.
   */
  def canConvert(from: Mime, to: Mime, backend: Option[Backend] = None): Boolean =
    backend match
      case Some(Backend.Aspose)      => BackendWiring.asposeCanConvert(from, to)
      case Some(Backend.LibreOffice) => LibreOfficeTransforms.canConvert(from, to)
      case Some(Backend.Xlcr)        => XlcrTransforms.canConvert(from, to)
      case None                      =>
        BackendWiring.asposeCanConvert(from, to) ||
        LibreOfficeTransforms.canConvert(from, to) ||
        XlcrTransforms.canConvert(from, to)

  // ===========================================================================
  // Splitter dispatch with fallback
  // ===========================================================================

  /**
   * Split content into fragments.
   *
   * When `backend` is None, tries Aspose first, falls back to LibreOffice, then to XLCR Core. When
   * a specific backend is given, routes directly with no fallback.
   *
   * @param input
   *   The input content to split
   * @param options
   *   Conversion options
   * @param backend
   *   Optional backend override (None = auto-fallback)
   * @return
   *   Chunk of dynamic fragments or TransformError
   */
  def split(
    input: Content[Mime],
    options: ConvertOptions = ConvertOptions(),
    backend: Option[Backend] = None
  ): ZIO[Any, TransformError, Chunk[DynamicFragment]] =
    backend match
      case Some(Backend.Aspose)      => BackendWiring.asposeSplit(input, options)
      case Some(Backend.LibreOffice) => LibreOfficeTransforms.split(input)
      case Some(Backend.Xlcr)        => XlcrTransforms.split(input)
      case None                      => splitWithFallback(input, options)

  /**
   * Check if splitting is supported.
   *
   * When `backend` is None, returns true if ANY backend supports it. When a specific backend is
   * given, checks only that backend.
   */
  def canSplit(mime: Mime, backend: Option[Backend] = None): Boolean =
    backend match
      case Some(Backend.Aspose)      => BackendWiring.asposeCanSplit(mime)
      case Some(Backend.LibreOffice) => LibreOfficeTransforms.canSplit(mime)
      case Some(Backend.Xlcr)        => XlcrTransforms.canSplit(mime)
      case None                      =>
        BackendWiring.asposeCanSplit(mime) ||
        LibreOfficeTransforms.canSplit(mime) ||
        XlcrTransforms.canSplit(mime)

  // ===========================================================================
  // Private: fallback chains (original behavior)
  // ===========================================================================

  private def convertWithFallback(
    input: Content[Mime],
    to: Mime,
    options: ConvertOptions
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

  private def splitWithFallback(
    input: Content[Mime],
    options: ConvertOptions
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
end UnifiedTransforms
