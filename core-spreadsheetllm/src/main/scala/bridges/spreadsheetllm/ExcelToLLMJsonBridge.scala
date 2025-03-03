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
   * @return Bridge for XLSX -> LLM JSON conversion
   */
  def forXlsx(): Bridge[CompressedWorkbook, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type, MimeType.ApplicationJson.type] =
    // Create a custom bridge implementation
    new Bridge[CompressedWorkbook, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type, MimeType.ApplicationJson.type]:
      override protected def inputParser = ExcelToLLMParser.forXlsx()
      override protected def outputRenderer = LLMJsonRenderer()
  
  /**
   * Creates a bridge for converting XLS files to LLM-friendly JSON.
   *
   * @return Bridge for XLS -> LLM JSON conversion
   */
  def forXls(): Bridge[CompressedWorkbook, MimeType.ApplicationVndMsExcel.type, MimeType.ApplicationJson.type] =
    // Create a custom bridge implementation
    new Bridge[CompressedWorkbook, MimeType.ApplicationVndMsExcel.type, MimeType.ApplicationJson.type]:
      override protected def inputParser = ExcelToLLMParser.forXls()
      override protected def outputRenderer = LLMJsonRenderer()