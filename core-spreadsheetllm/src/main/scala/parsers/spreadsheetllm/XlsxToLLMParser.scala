package com.tjclp.xlcr
package parsers.spreadsheetllm

import models.excel.SheetsData
import parsers.Parser
import types.MimeType

/** Parser for XLSX files to CompressedWorkbook models.
  */
class XlsxToLLMParser(
    val config: SpreadsheetLLMConfig,
    val excelParser: Parser[
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
      SheetsData
    ]
) extends ExcelToLLMParser[
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
    ] {}
