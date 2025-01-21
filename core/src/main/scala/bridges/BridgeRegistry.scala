package com.tjclp.xlcr
package bridges

import bridges.excel.*
import bridges.tika.{TikaPlainTextBridge, TikaXmlBridge}
import models.excel.SheetsData
import types.MimeType
import types.MimeType.*

import scala.reflect.ClassTag

/**
 * AdapterRegistry provides a registry of bridges between different mime types.
 * It matches the appropriate bridge for conversion between mime types.
 */
object BridgeRegistry {

  // The typed bridges for SheetsData:
  private val excelToJson: Bridge[SheetsData, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type, ApplicationJson.type] =
    SheetsDataExcelBridge.chain(SheetsDataJsonBridge)
  private val jsonToExcel: Bridge[SheetsData, ApplicationJson.type, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type] =
    SheetsDataJsonBridge.chain(SheetsDataExcelBridge)
  private val excelToMarkdown: Bridge[SheetsData, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type, TextMarkdown.type] =
    SheetsDataExcelBridge.chain(SheetsDataMarkdownBridge)
  private val markdownToExcel: Bridge[SheetsData, TextMarkdown.type, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type] =
    SheetsDataMarkdownBridge.chain(SheetsDataExcelBridge)
  private val excelToSvg: Bridge[SheetsData, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type, ImageSvgXml.type] =
    SheetsDataExcelBridge.chain(SheetsDataSvgBridge)
  // Tika bridges for generic conversions
  private val tikaToXml: TikaXmlBridge.type = TikaXmlBridge
  private val tikaToText: TikaPlainTextBridge.type = TikaPlainTextBridge

  /**
   * Test if we can merge between these mime types
   */
  def supportsMerging(input: MimeType, output: MimeType): Boolean =
    findMergeableBridge(input, output).isDefined

  /**
   * Find a bridge that supports merging between these mime types
   */
  def findMergeableBridge(
                           input: MimeType,
                           output: MimeType
                         ): Option[MergeableBridge[_, _, _]] =
    findBridge(input, output) match
      case Some(b: MergeableBridge[_, _, _]) => Some(b)
      case _ => None

  /**
   * Find the appropriate bridge for converting between mime types.
   * Returns the bridge if supported, None if unsupported.
   */
  def findBridge(inMime: MimeType, outMime: MimeType): Option[Bridge[_, _, _]] = {
    (inMime, outMime) match {
      // Excel conversions
      case (ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, ApplicationJson) => Some(excelToJson)
      case (ApplicationJson, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet) => Some(jsonToExcel)
      case (ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, TextMarkdown) => Some(excelToMarkdown)
      case (TextMarkdown, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet) => Some(markdownToExcel)
      case (ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, ImageSvgXml) => Some(excelToSvg)

      // Generic Tika conversions
      case (_, ApplicationXml) => Some(tikaToXml)
      case (_, TextPlain) => Some(tikaToText)

      case _ => None
    }
  }
}