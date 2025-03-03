package com.tjclp.xlcr
package bridges.spreadsheetllm

import bridges.{Bridge, SimpleBridge}
import models.spreadsheetllm.CompressedWorkbook
import parsers.spreadsheetllm.ExcelToLLMParser
import renderers.spreadsheetllm.LLMJsonRenderer
import types.MimeType

import scala.reflect.ClassTag

/**
 * Bridge that converts Excel files to LLM-friendly JSON using the SpreadsheetLLM
 * compression techniques.
 */
object ExcelToLLMJsonBridge:
  /**
   * Creates a bridge for converting XLSX files to LLM-friendly JSON.
   *
   * @param config Optional configuration for the compression pipeline
   * @return Bridge for XLSX -> LLM JSON conversion
   */
  def forXlsx(config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()): Bridge[CompressedWorkbook, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type, MimeType.ApplicationJson.type] =
    // Create a custom bridge implementation
    new Bridge[CompressedWorkbook, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type, MimeType.ApplicationJson.type]:
      override protected def inputParser = ExcelToLLMParser.forXlsx(config)
      override protected def outputRenderer = LLMJsonRenderer()
  
  /**
   * Creates a bridge for converting XLS files to LLM-friendly JSON.
   *
   * @param config Optional configuration for the compression pipeline
   * @return Bridge for XLS -> LLM JSON conversion
   */
  def forXls(config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()): Bridge[CompressedWorkbook, MimeType.ApplicationVndMsExcel.type, MimeType.ApplicationJson.type] =
    // Create a custom bridge implementation
    new Bridge[CompressedWorkbook, MimeType.ApplicationVndMsExcel.type, MimeType.ApplicationJson.type]:
      override protected def inputParser = ExcelToLLMParser.forXls(config)
      override protected def outputRenderer = LLMJsonRenderer()