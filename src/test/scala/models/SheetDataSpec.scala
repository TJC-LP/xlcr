package com.tjclp.xlcr
package models

import io.circe.parser.decode
import io.circe.syntax.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

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
    parsed shouldBe Right(sampleSheetData)
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

  it should "handle sheets with formulas and various data types" in {
    val formulaCell = sampleCellData.copy(
      cellType = "FORMULA",
      formula = Some("=SUM(A1:A10)"),
      value = Some("55"),
      address = "B1"
    )

    val numericCell = sampleCellData.copy(
      cellType = "NUMERIC",
      value = Some("42.5"),
      address = "C1"
    )

    val sheetWithFormulas = sampleSheetData.copy(
      cells = List(sampleCellData, formulaCell, numericCell),
      columnCount = 3
    )

    val json = SheetData.toJson(sheetWithFormulas)
    val parsed = SheetData.fromJson(json)

    parsed shouldBe a[Right[_, _]]
    parsed.right.get.cells should have length 3
    parsed.right.get.cells.find(_.formula.isDefined) shouldBe defined
  }

  it should "handle merged regions correctly" in {
    val sheetWithMerges = sampleSheetData.copy(
      mergedRegions = List("A1:B2", "C3:D4")
    )

    val json = SheetData.toJson(sheetWithMerges)
    val parsed = SheetData.fromJson(json)

    parsed shouldBe a[Right[_, _]]
    parsed.right.get.mergedRegions should have length 2
    parsed.right.get.mergedRegions should contain allOf("A1:B2", "C3:D4")
  }

  it should "preserve cell formatting information" in {
    val formattedCell = sampleCellData.copy(
      dataFormat = Some("#,##0.00"),
      formattedValue = Some("1,234.56")
    )

    val sheetWithFormatting = sampleSheetData.copy(
      cells = List(formattedCell)
    )

    val json = SheetData.toJson(sheetWithFormatting)
    val parsed = SheetData.fromJson(json)

    parsed shouldBe a[Right[_, _]]
    parsed.right.get.cells.head.dataFormat shouldBe defined
    parsed.right.get.cells.head.formattedValue shouldBe defined
  }

  it should "fail gracefully with malformed sheet data" in {
    val malformedJson =
      """{
      "name": "BadSheet",
      "index": "not_a_number",
      "cells": []
    }"""

    val parsed = SheetData.fromJson(malformedJson)
    parsed shouldBe a[Left[_, _]]
  }
}