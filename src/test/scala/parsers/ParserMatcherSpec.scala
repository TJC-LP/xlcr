package com.tjclp.xlcr
package parsers

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import types.MimeType
import parsers.excel.{ExcelJsonParser, ExcelMarkdownParser, JsonToExcelParser}
import parsers.tika.{StandardTikaParser, XMLTikaParser}

class ParserMatcherSpec extends AnyFlatSpec with Matchers {

  "ParserMatcher" should "find appropriate parser for Excel to JSON conversion" in {
    val parser = ParserMatcher.findParser(
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
      MimeType.ApplicationJson
    )
    
    parser shouldBe defined
    parser.get shouldBe ExcelJsonParser
  }

  it should "find appropriate parser for Excel to Markdown conversion" in {
    val parser = ParserMatcher.findParser(
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
      MimeType.TextMarkdown
    )
    
    parser shouldBe defined
    parser.get shouldBe ExcelMarkdownParser
  }

  it should "find appropriate parser for JSON to Excel conversion" in {
    val parser = ParserMatcher.findParser(
      MimeType.ApplicationJson,
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
    )
    
    parser shouldBe defined
    parser.get shouldBe JsonToExcelParser
  }

  it should "find Tika parser for generic document types" in {
    val parser = ParserMatcher.findParser(
      MimeType.ApplicationPdf,
      MimeType.TextPlain
    )
    
    parser shouldBe defined
    parser.get shouldBe StandardTikaParser
  }

  it should "find XML Tika parser for XML output" in {
    val parser = ParserMatcher.findParser(
      MimeType.ApplicationPdf,
      MimeType.ApplicationXml
    )
    
    parser shouldBe defined
    parser.get shouldBe XMLTikaParser
  }

  it should "return None for unsupported conversion" in {
    val parser = ParserMatcher.findParser(
      MimeType.ImageJpeg,
      MimeType.ApplicationVndMsPowerpoint
    )
    
    parser shouldBe empty
  }

  it should "select highest priority parser when multiple matches exist" in {
    // This test assumes ExcelParser has higher priority than generic parsers
    val parser = ParserMatcher.findParser(
      MimeType.ApplicationVndMsExcel,
      MimeType.ApplicationJson
    )
    
    parser shouldBe defined
    parser.get.priority should be > StandardTikaParser.priority
  }
}