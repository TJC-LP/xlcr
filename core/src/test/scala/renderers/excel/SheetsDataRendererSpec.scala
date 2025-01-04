package com.tjclp.xlcr
package renderers.excel

import models.excel.SheetsData
import base.RendererSpec
import types.MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet

class SheetsDataRendererSpec extends RendererSpec {

  "SheetsDataExcelRenderer" should "render an empty SheetsData" in {
    val renderer = new SheetsDataExcelRenderer()
    val model = SheetsData(Nil)
    val result = renderer.render(model)
    result.mimeType shouldBe ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
  }
}