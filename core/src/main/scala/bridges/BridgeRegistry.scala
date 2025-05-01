package com.tjclp.xlcr
package bridges

import bridges.excel._
import bridges.image.{PdfToJpegBridge, PdfToPngBridge, SvgToPngBridge}
import bridges.powerpoint.{SlidesDataJsonBridge, SlidesDataPowerPointBridge}
import bridges.tika.{TikaPlainTextBridge, TikaXmlBridge}
import types.MimeType._
import types.{MimeType, Priority}
import utils.PriorityRegistry

import org.slf4j.LoggerFactory

/** BridgeRegistry manages a set of registered Bridges between mime types.
  * We store them in a priority-based registry, so bridges with higher priority
  * will be preferred when multiple bridges are available for the same conversion path.
  */
object BridgeRegistry {
  private val logger = LoggerFactory.getLogger(getClass)

  /** Thread‑safe registry from (inMime, outMime) -> Priority-ordered list of Bridges */
  private lazy val registry
      : PriorityRegistry[(MimeType, MimeType), Bridge[_, _, _]] =
    new PriorityRegistry[(MimeType, MimeType), Bridge[_, _, _]]()

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

    register(
      ApplicationVndMsPowerpoint,
      ApplicationJson,
      Priority.CORE,
      pptToJson
    )
    register(
      ApplicationJson,
      ApplicationVndMsPowerpoint,
      Priority.CORE,
      jsonToPpt
    )

    // Generic Tika bridging for "anything" to text/xml:
    // The bridging code uses only outMime, so we can register them for any input:
    // We might do that by registering a fallback approach, but for simplicity, do direct here:
    registerCatchAllTo(
      ApplicationXml,
      tikaToXml
    ) // TikaBridge already has LOW priority
    registerCatchAllTo(
      TextPlain,
      tikaToText
    ) // TikaBridge already has LOW priority
  }

  /** Register a single Bridge for (inMime, outMime) with a specific priority.
    * Note: This method should be avoided in favor of using bridges with their own priority.
    */
  def register(
      inMime: MimeType,
      outMime: MimeType,
      priority: Priority,
      bridge: Bridge[_, _, _]
  ): Unit = {
    logger.info(
      s"Registering ${bridge.getClass.getSimpleName} for $inMime -> $outMime with priority $priority"
    )
    logger.warn(
      s"Bridge ${bridge.getClass.getSimpleName} has native priority ${bridge.priority} but is being registered with $priority"
    )
    logger.warn(
      "Consider updating the bridge to use the correct priority directly instead of overriding it here"
    )
    registry.register((inMime, outMime), bridge)
  }

  /** Register a single Bridge for (inMime, outMime) using the bridge's native priority.
    */
  def register(
      inMime: MimeType,
      outMime: MimeType,
      bridge: Bridge[_, _, _]
  ): Unit = {
    logger.info(
      s"Registering ${bridge.getClass.getSimpleName} for $inMime -> $outMime with native priority ${bridge.priority}"
    )
    registry.register((inMime, outMime), bridge)
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
    registry.get((inMime, outMime))
  }

  /** Find all bridges registered for the given mime types, in priority order.
    */
  def findAllBridges(
      inMime: MimeType,
      outMime: MimeType
  ): List[Bridge[_, _, _]] = {
    registry.getAll((inMime, outMime))
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
    registry.entries
      .map { case ((inMime, outMime), bridge) =>
        (inMime, outMime, bridge.getClass.getSimpleName, bridge.priority)
      }
      .toSeq
      .sortBy(t => (t._1.toString, t._2.toString))
  }
}
