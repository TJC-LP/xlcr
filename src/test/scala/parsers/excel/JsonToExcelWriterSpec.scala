package com.tjclp.xlcr
package parsers.excel

import org.apache.poi.ss.usermodel.WorkbookFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.PrintWriter
import java.nio.file.Files
import scala.util.{Failure, Success}

class JsonToExcelWriterSpec extends AnyFlatSpec with Matchers {

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
}