package com.tjclp.xlcr
package bridges.excel

import bridges.MergeableSymmetricBridge
import models.excel.SheetsData
import parsers.excel.SheetsDataMarkdownParser
import renderers.excel.SheetsDataMarkdownRenderer
import types.MimeType
import types.MimeType.TextMarkdown

import scala.reflect.ClassTag

/**
 * SheetsDataMarkdownBridge is a symmetric bridge that converts
 * SheetsData to/from Markdown. This merges the logic from the
 * older MarkdownExcelInputBridge and ExcelMarkdownOutputBridge.
 */
object SheetsDataMarkdownBridge extends MergeableSymmetricBridge[
  SheetsData,
  TextMarkdown.type
] {
  implicit val mTag: ClassTag[SheetsData] = implicitly[ClassTag[SheetsData]]
  implicit val tTag: ClassTag[TextMarkdown.type] = implicitly[ClassTag[TextMarkdown.type]]
  
  override protected def parser = new SheetsDataMarkdownParser()

  override protected def renderer = new SheetsDataMarkdownRenderer()
}