package com.tjclp.xlcr
package models

import com.tjclp.xlcr.models.excel.{CellData, SheetData}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SheetDataSpec extends AnyFlatSpec with Matchers {

  "SheetData" should "identify hidden cells" in {
    import org.apache.poi.xssf.usermodel.XSSFWorkbook

    val workbook = new XSSFWorkbook()
    val sheet = workbook.createSheet("HiddenTestSheet")

    // Create row 0
    val row0 = sheet.createRow(0)
    val cell0 = row0.createCell(0)
    cell0.setCellValue("VisibleCell")

    // Create row 1 (hidden row)
    val row1 = sheet.createRow(1)
    row1.setZeroHeight(true)
    val cell1 = row1.createCell(0)
    cell1.setCellValue("HiddenCellByRow")

    // Create row 2
    val row2 = sheet.createRow(2)
    val cell2 = row2.createCell(0)
    cell2.setCellValue("HiddenCellByStyle")

    // Mark cell style as hidden
    val style = workbook.createCellStyle()
    style.setHidden(true)
    cell2.setCellStyle(style)

    // Create row 3
    val row3 = sheet.createRow(3)
    val cell3 = row3.createCell(1)
    cell3.setCellValue("HiddenByColumn")
    sheet.setColumnHidden(1, true)

    // Evaluate all cells
    val evaluator = workbook.getCreationHelper.createFormulaEvaluator()

    val sheetData = SheetData.fromSheet(sheet, evaluator)
    val cellDataByAddress = sheetData.cells.map(cd => cd.address -> cd).toMap

    // row 0, col 0 => A1
    cellDataByAddress("A1").hidden shouldBe false
    // row 1, col 0 => A2
    cellDataByAddress("A2").hidden shouldBe true // hidden by row
    // row 2, col 0 => A3
    cellDataByAddress("A3").hidden shouldBe true // hidden by cell style
    // row 3, col 1 => B4
    cellDataByAddress("B4").hidden shouldBe true // hidden by column
    workbook.close()
  }

  val sampleCellData: CellData = CellData(
    referenceA1 = "Sheet1!A1",
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

  val sampleSheetData: SheetData = SheetData(
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
    json should include(""""name" : "Sheet1"""")
    json should include(""""rowCount" : 1""")
    json should include(""""columnCount" : 1""")
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
    parsed.toOption.get should have length 2
    parsed.toOption.get.map(_.name) should contain theSameElementsAs List("Sheet1", "Sheet2")
  }

  it should "handle single sheet JSON as multiple for backward compatibility" in {
    val json = SheetData.toJson(sampleSheetData)
    val parsed = SheetData.fromJsonMultiple(json)

    parsed shouldBe a[Right[_, _]]
    parsed.toOption.get should have length 1
    parsed.toOption.get.head shouldBe sampleSheetData
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
    )

    val numericCell = sampleCellData.copy(
      cellType = "NUMERIC",
      value = Some("42.5"),
    )

    val sheetWithFormulas = sampleSheetData.copy(
      cells = List(sampleCellData, formulaCell, numericCell),
      columnCount = 3
    )

    val json = SheetData.toJson(sheetWithFormulas)
    val parsed = SheetData.fromJson(json)

    parsed shouldBe a[Right[_, _]]
    parsed.toOption.get.cells should have length 3
    parsed.toOption.get.cells.find(_.formula.isDefined) shouldBe defined
  }

  it should "handle merged regions correctly" in {
    val sheetWithMerges = sampleSheetData.copy(
      mergedRegions = List("A1:B2", "C3:D4")
    )

    val json = SheetData.toJson(sheetWithMerges)
    val parsed = SheetData.fromJson(json)

    parsed shouldBe a[Right[_, _]]
    parsed.toOption.get.mergedRegions should have length 2
    parsed.toOption.get.mergedRegions should contain allOf("A1:B2", "C3:D4")
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
    parsed.toOption.get.cells.head.dataFormat shouldBe defined
    parsed.toOption.get.cells.head.formattedValue shouldBe defined
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