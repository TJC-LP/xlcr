package com.tjclp.xlcr
package bridges.excel

import org.scalatest.BeforeAndAfter

import base.BridgeSpec
import models.FileContent
import models.excel.SheetsData
import types.MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet

class SheetsDataExcelBridgeSpec extends BridgeSpec with BeforeAndAfter {

  // Example usage of the bridge
  "SheetsDataExcelBridge" should "convert a basic SheetsData model to XLSX" in {
    val model = SheetsData(Nil)
    val input = FileContent(Array.emptyByteArray, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)
    // Not a real test: no real input data
    // Typically you'd parse input -> model, or build model for test

    // Just verify that we can do a convert call without errors
    val result = SheetsDataExcelBridge.render(model)
    result.mimeType shouldBe ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
  }
}
