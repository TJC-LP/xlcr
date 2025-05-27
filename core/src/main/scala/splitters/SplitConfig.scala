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
  useCloneForSlides: Boolean = true, // Use clone method instead of remove for slide extraction
  preserveSlideNotes: Boolean = true // Preserve slide notes during extraction
) {

  /** Helper method to check if a strategy is set to a specific value */
  def hasStrategy(s: SplitStrategy): Boolean = strategy.contains(s)
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
        MimeType.TextXml =>
      SplitStrategy.Chunk

    case MimeType.TextCsv => SplitStrategy.Row

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
