package com.tjclp.xlcr
package parsers.spreadsheetllm

import models.excel.SheetsData
import parsers.Parser
import types.MimeType

/** Parser for XLS files to CompressedWorkbook models.
  */
class XlsToLLMParser(
    val config: SpreadsheetLLMConfig,
    val excelParser: Parser[MimeType.ApplicationVndMsExcel.type, SheetsData]
) extends ExcelToLLMParser[MimeType.ApplicationVndMsExcel.type] {}
