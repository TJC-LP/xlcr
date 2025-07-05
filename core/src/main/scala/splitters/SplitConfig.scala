package com.tjclp.xlcr
package splitters

import types.MimeType

/** Configuration for document splitting. */
case class SplitConfig(
  strategy: Option[SplitStrategy] = None,
  maxChars: Int = 8000,
  overlap: Int = 0,
  recursive: Boolean = false,
  maxRecursionDepth: Int = 5,
  maxTotalSize: Long = 1024 * 1024 * 100, // 100MB zipbomb protection

  // Image-related settings (used in Pipeline for post-split conversion)
  maxImageWidth: Int = 2000,                 // Max width in pixels
  maxImageHeight: Int = 2000,                // Max height in pixels
  maxImageSizeBytes: Long = 1024 * 1024 * 5, // 5MB default limit
  imageDpi: Int = 300,                       // DPI for rendering
  jpegQuality: Float = 0.85f,                // JPEG quality factor (0.0-1.0)
  autoTuneImages: Boolean = true,            // Whether to auto-tune image quality/size

  // Excel and zip settings
  maxFileCount: Long = 1000L,

  // PowerPoint slide extraction settings
  useCloneForSlides: Boolean = true,  // Use clone method instead of remove for slide extraction
  preserveSlideNotes: Boolean = true, // Preserve slide notes during extraction

  // Universal chunk extraction settings
  chunkRange: Option[Range] =
    None, // Universal range for any chunk type (pages, sheets, slides, etc.)
  skipBlankPages: Boolean = false,   // Whether to skip blank pages during extraction
  blankPageThreshold: Double = 0.01, // Threshold for considering a page blank (0.0-1.0)

  // Failure handling configuration
  failureMode: SplitFailureMode =
    SplitFailureMode.PreserveAsChunk,             // How to handle splitting failures
  failureContext: Map[String, String] = Map.empty // Additional context for failure handling
) {

  /** Helper method to check if a strategy is set to a specific value */
  def hasStrategy(s: SplitStrategy): Boolean = strategy.contains(s)

  /** Backward compatibility - deprecated in favor of chunkRange */
  @deprecated("Use chunkRange instead", "0.2.0")
  def pageRange: Option[Range] = chunkRange

  /**
   * Create a copy of this config with a specific failure mode.
   *
   * @param mode
   *   The failure mode to use
   * @return
   *   A new SplitConfig with the updated failure mode
   */
  def withFailureMode(mode: SplitFailureMode): SplitConfig =
    copy(failureMode = mode)

  /**
   * Add context information for failure handling. This context will be included in error messages
   * and metadata.
   *
   * @param key
   *   The context key
   * @param value
   *   The context value
   * @return
   *   A new SplitConfig with the added context
   */
  def withFailureContext(key: String, value: String): SplitConfig =
    copy(failureContext = failureContext + (key -> value))

  /**
   * Add multiple context entries for failure handling.
   *
   * @param context
   *   Map of context key-value pairs
   * @return
   *   A new SplitConfig with the added context
   */
  def withFailureContext(context: Map[String, String]): SplitConfig =
    copy(failureContext = failureContext ++ context)
}

object SplitConfig {

  /**
   * Create a SplitConfig with a strategy automatically chosen from the input MIME type. The other
   * parameters default to the same values as the primary case-class constructor so callers only
   * specify what they need.
   */
  def autoForMime(
    mime: MimeType,
    recursive: Boolean = false,
    maxRecursionDepth: Int = 5
  ): SplitConfig =
    SplitConfig(
      strategy = Some(defaultStrategyForMime(mime)),
      recursive = recursive,
      maxRecursionDepth = maxRecursionDepth
    )

  /**
   * Extracted from SplitStep – central place so both core and Spark code can reuse it without
   * duplication.
   */
  def defaultStrategyForMime(mime: MimeType): SplitStrategy = mime match {
    // Text files
    case MimeType.TextPlain |
        MimeType.TextMarkdown |
        MimeType.TextHtml |
        MimeType.TextJavascript |
        MimeType.TextCss |
        MimeType.TextXml |
        MimeType.TextCsv =>
      SplitStrategy.Chunk

    case MimeType.ApplicationPdf => SplitStrategy.Page

    // Excel
    case MimeType.ApplicationVndMsExcel |
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet |
        MimeType.ApplicationVndOasisOpendocumentSpreadsheet |
        MimeType.ApplicationVndMsExcelSheetMacroEnabled |
        MimeType.ApplicationVndMsExcelSheetBinary =>
      SplitStrategy.Sheet

    // PowerPoint
    case MimeType.ApplicationVndMsPowerpoint |
        MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation =>
      SplitStrategy.Slide

    // Archives / containers
    case MimeType.ApplicationZip | MimeType.ApplicationGzip |
        MimeType.ApplicationSevenz | MimeType.ApplicationTar |
        MimeType.ApplicationBzip2 | MimeType.ApplicationXz =>
      SplitStrategy.Embedded

    // Emails
    case MimeType.MessageRfc822 | MimeType.ApplicationVndMsOutlook =>
      SplitStrategy.Attachment

    case _ => SplitStrategy.Page // Default fallback
  }
}
