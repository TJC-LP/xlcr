package com.tjclp.xlcr
package parsers.excel

import org.apache.poi.ss.usermodel.{DataFormatter, WorkbookFactory}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.PrintWriter
import java.nio.file.{Files, Path}
import scala.util.{Failure, Success}
import utils.TestExcelHelpers

import com.tjclp.xlcr.models.excel.SheetData

class JsonToExcelWriterSpec extends AnyFlatSpec with Matchers {

  def compareResults(output: Path, expected: Path): Boolean = {
    TestExcelHelpers.compareExcelFiles(output, expected)
  }

  "JsonToExcelWriter" should "create an Excel file from a valid JSON input" in {
    // Create a temp directory for our files
    val tempDir = Files.createTempDirectory("xlcr-test")
    val inputJsonPath = tempDir.resolve("testData.json")
    val outputExcelPath = tempDir.resolve("output.xlsx")

    // Minimal JSON for a single sheet with 2 columns and 2 rows
    // We'll fill in the 'A1' and 'B2' cells with some data.
    val jsonString =
      s"""
         |{
         |  "name": "TestSheet",
         |  "index": 0,
         |  "isHidden": false,
         |  "rowCount": 2,
         |  "columnCount": 2,
         |  "cells": [
         |    {
         |      "referenceA1": "TestSheet!A1",
         |      "rowIndex": 0,
         |      "columnIndex": 0,
         |      "address": "A1",
         |      "cellType": "STRING",
         |      "value": "Hello",
         |      "formula": null,
         |      "errorValue": null,
         |      "comment": null,
         |      "commentAuthor": null,
         |      "hyperlink": null,
         |      "dataFormat": null,
         |      "formattedValue": null
         |    },
         |    {
         |      "referenceA1": "TestSheet!B2",
         |      "rowIndex": 1,
         |      "columnIndex": 1,
         |      "address": "B2",
         |      "cellType": "STRING",
         |      "value": "World",
         |      "formula": null,
         |      "errorValue": null,
         |      "comment": null,
         |      "commentAuthor": null,
         |      "hyperlink": null,
         |      "dataFormat": null,
         |      "formattedValue": null
         |    }
         |  ],
         |  "mergedRegions": [],
         |  "protectionStatus": false,
         |  "hasAutoFilter": false
         |}
         |""".stripMargin

    // Write the JSON to a file
    val writer = new PrintWriter(inputJsonPath.toFile)
    try {
      writer.write(jsonString)
    } finally {
      writer.close()
    }

    // Attempt to convert the JSON file to an Excel file
    val result = JsonToExcelWriter.jsonToExcelFile(inputJsonPath, outputExcelPath)
    result match {
      case Failure(ex) => fail(s"Failed to convert JSON to Excel: ${ex.getMessage}")
      case Success(_) =>
        // Verify that the Excel file was indeed created
        Files.exists(outputExcelPath) shouldBe true

        // We can open the Excel file using POI and check the contents
        val workbook = WorkbookFactory.create(outputExcelPath.toFile)
        val sheet = workbook.getSheet("TestSheet")
        sheet should not be null

        // Check the cell A1
        val row0 = sheet.getRow(0)
        row0 should not be null
        val cellA1 = row0.getCell(0)
        cellA1 should not be null
        cellA1.getStringCellValue shouldBe "Hello"

        // Check the cell B2
        val row1 = sheet.getRow(1)
        row1 should not be null
        val cellB2 = row1.getCell(1)
        cellB2 should not be null
        cellB2.getStringCellValue shouldBe "World"

        workbook.close()
    }
  }

