package com.tjclp.xlcr
package bridges.excel

import bridges.{ BridgeConfig, ConfigConversion, MergeableSymmetricBridge }
import models.excel.SheetsData
import parsers.ParserConfig
import parsers.excel.SheetsDataExcelParser
import renderers.RendererConfig
import renderers.excel.SheetsDataExcelRenderer
import types.MimeType

/**
 * ExcelBridge can parse XLSX bytes into a List[SheetData] and render them back to XLSX. Supports
 * configuration via ExcelBridgeConfig.
 */
object SheetsDataExcelBridge
    extends MergeableSymmetricBridge[
      SheetsData,
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
    ] {

  override protected def parser = new SheetsDataExcelParser()

  override protected def renderer = new SheetsDataExcelRenderer()

  // Add support for config conversion from ExcelBridgeConfig
  // Using the new extractConfigs helper method to simplify the code
  override protected def getParserConfig(config: Option[BridgeConfig]): Option[ParserConfig] =
    ConfigConversion.extractConfigs[ExcelBridgeConfig](config)._1

  override protected def getRendererConfig(config: Option[BridgeConfig]): Option[RendererConfig] =
    ConfigConversion.extractConfigs[ExcelBridgeConfig](config)._2
}
