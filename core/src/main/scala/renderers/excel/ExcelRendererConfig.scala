package com.tjclp.xlcr
package renderers.excel

import renderers.RendererConfig

/**
 * Configuration for Excel renderers.
 *
 * @param autoSizeColumns
 *   Whether to automatically size columns based on content (default: false)
 * @param freezeFirstRow
 *   Whether to freeze the first row (header) (default: false)
 * @param defaultColumnWidth
 *   Default width for columns in characters (default: 12)
 * @param enableFormulas
 *   Whether to enable formula evaluation (default: true)
 * @param calculateOnSave
 *   Whether to calculate formulas on save (default: true)
 */
case class ExcelRendererConfig(
  autoSizeColumns: Boolean = false,
  freezeFirstRow: Boolean = false,
  defaultColumnWidth: Int = 12,
  enableFormulas: Boolean = true,
  calculateOnSave: Boolean = true
) extends RendererConfig
