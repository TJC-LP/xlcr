package com.tjclp.xlcr
package parsers.excel

import parsers.excel.SheetsDataExcelParser
import models.{FileContent, Model}
import types.MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
import base.ParserSpec

class SheetsDataParserSpec extends ParserSpec {

  "SheetsDataExcelParser" should "parse a minimal XLSX" in {
    val parser = new SheetsDataExcelParser()
    val input = FileContent[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type](Array.emptyByteArray, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)
    // This is not a real XLSX, but for demonstration
    // A real test would have actual XLSX bytes

    // Expect likely an error unless we have real data
    val ex = intercept[ParserError] {
      parser.parse(input)
    }
    ex.message should include ("Failed to parse Excel to SheetsData")
  }
}