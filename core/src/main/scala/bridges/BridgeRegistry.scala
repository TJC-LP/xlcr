package com.tjclp.xlcr
package bridges

import bridges.excel.*
import bridges.tika.{TikaPlainTextBridge, TikaXmlBridge}
import bridges.ppt.{SlidesDataPowerPointBridge, SlidesDataJsonBridge}
import models.excel.SheetsData
import models.ppt.SlidesData
import types.MimeType
import types.MimeType.*

/**
 * AdapterRegistry provides a registry of bridges between different mime types.
 * It matches the appropriate bridge for conversion between mime types.
 */
object BridgeRegistry {

  // The typed bridges for SheetsData:
  private val excelToJson = SheetsDataExcelBridge.chain(SheetsDataJsonBridge)
  private val jsonToExcel = SheetsDataJsonBridge.chain(SheetsDataExcelBridge)
  private val excelToMarkdown = SheetsDataExcelBridge.chain(SheetsDataMarkdownBridge)
  private val markdownToExcel = SheetsDataMarkdownBridge.chain(SheetsDataExcelBridge)
  private val excelToSvg = SheetsDataExcelBridge.chain(SheetsDataSvgBridge)

  // Tika bridges for generic conversions
  private val tikaToXml = TikaXmlBridge
  private val tikaToText = TikaPlainTextBridge

  // The typed bridges for SlidesData:
  // (Symmetric slides - can parse PPTX => SlidesData => PPTX, or JSON => SlidesData => JSON)
  // We also want to chain them for PPTX -> JSON and JSON -> PPTX conversions.
  private val pptToJson = SlidesDataPowerPointBridge.chain(SlidesDataJsonBridge)
  private val jsonToPpt = SlidesDataJsonBridge.chain(SlidesDataPowerPointBridge)

  /**
   * Test if we can merge between these mime types
   */
  def supportsMerging(input: MimeType, output: MimeType): Boolean =
    findMergeableBridge(input, output).isDefined

  /**
   * Find a bridge that supports merging between these mime types
   */
  def findMergeableBridge(input: MimeType, output: MimeType): Option[MergeableBridge[_, _, _]] =
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
      case (ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, TextMarkdown)    => Some(excelToMarkdown)
      case (TextMarkdown, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)    => Some(markdownToExcel)
      case (ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, ImageSvgXml)     => Some(excelToSvg)

      // SlidesData conversions
      // PPT <-> JSON
      case (ApplicationVndMsPowerpoint, ApplicationJson) => Some(pptToJson)
      case (ApplicationJson, ApplicationVndMsPowerpoint) => Some(jsonToPpt)

      // Generic Tika conversions
      case (_, ApplicationXml) => Some(tikaToXml)
      case (_, TextPlain)      => Some(tikaToText)

      case _ => None
    }
  }
}