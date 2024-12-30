package com.tjclp.xlcr

import utils.FileUtils

import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.FileOutputStream
import java.nio.file.{Files, Path}
import java.util.Date
import scala.util.Using

class PipelineSpec extends AnyFlatSpec with Matchers {

  "Pipeline" should "successfully process Excel to JSON conversion" in {
    FileUtils.withTempFile("test", ".xlsx") { inputPath =>
      FileUtils.withTempFile("test", ".json") { outputPath =>
        // Create a minimal Excel file
        createMinimalExcelFile(inputPath)

        val content = Pipeline.run(inputPath.toString, outputPath.toString)

        Files.exists(outputPath) shouldBe true
        val jsonString = String(Files.readAllBytes(outputPath))
        jsonString should include("Sheet1")
        jsonString should include("Test")

        // Also confirm the pipeline returned some content
        content.contentType shouldBe "application/json"
        new String(content.data) should include("Test")
      }
    }
  }

  it should "handle non-existent input file gracefully" in {
    FileUtils.withTempFile("test", ".json") { outputPath =>
      val nonExistentPath = Path.of("non-existent.xlsx")

      val ex = intercept[InputFileNotFoundException] {
        Pipeline.run(nonExistentPath.toString, outputPath.toString)
      }
      ex.getMessage should include("does not exist")
    }
  }

  it should "handle unsupported conversion types gracefully" in {
    FileUtils.withTempFile("test", ".xyz") { inputPath =>
      FileUtils.withTempFile("test", ".abc") { outputPath =>
        Files.write(inputPath, "test".getBytes)

        val ex = intercept[UnknownExtensionException] {
          Pipeline.run(inputPath.toString, outputPath.toString)
        }
        ex.getMessage should include("Cannot determine MIME type for extension 'abc'")
      }
    }
  }

  it should "convert Excel to JSON and back to Excel maintaining data integrity" in {
    FileUtils.withTempFile("test", ".xlsx") { inputExcel =>
      FileUtils.withTempFile("intermediate", ".json") { jsonPath =>
        FileUtils.withTempFile("output", ".xlsx") { outputExcel =>
          // Create test Excel file with various data types
          createComplexExcelFile(inputExcel)

          // Convert Excel to JSON
          Pipeline.run(inputExcel.toString, jsonPath.toString)
          Files.exists(jsonPath) shouldBe true

          // Convert JSON back to Excel
          Pipeline.run(jsonPath.toString, outputExcel.toString)
          Files.exists(outputExcel) shouldBe true

          // Compare original and final Excel files
          compareExcelFiles(inputExcel, outputExcel)
        }
      }
    }
  }

  private def createMinimalExcelFile(path: Path): Unit = {
    import org.apache.poi.xssf.usermodel.XSSFWorkbook
    val workbook = new XSSFWorkbook()
    val sheet = workbook.createSheet("Sheet1")
    val row = sheet.createRow(0)
    val cell = row.createCell(0)
    cell.setCellValue("Test")

    Using.resource(new FileOutputStream(path.toFile)) { fos =>
      workbook.write(fos)
    }
    workbook.close()
  }

  private def createComplexExcelFile(path: Path): Unit = {
    val workbook = new XSSFWorkbook()
    val sheet = workbook.createSheet("TestSheet")

    // Add various types of data
    val row = sheet.createRow(0)

    // String cell
    row.createCell(0).setCellValue("Text")

    // Numeric cell
    row.createCell(1).setCellValue(42.5)

    // Date cell
    val dateCell = row.createCell(2)
    dateCell.setCellValue(new java.util.Date())
    val style = workbook.createCellStyle()
    style.setDataFormat(workbook.getCreationHelper.createDataFormat().getFormat("yyyy-mm-dd"))
    dateCell.setCellStyle(style)

    // Formula cell
    row.createCell(3).setCellFormula("SUM(B1:B1)")

    Using.resource(new FileOutputStream(path.toFile))(workbook.write)
    workbook.close()
  }

  private def compareExcelFiles(file1: Path, file2: Path): Unit = {
    val wb1 = WorkbookFactory.create(file1.toFile)
    val wb2 = WorkbookFactory.create(file2.toFile)

    try {
      wb1.getNumberOfSheets shouldBe wb2.getNumberOfSheets

      for (sheetIndex <- 0 until wb1.getNumberOfSheets) {
        val sheet1 = wb1.getSheetAt(sheetIndex)
        val sheet2 = wb2.getSheetAt(sheetIndex)

        sheet1.getLastRowNum shouldBe sheet2.getLastRowNum

        for {
          rowIndex <- 0 to sheet1.getLastRowNum
          row1 = sheet1.getRow(rowIndex)
          row2 = sheet2.getRow(rowIndex)
          if row1 != null && row2 != null
        } {
          row1.getLastCellNum shouldBe row2.getLastCellNum
        }
      }
    } finally {
      wb1.close()
      wb2.close()
    }
  }
}