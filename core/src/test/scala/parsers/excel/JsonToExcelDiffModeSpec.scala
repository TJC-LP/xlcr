package com.tjclp.xlcr
package parsers.excel

import org.apache.poi.ss.usermodel.WorkbookFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import utils.FileUtils

import java.io.FileOutputStream
import java.nio.file.{Files, Path}
import scala.util.{Failure, Success, Using}

/**
 * This test suite verifies that JsonToExcelWriter can merge JSON
 * updates into an existing Excel file by using diff mode.
 *
 * We'll create an existing Excel file, then create a JSON file
 * with updates, and finally ensure the updates are merged properly
 * into the existing Excel when diffMode = true.
 */
class JsonToExcelDiffModeSpec extends AnyFlatSpec with Matchers {

  "JsonToExcelWriter" should "merge JSON updates into existing Excel file in diff mode" in {
    // 1) Create a base Excel file
    FileUtils.withTempFile("existingBase", ".xlsx") { existingXlsx =>
      createBaseExcelFile(existingXlsx)

      // 2) Create JSON that modifies or adds new data
      val tempDir = Files.createTempDirectory("json-diff-test")
      val updateJsonPath = tempDir.resolve("update.json")

      val updateJson =
        s"""[
           {
             "name": "TestSheet",
             "index": 0,
             "isHidden": false,
             "rowCount": 3,
             "columnCount": 2,
             "cells": [
               {
                 "referenceA1": "TestSheet!A2",
                 "rowIndex": 1,
                 "columnIndex": 0,
                 "address": "A2",
                 "cellType": "STRING",
                 "value": "UpdatedCell",
                 "formula": null,
                 "errorValue": null,
                 "comment": null,
                 "commentAuthor": null,
                 "hyperlink": null,
                 "dataFormat": null,
                 "formattedValue": null
               },
               {
                 "referenceA1": "TestSheet!B3",
                 "rowIndex": 2,
                 "columnIndex": 1,
                 "address": "B3",
                 "cellType": "STRING",
                 "value": "NewCell",
                 "formula": null,
                 "errorValue": null,
                 "comment": null,
                 "commentAuthor": null,
                 "hyperlink": null,
                 "dataFormat": null,
                 "formattedValue": null
               }
             ],
             "mergedRegions": [],
             "protectionStatus": false,
             "hasAutoFilter": false
           }
         ]""".stripMargin

      Files.write(updateJsonPath, updateJson.getBytes)

      // 3) Run JsonToExcelWriter in diff mode
      val result = JsonToExcelWriter.jsonToExcelFile(updateJsonPath, existingXlsx, diffMode = true)

      result match {
        case Failure(ex) =>
          fail(s"Diff mode update failed: ${ex.getMessage}")

        case Success(_) =>
          // 4) Verify that the existing Excel now has merged data
          val workbook = WorkbookFactory.create(existingXlsx.toFile)
          val sheet = workbook.getSheet("TestSheet")
          sheet should not be null

          // A2 should be updated
          val row1 = sheet.getRow(1)
          row1 should not be null
          val cellA2 = row1.getCell(0)
          cellA2 should not be null
          cellA2.getStringCellValue shouldBe "UpdatedCell"

          // B3 should be newly created
          val row2 = sheet.getRow(2)
          row2 should not be null
          val cellB3 = row2.getCell(1)
          cellB3 should not be null
          cellB3.getStringCellValue shouldBe "NewCell"

          workbook.close()
      }

      // Cleanup
      Files.deleteIfExists(updateJsonPath)
      Files.deleteIfExists(tempDir)
    }
  }

  /**
   * Creates a minimal Excel file with a single sheet named "TestSheet",
   * and one cell "A1" that has some initial value.
   */
  private def createBaseExcelFile(path: Path): Unit = {
    Using.resource(new FileOutputStream(path.toFile)) { fos =>
      val wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()
      val sheet = wb.createSheet("TestSheet")

      val row0 = sheet.createRow(0)
      val cellA1 = row0.createCell(0)
      cellA1.setCellValue("OriginalCell")

      wb.write(fos)
      wb.close()
    }
  }
}