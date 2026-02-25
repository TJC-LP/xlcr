package com.tjclp.xlcr.output

/**
 * Utilities for consistent fragment naming across CLI and Server.
 *
 * Naming convention: {paddedIndex}__{sanitizedName}.{ext}
 *   - Index is 1-based and zero-padded to minimum required width
 *   - Double underscore separates index from name for unambiguous parsing
 *   - Name is sanitized for filesystem safety
 */
object FragmentNaming:

  /**
   * Calculate minimum digits needed for zero-padding.
   *
   * @param total
   *   Total number of fragments
   * @return
   *   Number of digits needed (minimum 1)
   */
  def paddingWidth(total: Int): Int =
    if total <= 0 then 1
    else math.ceil(math.log10(total + 1)).toInt.max(1)

  /**
   * Zero-pad an index to the required width.
   *
   * @param index
   *   1-based index to pad
   * @param total
   *   Total number of items (used to calculate padding width)
   * @return
   *   Zero-padded index string
   */
  def padIndex(index: Int, total: Int): String =
    val width = paddingWidth(total)
    String.format(s"%0${width}d", index)

  /**
   * Sanitize a fragment name for filesystem safety.
   *
   * Performs minimal sanitization to preserve the original name for lineage tracking while ensuring
   * filesystem compatibility. Only replaces characters that are invalid on major filesystems
   * (Windows, macOS, Linux).
   *
   * Invalid characters replaced with underscore: \ / : * ? " < > |
   *
   * Preserves: spaces, dots, dashes, underscores, unicode characters
   *
   * @param name
   *   Original fragment name
   * @return
   *   Minimally sanitized name safe for filesystem use
   */
  def sanitizeName(name: String): String =
    name
      .replaceAll("[\\\\/:*?\"<>|]", "_")
      .take(200) // Generous limit while staying under filesystem limits

  /**
   * Build a complete filename for a fragment.
   *
   * Format: {paddedIndex}__{sanitizedName}.{ext}
   *
   * Examples:
   *   - buildFilename(1, 5, "Sheet 1", "xlsx") -> "1__Sheet_1.xlsx"
   *   - buildFilename(3, 15, "Q1 Results", "xlsx") -> "03__Q1_Results.xlsx"
   *   - buildFilename(42, 100, "Page 42", "pdf") -> "042__Page_42.pdf"
   *   - buildFilename(7, 1000, "Slide 7", "pptx") -> "0007__Slide_7.pptx"
   *
   * @param index
   *   1-based index of the fragment
   * @param total
   *   Total number of fragments
   * @param name
   *   Fragment name (will be sanitized)
   * @param extension
   *   File extension (with or without leading dot)
   * @return
   *   Complete filename
   */
  def buildFilename(index: Int, total: Int, name: String, extension: String): String =
    val padded    = padIndex(index, total)
    val sanitized = sanitizeName(name)
    val ext       = extension.stripPrefix(".")
    s"${padded}__${sanitized}.${ext}"
