package com.tjclp.xlcr
package bridges.spreadsheetllm

import bridges.Bridge
import models.spreadsheetllm.CompressedWorkbook
import parsers.spreadsheetllm.ExcelToLLMParser
import renderers.spreadsheetllm.LLMJsonRenderer
import types.MimeType

/** Bridge that converts Excel files to LLM-friendly JSON using the SpreadsheetLLM
  * compression techniques.
  * Compatible with Scala 2.12.
  */
object ExcelToLLMJsonBridge {

  /** Creates a bridge for converting XLSX files to LLM-friendly JSON.
    *
    * @param config Optional configuration for the compression pipeline. Defaults to default config.
    * @return Bridge for XLSX -> LLM JSON conversion.
    */
  def forXlsx(config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()): Bridge[
    CompressedWorkbook,
    MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
    MimeType.ApplicationJson.type
  ] = {
    // Create an anonymous implementation of the Bridge trait/class
    new Bridge[
      CompressedWorkbook,
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
      MimeType.ApplicationJson.type
    ] {

      override private[bridges] def inputParser =
        ExcelToLLMParser.forXlsx(config)

      override private[bridges] def outputRenderer = LLMJsonRenderer()
    }
  }

  /** Creates a bridge for converting XLS files to LLM-friendly JSON.
    *
    * @param config Optional configuration for the compression pipeline. Defaults to default config.
    * @return Bridge for XLS -> LLM JSON conversion.
    */
  def forXls(config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()): Bridge[
    CompressedWorkbook,
    MimeType.ApplicationVndMsExcel.type,
    MimeType.ApplicationJson.type
  ] = {
    // Create an anonymous implementation of the Bridge trait/class
    new Bridge[
      CompressedWorkbook,
      MimeType.ApplicationVndMsExcel.type,
      MimeType.ApplicationJson.type
    ] {

      override private[bridges] def inputParser =
        ExcelToLLMParser.forXls(config)

      override private[bridges] def outputRenderer = LLMJsonRenderer()
    }
  }
}
