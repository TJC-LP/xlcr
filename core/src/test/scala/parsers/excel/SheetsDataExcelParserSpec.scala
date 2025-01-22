package com.tjclp.xlcr
package parsers.excel

import models.FileContent
import models.excel.SheetsData
import types.MimeType

import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, Path}

class SheetsDataExcelParserSpec extends AnyFlatSpec with Matchers with BeforeAndAfter {
  private type Excel = MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
  private val parser = new SheetsDataExcelParser()
  private var tempFilePath: Path = _

  before {
    tempFilePath = Files.createTempFile("test", ".xlsx")
  }

  after {
    Files.deleteIfExists(tempFilePath)
  }

  "SheetsDataExcelParser" should "parse basic sheet structure" in {
    ExcelParserTestCommon.createComplexWorkbook(tempFilePath)
    val bytes = Files.readAllBytes(tempFilePath)
    val input = FileContent[Excel](bytes, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)

    val result = parser.parse(input)
    result shouldBe a[SheetsData]
    result.sheets should have length 3

    // Check sheet names
    val sheetNames = result.sheets.map(_.name)
    sheetNames should contain allOf("DataTypes", "Formatting", "Formulas")
  }

  it should "parse various data types correctly" in {
    ExcelParserTestCommon.createComplexWorkbook(tempFilePath)
    val bytes = Files.readAllBytes(tempFilePath)
    val input = FileContent[Excel](bytes, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)

    val result = parser.parse(input)
    val dataTypesSheet = result.sheets.find(_.name == "DataTypes").get

    // Get cells from first row
    val cells = dataTypesSheet.cells.filter(_.rowIndex == 0).sortBy(_.columnIndex)

    // Check string cell
    val stringCell = cells(0)
    stringCell.cellType shouldBe "STRING"
    stringCell.value.get shouldBe "Text"

    // Check number cell
    val numberCell = cells(1)
    numberCell.cellType shouldBe "NUMERIC"
    numberCell.value.get shouldBe "42.5"

    // Check boolean cell
    val booleanCell = cells(2)
    booleanCell.cellType shouldBe "BOOLEAN"
    booleanCell.value.get shouldBe "true"

    // Check error cell
    val errorCell = cells(4)
    errorCell.cellType shouldBe "ERROR"
    errorCell.errorValue shouldBe defined
  }

  it should "parse cell formatting correctly" in {
    ExcelParserTestCommon.createComplexWorkbook(tempFilePath)
    val bytes = Files.readAllBytes(tempFilePath)
    val input = FileContent.fromBytes[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type](bytes)

    val result = parser.parse(input)
    val formattingSheet = result.sheets.find(_.name == "Formatting").get

    // Get cells from first row
    val cells = formattingSheet.cells.filter(_.rowIndex == 0).sortBy(_.columnIndex)

    // Check bold cell
    val boldCell = cells(0)
    boldCell.value.get shouldBe "Bold"
    boldCell.font shouldBe defined
    boldCell.font.get.bold shouldBe true

    // Check colored cell
    val coloredCell = cells(1)
    coloredCell.value.get shouldBe "Background"
    coloredCell.style shouldBe defined
    coloredCell.style.get.foregroundColor shouldBe defined

    // Check borders
    val borderedCell = cells(2)
    borderedCell.value.get shouldBe "Bordered"
    borderedCell.style shouldBe defined
    borderedCell.style.get.borderTop shouldBe defined
    borderedCell.style.get.borderBottom shouldBe defined
    borderedCell.style.get.borderLeft shouldBe defined
    borderedCell.style.get.borderRight shouldBe defined
  }

  it should "parse formulas correctly" in {
    ExcelParserTestCommon.createComplexWorkbook(tempFilePath)
    val bytes = Files.readAllBytes(tempFilePath)
    val input = FileContent[Excel](bytes, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)

    val result = parser.parse(input)
    val formulasSheet = result.sheets.find(_.name == "Formulas").get

    // Get formula cells from second row
    val formulaCells = formulasSheet.cells.filter(_.rowIndex == 1).sortBy(_.columnIndex)

    // Check sum formula
    val sumCell = formulaCells(0)
    sumCell.cellType shouldBe "FORMULA"
    sumCell.formula shouldBe defined
    sumCell.formula.get shouldBe "SUM(A1:B1)"
    sumCell.value shouldBe defined // Should have evaluated value

    // Check average formula
    val avgCell = formulaCells(1)
    avgCell.cellType shouldBe "FORMULA"
    avgCell.formula shouldBe defined
    avgCell.formula.get shouldBe "AVERAGE(A1:B1)"
    avgCell.value shouldBe defined // Should have evaluated value

    // Check IF formula
    val complexCell = formulaCells(2)
    complexCell.cellType shouldBe "FORMULA"
    complexCell.formula shouldBe defined
    complexCell.formula.get shouldBe "IF(A1>B1,A1,B1)"
    complexCell.value shouldBe defined // Should have evaluated value
  }
}