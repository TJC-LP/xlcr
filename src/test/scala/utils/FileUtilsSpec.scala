package com.tjclp.xlcr
package utils

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import types.{Extension, FileType, MimeType}

import java.nio.file.{Files, Path, Paths}
import scala.util.{Success, Failure}

class FileUtilsSpec extends AnyFlatSpec with Matchers {

  "FileUtils" should "correctly detect MIME types from file extensions" in {
    val jsonPath = Paths.get("test.json")
    val xlsPath = Paths.get("test.xls")
    val xlsxPath = Paths.get("test.xlsx")
    val xlsmPath = Paths.get("test.xlsm")
    val xlsbPath = Paths.get("test.xlsb")
    val unknownPath = Paths.get("test.unknown")
    FileUtils.detectMimeType(jsonPath) shouldBe MimeType.ApplicationJson
    FileUtils.detectMimeType(xlsPath) shouldBe MimeType.ApplicationVndMsExcel
    FileUtils.detectMimeType(xlsxPath) shouldBe MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
    FileUtils.detectMimeType(xlsmPath) shouldBe MimeType.ApplicationVndMsExcel
    FileUtils.detectMimeType(xlsbPath) shouldBe MimeType.ApplicationVndMsExcel
    FileUtils.detectMimeType(unknownPath) shouldBe MimeType.TextPlain
  }

  it should "extract file extensions correctly" in {
    FileUtils.getExtension("test.json") shouldBe "json"
    FileUtils.getExtension("path/to/test.xlsx") shouldBe "xlsx"
    FileUtils.getExtension("noextension") shouldBe ""
    FileUtils.getExtension(".hiddenfile") shouldBe ""
    FileUtils.getExtension("multiple.dots.txt") shouldBe "txt"
  }

  it should "handle temporary file operations safely" in {
    val result = FileUtils.withTempFile("test", ".txt") { path =>
      Files.exists(path) shouldBe true
      "operation completed"
    }
    
    result shouldBe "operation completed"
  }

  it should "write and read JSON files correctly" in {
    FileUtils.withTempFile("test", ".json") { path =>
      val jsonContent = """{"test": "value"}"""
      
      FileUtils.writeJsonFile(path, jsonContent) shouldBe a[Success[_]]
      
      val readResult = FileUtils.readJsonFile(path)
      readResult shouldBe a[Success[_]]
      readResult.get shouldBe jsonContent
    }
  }

  it should "handle non-existent files appropriately" in {
    val nonExistentPath = Paths.get("non-existent-file.txt")
    
    FileUtils.fileExists(nonExistentPath) shouldBe false
    FileUtils.readBytes(nonExistentPath) shouldBe a[Failure[_]]
    FileUtils.readJsonFile(nonExistentPath) shouldBe a[Failure[_]]
  }

  it should "write bytes to file successfully" in {
    FileUtils.withTempFile("test", ".bin") { path =>
      val testData = "Hello, World!".getBytes
      
      val writeResult = FileUtils.writeBytes(path, testData)
      writeResult shouldBe a[Success[_]]
      
      val readBack = Files.readAllBytes(path)
      readBack shouldBe testData
    }
  }
}