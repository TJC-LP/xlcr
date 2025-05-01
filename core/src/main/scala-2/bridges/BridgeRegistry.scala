package com.tjclp.xlcr
package bridges

import bridges.excel._
import bridges.tika.{TikaPlainTextBridge, TikaXmlBridge}
import bridges.powerpoint.{SlidesDataPowerPointBridge, SlidesDataJsonBridge}
import bridges.image.{SvgToPngBridge, PdfToPngBridge, PdfToJpegBridge}
import models.excel.SheetsData
import models.powerpoint.SlidesData
import types.{MimeType, Priority}
import types.MimeType._
import utils.{Prioritized, PriorityRegistry}

import scala.collection.concurrent.TrieMap
import org.slf4j.LoggerFactory

/** BridgeRegistry manages a set of registered Bridges between mime types.
  * We store them in a priority-based registry, so bridges with higher priority
  * will be preferred when multiple bridges are available for the same conversion path.
  */
object BridgeRegistry {
  private val logger = LoggerFactory.getLogger(getClass)

  /** Thread‑safe registry from (inMime, outMime) -> Priority-ordered list of Bridges */
  private lazy val registry: PriorityRegistry[(MimeType, MimeType), PrioritizedBridge] =
    new PriorityRegistry[(MimeType, MimeType), PrioritizedBridge]()

  /**
   * Wrapper class that adds priority to bridges.
   * This is needed because Bridge doesn't directly implement Prioritized.
   */
  private class PrioritizedBridge(val bridge: Bridge[_, _, _], override val priority: Priority) extends Prioritized

  /** Initialize the default bridging from the "core" module.
    * This includes standard Excel, PPT, Tika, etc.
    * Call this once at application start if you want the default bridging.
    */
  def init(): Unit = {
    // If we've already inited, do nothing. Otherwise, register default core bridges.

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

    // Register them with CORE priority:
    register(
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
      ApplicationJson,
      Priority.CORE,
      excelToJson
    )
    register(
      ApplicationJson,
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
      Priority.CORE,
      jsonToExcel
    )
    register(
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
      TextMarkdown,
      Priority.CORE,
      excelToMarkdown
    )
    register(
      TextMarkdown,
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
      Priority.CORE,
      markdownToExcel
    )
    register(
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
      ImageSvgXml,
      Priority.CORE,
      excelToSvg
    )
    register(
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
      ApplicationVndOasisOpendocumentSpreadsheet,
      Priority.CORE,
      ExcelToOdsBridge
    )
    register(ImageSvgXml, ImagePng, Priority.CORE, SvgToPngBridge)

    // PDF to image conversions
    register(ApplicationPdf, ImagePng, Priority.CORE, PdfToPngBridge)
    register(ApplicationPdf, ImageJpeg, Priority.CORE, PdfToJpegBridge)

    register(ApplicationVndMsPowerpoint, ApplicationJson, Priority.CORE, pptToJson)
    register(ApplicationJson, ApplicationVndMsPowerpoint, Priority.CORE, jsonToPpt)

    // Generic Tika bridging for "anything" to text/xml:
    // The bridging code uses only outMime, so we can register them for any input:
    // We might do that by registering a fallback approach, but for simplicity, do direct here:
    registerCatchAllTo(ApplicationXml, Priority.LOW, tikaToXml)  // Lower priority since it's a fallback
    registerCatchAllTo(TextPlain, Priority.LOW, tikaToText)      // Lower priority since it's a fallback
  }

  /** Register a single Bridge for (inMime, outMime) with a specific priority.
    */
  def register(
      inMime: MimeType,
      outMime: MimeType,
      priority: Priority,
      bridge: Bridge[_, _, _]
  ): Unit = {
    registry.register((inMime, outMime), new PrioritizedBridge(bridge, priority))
  }

  /** Register a single Bridge for (inMime, outMime) with default priority.
    */
  def register(
      inMime: MimeType,
      outMime: MimeType,
      bridge: Bridge[_, _, _]
  ): Unit = {
    register(inMime, outMime, Priority.DEFAULT, bridge)
  }

  /** Some bridging logic (like Tika) can handle "any" input -> certain output,
    * so we provide a helper to register for all known input types with a specific priority.
    * Typically used for Tika bridging, which is a catch-all approach.
    */
  def registerCatchAllTo(
      outputMime: MimeType,
      priority: Priority,
      bridge: Bridge[_, _, _]
  ): Unit = {
    // We can either do a big enumerations, or store a special pattern,
    // but for simplicity, we iterate all known mime types from MimeType.values
    for (mt <- MimeType.values) {
      // Skip if outMime is the same as inMime or some conflict
      if (mt != outputMime) {
        register(mt, outputMime, priority, bridge)
      }
    }
  }

  /** Register a catch-all bridge with default priority.
    */
  def registerCatchAllTo(
      outputMime: MimeType,
      bridge: Bridge[_, _, _]
  ): Unit = {
    registerCatchAllTo(outputMime, Priority.DEFAULT, bridge)
  }

  /** Find the appropriate bridge for converting between mime types.
    * Returns Some(bridge) if found, otherwise None.
    * If multiple bridges are registered, the one with the highest priority is returned.
    */
  def findBridge(
      inMime: MimeType,
      outMime: MimeType
  ): Option[Bridge[_, _, _]] = {
    registry.get((inMime, outMime)).map(_.bridge)
  }

  /** Find all bridges registered for the given mime types, in priority order.
    */
  def findAllBridges(
      inMime: MimeType,
      outMime: MimeType
  ): List[Bridge[_, _, _]] = {
    registry.getAll((inMime, outMime)).map(_.bridge)
  }

  /** Check if we can merge between these mime types
    */
  def supportsMerging(input: MimeType, output: MimeType): Boolean = {
    findMergeableBridge(input, output).isDefined
  }

  /** Find a bridge that supports merging between these mime types
    */
  def findMergeableBridge(
      input: MimeType,
      output: MimeType
  ): Option[MergeableBridge[_, _, _]] = {
    findBridge(input, output) match {
      case Some(b: MergeableBridge[_, _, _]) => Some(b)
      case _                                 => None
    }
  }
  
  /** Diagnostic method to list all registered bridges with their priorities.
    * Useful for debugging and logging.
    */
  def listRegisteredBridges(): Seq[(MimeType, MimeType, String, Priority)] = {
    registry.entries.map { case ((inMime, outMime), prioritizedBridge) =>
      (inMime, outMime, prioritizedBridge.bridge.getClass.getSimpleName, prioritizedBridge.priority)
    }.toSeq.sortBy(t => (t._1.toString, t._2.toString))
  }
}