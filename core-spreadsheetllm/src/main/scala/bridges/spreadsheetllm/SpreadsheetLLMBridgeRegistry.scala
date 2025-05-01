package com.tjclp.xlcr
package bridges.spreadsheetllm

import bridges.BridgeRegistry
import types.MimeType

import org.slf4j.LoggerFactory

/** Registry for SpreadsheetLLM-related bridges, providing conversion
  * between Excel formats and the LLM-friendly JSON representation.
  */
object SpreadsheetLLMBridgeRegistry {
  private val logger = LoggerFactory.getLogger(getClass)
  private var initialized = false

  /** Register all SpreadsheetLLM bridges with the main BridgeRegistry.
    * This makes them available to the XLCR pipeline system.
    *
    * @param config Optional configuration for the compression pipeline
    */
  def registerAll(config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()): Unit =
    synchronized {
      if (initialized) {
        return
      }

      logger.info(s"Registering SpreadsheetLLM bridges with config: $config")

      // Register Excel -> LLM JSON bridges
      val xlsxBridge = ExcelToLLMJsonBridge.forXlsx(config)
      BridgeRegistry.register(
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
        MimeType.ApplicationJson,
        xlsxBridge
      )

      // Register other Excel format bridges as needed
      val xlsBridge = ExcelToLLMJsonBridge.forXls(config)
      BridgeRegistry.register(
        MimeType.ApplicationVndMsExcel,
        MimeType.ApplicationJson,
        xlsBridge
      )

      logger.info("Successfully registered SpreadsheetLLM bridges")
      initialized = true
    }
}
