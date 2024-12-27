package com.tjclp.xlcr
package models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.decode
import io.circe.syntax._

class SheetDataSpec extends AnyFlatSpec with Matchers {

  val sampleCellData = CellData(
    referenceA1 = "Sheet1!A1",
    rowIndex = 0,
    columnIndex = 0,
    address = "A1",
    cellType = "STRING",
    value = Some("Test"),
    formula = None,
    errorValue = None,
    comment = None,
    commentAuthor = None,
    hyperlink = None,
    dataFormat = None,
    formattedValue = None
  )

  val sampleSheetData = SheetData(
    name = "Sheet1",
    index = 0,
    isHidden = false,
    rowCount = 1,
    columnCount = 1,
    cells = List(sampleCellData),
    mergedRegions = List(),
    protectionStatus = false,
    hasAutoFilter = false
  )

  "SheetData" should "serialize to JSON correctly" in {
    val json = SheetData.toJson(sampleSheetData)
    json should include(""""name":"Sheet1"""")
    json should include(""""rowCount":1""")
    json should include(""""columnCount":1""")
  }

  it should "deserialize from JSON correctly" in {
    val json = SheetData.toJson(sampleSheetData)
    val parsed = SheetData.fromJson(json)
    
    parsed shouldBe a[Right[_, _]]
    parsed.right.get shouldBe sampleSheetData
  }

  it should "handle multiple sheets in JSON array" in {
    val sheets = List(sampleSheetData, sampleSheetData.copy(name = "Sheet2", index = 1))
    val json = SheetData.toJsonMultiple(sheets)
    
    val parsed = SheetData.fromJsonMultiple(json)
    parsed shouldBe a[Right[_, _]]
    parsed.right.get should have length 2
    parsed.right.get.map(_.name) should contain theSameElementsAs List("Sheet1", "Sheet2")
  }

  it should "handle single sheet JSON as multiple for backward compatibility" in {
    val json = SheetData.toJson(sampleSheetData)
    val parsed = SheetData.fromJsonMultiple(json)
    
    parsed shouldBe a[Right[_, _]]
    parsed.right.get should have length 1
    parsed.right.get.head shouldBe sampleSheetData
  }

  it should "fail gracefully with invalid JSON" in {
    val invalidJson = """{"name": "Sheet1", "invalid": true"""
    val parsed = SheetData.fromJson(invalidJson)
    parsed shouldBe a[Left[_, _]]
  }
}