  it should "preserve advanced formatting details in a round-trip" in {
    // We'll create JSON data with style and font details for a single sheet
    val tempDir = Files.createTempDirectory("xlcr-format-test")
    val inputJsonPath = tempDir.resolve("formatData.json")
    val outputExcelPath = tempDir.resolve("formatOutput.xlsx")
    val roundTripJsonPath = tempDir.resolve("roundTrip.json")

    // JSON with styling in the cells (foreground color, bold font, etc.)
    val jsonWithFormatting =
      s"""
         |{
         |  "name": "StyledSheet",
         |  "index": 0,
         |  "isHidden": false,
         |  "rowCount": 1,
         |  "columnCount": 2,
         |  "cells": [
         |    {
         |      "referenceA1": "StyledSheet!A1",
         |      "rowIndex": 0,
         |      "columnIndex": 0,
         |      "address": "A1",
         |      "cellType": "STRING",
         |      "value": "Styled",
         |      "style": {
         |        "foregroundColor": "#FFC0CB",
         |        "pattern": "SOLID_FOREGROUND",
         |        "borderTop": "THIN",
         |        "borderRight": "NONE",
         |        "borderBottom": "THICK",
         |        "borderLeft": "NONE"
         |      },
         |      "font": {
         |        "name": "Arial",
         |        "bold": true,
         |        "italic": false,
         |        "underline": 1,
         |        "strikeout": false,
         |        "rgbColor": "#FF0000"
         |      }
         |    },
         |    {
         |      "referenceA1": "StyledSheet!B1",
         |      "rowIndex": 0,
         |      "columnIndex": 1,
         |      "address": "B1",
         |      "cellType": "STRING",
         |      "value": "Cell",
         |      "style": {
         |        "foregroundColor": "#00FF00",
         |        "pattern": "SOLID_FOREGROUND"
         |      },
         |      "font": {
         |        "name": "Courier New",
         |        "bold": false,
         |        "italic": true,
         |        "underline": 0,
         |        "strikeout": true,
         |        "rgbColor": "#0000FF"
         |      }
         |    }
         |  ],
         |  "mergedRegions": [],
         |  "protectionStatus": false,
         |  "hasAutoFilter": false
         |}
         |""".stripMargin

    // Write the JSON to a file
    val writer = new PrintWriter(inputJsonPath.toFile)
    try {
      writer.write(jsonWithFormatting)
    } finally {
      writer.close()
    }

    // 1) Convert JSON -> Excel
    val writeResult = JsonToExcelWriter.jsonToExcelFile(inputJsonPath, outputExcelPath)
    writeResult match {
      case Failure(ex) => fail(s"Failed to convert JSON with formatting to Excel: ${ex.getMessage}")
      case Success(_) =>
        // 2) Now convert that Excel file back to JSON for comparison
        val parseResult = ExcelJsonParser.extractContent(outputExcelPath)
        parseResult match {
          case Failure(ex) =>
            fail(s"Failed to parse Excel back to JSON: ${ex.getMessage}")

          case Success(content) =>
            // Write the round-trip JSON to disk for inspection
            Files.write(roundTripJsonPath, content.data)

            // We'll parse the JSON back into SheetData objects
            val parsed = SheetData.fromJsonMultiple(new String(content.data))
            parsed match {
              case Left(err) =>
                fail(s"Failed to parse round-trip JSON: ${err.getMessage}")

              case Right(sheets) =>
                sheets should have size 1
                val sheet = sheets.head
                sheet.name shouldBe "StyledSheet"

                // We should have 2 cells in that single row
                sheet.cells should have size 2

                // Let's check the first cell's style
                val cellA1 = sheet.cells.find(_.address == "A1").get
                cellA1.value shouldBe Some("Styled")
                cellA1.font shouldBe defined
                cellA1.style shouldBe defined

                val fontA1 = cellA1.font.get
                fontA1.bold shouldBe true
                fontA1.italic shouldBe false
                fontA1.underline shouldBe Some(1.toByte)
                fontA1.rgbColor shouldBe Some("#FF0000")

                val styleA1 = cellA1.style.get
                styleA1.foregroundColor shouldBe Some("#FFC0CB")
                styleA1.pattern shouldBe Some("SOLID_FOREGROUND")
                styleA1.borderBottom shouldBe Some("THICK")

                // Check second cell
                val cellB1 = sheet.cells.find(_.address == "B1").get
                cellB1.value shouldBe Some("Cell")

                val fontB1 = cellB1.font.get
                fontB1.bold shouldBe false
                fontB1.italic shouldBe true
                fontB1.underline shouldBe Some(0.toByte)
                fontB1.strikeout shouldBe true
                fontB1.rgbColor shouldBe Some("#0000FF")

                val styleB1 = cellB1.style.get
                styleB1.foregroundColor shouldBe Some("#00FF00")
                styleB1.pattern shouldBe Some("SOLID_FOREGROUND")

              // If we get this far, the test passes (the cell formatting was preserved)
            }
        }
    }
  }

