package com.tjclp.xlcr
package bridges.excel

import bridges.MergeableSymmetricBridge
import models.FileContent
import models.excel.{SheetData, SheetsData}
import parsers.Parser
import parsers.excel.SheetsDataMarkdownParser
import renderers.Renderer
import renderers.excel.SheetsDataMarkdownRenderer
import types.MimeType
import types.MimeType.TextMarkdown

import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook

import java.nio.charset.StandardCharsets
import scala.collection.mutable

/**
 * SheetsDataMarkdownBridge is a symmetric bridge that converts
 * SheetsData to/from Markdown. This merges the logic from the
 * older MarkdownExcelInputBridge and ExcelMarkdownOutputBridge.
 */
object SheetsDataMarkdownBridge extends MergeableSymmetricBridge[
  SheetsData,
  TextMarkdown.type
] {
  override protected def parser = new SheetsDataMarkdownParser()

  override protected def renderer = new SheetsDataMarkdownRenderer()
}