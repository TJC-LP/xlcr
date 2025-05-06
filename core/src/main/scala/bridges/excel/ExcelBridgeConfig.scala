package com.tjclp.xlcr
package bridges.excel

import bridges.{ BridgeConfig, ConfigConversion }
import parsers.ParserConfig
import parsers.excel.ExcelParserConfig
import renderers.RendererConfig
import renderers.excel.ExcelRendererConfig

/**
 * Configuration for Excel bridges. This provides a unified configuration that can be converted to
 * both Parser and Renderer configurations.
 *
 * @param evaluateFormulas
 *   Whether to evaluate all formulas during parsing and rendering (default: true)
 * @param includeHiddenSheets
 *   Whether to include hidden sheets in the output (default: false)
 * @param maxSheets
 *   Maximum number of sheets to parse (default: unlimited)
 * @param parseNamedRanges
 *   Whether to parse named ranges (default: false)
 * @param autoSizeColumns
 *   Whether to automatically size columns based on content (default: false)
 * @param freezeFirstRow
 *   Whether to freeze the first row (header) (default: false)
 * @param defaultColumnWidth
 *   Default width for columns in characters (default: 12)
 * @param calculateOnSave
 *   Whether to calculate formulas on save (default: true)
 */
case class ExcelBridgeConfig(
  evaluateFormulas: Boolean = true,
  includeHiddenSheets: Boolean = false,
  maxSheets: Option[Int] = None,
  parseNamedRanges: Boolean = false,
  autoSizeColumns: Boolean = false,
  freezeFirstRow: Boolean = false,
  defaultColumnWidth: Int = 12,
  calculateOnSave: Boolean = true
) extends BridgeConfig

/**
 * Provides implicit converters between ExcelBridgeConfig and the corresponding parser and renderer
 * configs.
 */
object ExcelBridgeConfig {
  // Single converter implementation that handles both parser and renderer configs
  implicit val excelConfigConverter: ConfigConversion.DualConverter[ExcelBridgeConfig] =
    new ConfigConversion.DualConverter[ExcelBridgeConfig] {
      override def toParserConfig(config: ExcelBridgeConfig): ParserConfig =
        ExcelParserConfig(
          evaluateFormulas = config.evaluateFormulas,
          includeHiddenSheets = config.includeHiddenSheets,
          maxSheets = config.maxSheets,
          parseNamedRanges = config.parseNamedRanges,
          includeDocumentProperties = false
        )

      override def toRendererConfig(config: ExcelBridgeConfig): RendererConfig =
        ExcelRendererConfig(
          autoSizeColumns = config.autoSizeColumns,
          freezeFirstRow = config.freezeFirstRow,
          defaultColumnWidth = config.defaultColumnWidth,
          enableFormulas = config.evaluateFormulas,
          calculateOnSave = config.calculateOnSave
        )
    }

  // For backward compatibility
  implicit val toParserConfig: ConfigConversion.BridgeToParserConfig[ExcelBridgeConfig] =
    excelConfigConverter

  // For backward compatibility
  implicit val toRendererConfig: ConfigConversion.BridgeToRendererConfig[ExcelBridgeConfig] =
    excelConfigConverter
}
