package com.tjclp.xlcr
package bridges.excel

import base.BridgeSpec
import models.FileContent
import models.excel.SheetsData
import types.MimeType.TextMarkdown

class SheetsDataMarkdownBridgeSpec extends BridgeSpec {

  "SheetsDataMarkdownBridge" should "convert SheetsData to markdown" in {
    val sheets = SheetsData(Nil)
    val fc = SheetsDataMarkdownBridge.render(sheets)
    fc.mimeType shouldBe TextMarkdown
    // further tests needed
  }

  it should "parse markdown back to SheetsData" in {
    val md = "# Sample MD"
    val bytes = md.getBytes
    val fc = FileContent[TextMarkdown.type](bytes, TextMarkdown)
    val model = SheetsDataMarkdownBridge.parseInput(fc)
    model.sheets shouldBe empty
  }
}