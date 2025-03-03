package com.tjclp.xlcr
package bridges.spreadsheetllm

import bridges.{Bridge, BridgeRegistry}
import types.{MimeType, FileType, Extension}
import org.slf4j.LoggerFactory

/**
 * Registry for SpreadsheetLLM-related bridges, providing conversion
 * between Excel formats and the LLM-friendly JSON representation.
 */
object SpreadsheetLLMBridgeRegistry:
  private val logger = LoggerFactory.getLogger(getClass)
  private var initialized = false
  
  /**
   * Register all SpreadsheetLLM bridges with the main BridgeRegistry.
   * This makes them available to the XLCR pipeline system.
   */
  def registerAll(): Unit = synchronized {
    if initialized then
      return
      
    logger.info("Registering SpreadsheetLLM bridges")
    
    // Register Excel -> LLM JSON bridges
    val xlsxBridge = ExcelToLLMJsonBridge.forXlsx()
    BridgeRegistry.register(
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
      MimeType.ApplicationJson,
      xlsxBridge
    )
    
    // Register other Excel format bridges as needed
    val xlsBridge = ExcelToLLMJsonBridge.forXls()
    BridgeRegistry.register(
      MimeType.ApplicationVndMsExcel,
      MimeType.ApplicationJson,
      xlsBridge
    )
    
    logger.info("Successfully registered SpreadsheetLLM bridges")
    initialized = true
  }