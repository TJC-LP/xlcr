package com.tjclp.xlcr

import utils.FileUtils
import utils.TestExcelHelpers

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
    TestExcelHelpers.createBasicExcel(path)
  }

  private def createComplexExcelFile(path: Path): Unit = {
    TestExcelHelpers.createMultiDataTypeExcel(path)
  }

  private def compareExcelFiles(file1: Path, file2: Path): Unit = {
    assert(TestExcelHelpers.compareExcelFiles(file1, file2))
  }
}