  it should "apply multiple data formats (accounting, currency, percentage, etc.)" in {
    val tempDir = Files.createTempDirectory("xlcr-format-check")
    val inputJsonPath = tempDir.resolve("dataFormats.json")
    val outputExcelPath = tempDir.resolve("formattedOutput.xlsx")

    // We'll create JSON that sets various dataFormat strings, including:
    // - Accounting style
    // - Percentage style
    // - Currency style
    // - General numeric
    // - etc.

    val jsonWithFormats =
      """
         |{
         |  "name": "DataFormats",
         |  "index": 0,
         |  "isHidden": false,
         |  "rowCount": 5,
         |  "columnCount": 1,
         |  "cells": [
         |    {
         |      "referenceA1": "DataFormats!A1",
         |      "rowIndex": 0,
         |      "columnIndex": 0,
         |      "address": "A1",
         |      "cellType": "NUMERIC",
         |      "value": "1234.567",
         |      "dataFormat": "_-$* #,##0.00_ ;_-$* #,##0.00_",
         |      "formattedValue": null
         |    },
         |    {
         |      "referenceA1": "DataFormats!A2",
         |      "rowIndex": 1,
         |      "columnIndex": 0,
         |      "address": "A2",
         |      "cellType": "NUMERIC",
         |      "value": "0.45",
         |      "dataFormat": "0.00%",
         |      "formattedValue": null
         |    },
         |    {
         |      "referenceA1": "DataFormats!A3",
         |      "rowIndex": 2,
         |      "columnIndex": 0,
         |      "address": "A3",
         |      "cellType": "NUMERIC",
         |      "value": "99999",
         |      "dataFormat": "General",
         |      "formattedValue": null
         |    },
         |    {
         |      "referenceA1": "DataFormats!A4",
         |      "rowIndex": 3,
         |      "columnIndex": 0,
         |      "address": "A4",
         |      "cellType": "NUMERIC",
         |      "value": "56.78",
         |      "dataFormat": "¥#,##0;[Red](¥#,##0)",
         |      "formattedValue": null
         |    },
         |    {
         |      "referenceA1": "DataFormats!A5",
         |      "rowIndex": 4,
         |      "columnIndex": 0,
         |      "address": "A5",
         |      "cellType": "NUMERIC",
         |      "value": "-123",
         |      "dataFormat": "$#,##0_);[Red]($#,##0)",
         |      "formattedValue": null
         |    }
         |  ],
         |  "mergedRegions": [],
         |  "protectionStatus": false,
         |  "hasAutoFilter": false
         |}
         |""".stripMargin

    Files.write(inputJsonPath, jsonWithFormats.getBytes)

    val writeResult = JsonToExcelWriter.jsonToExcelFile(inputJsonPath, outputExcelPath)
    writeResult match {
      case Failure(ex) => fail(s"Failed to write data formats to Excel: ${ex.getMessage}")
      case Success(_) =>
        // Let's check that the workbook was created
        Files.exists(outputExcelPath) shouldBe true

        // We'll confirm the data formats by reading back the generated file
        val workbook = WorkbookFactory.create(outputExcelPath.toFile)
        val sheet = workbook.getSheet("DataFormats")
        sheet should not be null

        val rowCount = sheet.getLastRowNum + 1
        rowCount shouldBe 5

        val dataFormatter = new DataFormatter()

        val accountingCell = sheet.getRow(0).getCell(0)
        val percentCell = sheet.getRow(1).getCell(0)
        val generalCell = sheet.getRow(2).getCell(0)
        val customCurrencyCell = sheet.getRow(3).getCell(0)
        val redCurrencyCell = sheet.getRow(4).getCell(0)

        // Checking the raw format strings from the cell style
        val accountingFormat = accountingCell.getCellStyle.getDataFormatString
        accountingFormat should include("$")
        accountingFormat should include("#,##0.00")
        percentCell.getCellStyle.getDataFormatString should include("%")
        generalCell.getCellStyle.getDataFormatString shouldBe "General"
        customCurrencyCell.getCellStyle.getDataFormatString should include("¥")
        redCurrencyCell.getCellStyle.getDataFormatString should include("Red")

        // Optionally, we can check the "Formatted" string from DataFormatter
        // We won't rely on exact content, just that it's not the raw "56.78" or "0.45", etc.
        val formattedAccounting = dataFormatter.formatCellValue(accountingCell)
        formattedAccounting should include("$")
        formattedAccounting should not be ("1234.567")
        dataFormatter.formatCellValue(percentCell) should not be ("0.45")

        workbook.close()
    }
  }
}