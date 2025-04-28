package com.tjclp.xlcr
package bridges

import bridges.excel.*
import bridges.tika.{TikaPlainTextBridge, TikaXmlBridge}
import bridges.powerpoint.{SlidesDataPowerPointBridge, SlidesDataJsonBridge}
import bridges.image.{SvgToPngBridge, PdfToPngBridge, PdfToJpegBridge}
import models.excel.SheetsData
import models.powerpoint.SlidesData
import types.MimeType
import types.MimeType._

import scala.collection.concurrent.TrieMap

/**
 * BridgeRegistry manages a set of registered Bridges between mime types.
 * We store them in a mutable map, so we can add more at runtime (e.g. Aspose bridges).
 */
object BridgeRegistry {

  /** Our internal registry from (inMime, outMime) -> Bridge */
  private val registry: TrieMap[(MimeType, MimeType), Bridge[_, _, _]] = TrieMap.empty

  /**
   * Initialize the default bridging from the “core” module.
   * This includes standard Excel, PPT, Tika, etc.
   * Call this once at application start if you want the default bridging.
   */
  def init(): Unit = {
    // If we’ve already inited, do nothing. Otherwise, register default core bridges.

    // SheetsData bridging:
    val excelToJson = SheetsDataExcelBridge.chain(SheetsDataJsonBridge)
    val jsonToExcel = SheetsDataJsonBridge.chain(SheetsDataExcelBridge)
    val excelToMarkdown = SheetsDataExcelBridge.chain(SheetsDataMarkdownBridge)
    val markdownToExcel = SheetsDataMarkdownBridge.chain(SheetsDataExcelBridge)
    val excelToSvg = SheetsDataExcelBridge.chain(SheetsDataSvgBridge)

    // Tika bridging:
    val tikaToXml = TikaXmlBridge
    val tikaToText = TikaPlainTextBridge

    // SlidesData bridging:
    val pptToJson = SlidesDataPowerPointBridge.chain(SlidesDataJsonBridge)
    val jsonToPpt = SlidesDataJsonBridge.chain(SlidesDataPowerPointBridge)

    // Register them:
    register(ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, ApplicationJson, excelToJson)
    register(ApplicationJson, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, jsonToExcel)
    register(ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, TextMarkdown, excelToMarkdown)
    register(TextMarkdown, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, markdownToExcel)
    register(ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, ImageSvgXml, excelToSvg)
    register(ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, ApplicationVndOasisOpendocumentSpreadsheet, ExcelToOdsBridge)
    register(ImageSvgXml, ImagePng, SvgToPngBridge)
    
    // PDF to image conversions
    register(ApplicationPdf, ImagePng, PdfToPngBridge)
    register(ApplicationPdf, ImageJpeg, PdfToJpegBridge)

    register(ApplicationVndMsPowerpoint, ApplicationJson, pptToJson)
    register(ApplicationJson, ApplicationVndMsPowerpoint, jsonToPpt)

    // Generic Tika bridging for “anything” to text/xml:
    // The bridging code uses only outMime, so we can register them for any input:
    // We might do that by registering a fallback approach, but for simplicity, do direct here:
    registerCatchAllTo(ApplicationXml, tikaToXml)
    registerCatchAllTo(TextPlain, tikaToText)
  }
  
  /**
   * Register a single Bridge for (inMime, outMime).
   */
  def register(inMime: MimeType, outMime: MimeType, bridge: Bridge[_, _, _]): Unit = {
    registry.update((inMime, outMime), bridge)
  }

  /**
   * Some bridging logic (like Tika) can handle “any” input -> certain output,
   * so we provide a helper to register for all known input types.
   * Typically used for Tika bridging, which is a catch-all approach.
   */
  def registerCatchAllTo(outputMime: MimeType, bridge: Bridge[_, _, _]): Unit = {
    // We can either do a big enumerations, or store a special pattern,
    // but for simplicity, we iterate all known mime types from MimeType.values
    for (mt <- MimeType.values) {
      // Skip if outMime is the same as inMime or some conflict
      if (mt != outputMime) {
        register(mt, outputMime, bridge)
      }
    }
  }

  /**
   * Find the appropriate bridge for converting between mime types.
   * Returns Some(bridge) if found, otherwise None.
   */
  def findBridge(inMime: MimeType, outMime: MimeType): Option[Bridge[_, _, _]] = {
    registry.get((inMime, outMime))
  }

  /**
   * Check if we can merge between these mime types
   */
  def supportsMerging(input: MimeType, output: MimeType): Boolean =
    findMergeableBridge(input, output).isDefined

  /**
   * Find a bridge that supports merging between these mime types
   */
  def findMergeableBridge(input: MimeType, output: MimeType): Option[MergeableBridge[_, _, _]] =
    findBridge(input, output) match {
      case Some(b: MergeableBridge[_, _, _]) => Some(b)
      case _ => None
    }
}