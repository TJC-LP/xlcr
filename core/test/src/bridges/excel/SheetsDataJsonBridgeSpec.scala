package com.tjclp.xlcr
package bridges.excel

import base.BridgeSpec
import models.FileContent
import models.excel.SheetsData
import types.MimeType.ApplicationJson

class SheetsDataJsonBridgeSpec extends BridgeSpec {

  "SheetsDataJsonBridge" should "convert SheetsData to JSON" in {
    val sheets = SheetsData(Nil)
    val fc     = SheetsDataJsonBridge.render(sheets)
    fc.mimeType shouldBe ApplicationJson
  }

  it should "parse JSON to SheetsData" in {
    val inputJson = """[{"name":"Sheet1","index":0,"cells":[]}]"""
    val bytes     = inputJson.getBytes
    val fc        = FileContent[ApplicationJson.type](bytes, ApplicationJson)
    val model     = SheetsDataJsonBridge.parseInput(fc)
    (model.sheets should have).length(1)
    model.sheets.head.name shouldBe "Sheet1"
  }
}
