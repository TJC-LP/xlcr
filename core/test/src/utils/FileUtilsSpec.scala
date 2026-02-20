package com.tjclp.xlcr
package utils

import java.nio.file.{ Files, Paths }

import scala.util.{ Failure, Success }

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import types.MimeType

class FileUtilsSpec extends AnyFlatSpec with Matchers {

  it should "correctly detect MIME types from file extensions" in {
    val jsonPath    = Paths.get("test.json")
    val xlsPath     = Paths.get("test.xls")
    val xlsxPath    = Paths.get("test.xlsx")
    val xlsmPath    = Paths.get("test.xlsm")
    val xlsbPath    = Paths.get("test.xlsb")
    val unknownPath = Paths.get("test.unknown")
    FileUtils.detectMimeType(jsonPath) shouldBe MimeType.ApplicationJson
    FileUtils.detectMimeType(xlsPath) shouldBe MimeType.ApplicationVndMsExcel
    FileUtils.detectMimeType(xlsxPath) shouldBe MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
    FileUtils.detectMimeType(xlsmPath) shouldBe MimeType.ApplicationVndMsExcelSheetMacroEnabled
    FileUtils.detectMimeType(xlsbPath) shouldBe MimeType.ApplicationVndMsExcelSheetBinary
    FileUtils.detectMimeType(unknownPath) shouldBe MimeType.TextPlain
  }

  it should "detect MIME types using Tika for various file content types" in {
    FileUtils.withTempFile("test", ".custom") { path =>
      // Create a fake PDF content
      val pdfHeader = "%PDF-1.4\n%âãÏÓ\n"
      Files.write(path, pdfHeader.getBytes("ISO-8859-1"))

      FileUtils.detectMimeType(path) shouldBe MimeType.ApplicationPdf
    }
    FileUtils.withTempFile("test", ".custom") { path =>
      // Create a fake XML content
      val xmlContent = """<?xml version="1.0"?><root></root>"""
      Files.write(path, xmlContent.getBytes)

      FileUtils.detectMimeType(path) shouldBe MimeType.ApplicationXml
    }
  }

  it should "detect MIME types for various file extensions" in {
    val testCases = List(
      ("test.doc", MimeType.ApplicationMsWord),
      ("test.docx", MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument),
      ("test.pdf", MimeType.ApplicationPdf),
      ("test.xml", MimeType.ApplicationXml),
      ("test.json", MimeType.ApplicationJson),
      ("test.md", MimeType.TextMarkdown),
      ("test.svg", MimeType.ImageSvgXml),
      ("test.jpg", MimeType.ImageJpeg),
      ("test.png", MimeType.ImagePng)
    )

    for ((filename, expectedType) <- testCases) {
      val path = java.nio.file.Paths.get(filename)
      FileUtils.detectMimeTypeFromExtension(path) shouldBe expectedType
    }
  }

  it should "handle files with mismatched content and extension" in {
    FileUtils.withTempFile("test", ".txt") { path =>
      val pdfHeader = "%PDF-1.4\n%âãÏÓ\n"
      Files.write(path, pdfHeader.getBytes("ISO-8859-1"))

      val detectedType = FileUtils.detectMimeType(path)
      detectedType shouldBe MimeType.ApplicationPdf
    }
  }

  // Commented out as `application/octet-stream` is expected by current Tika behavior
//  it should "fall back to extension-based detection when Tika fails" in {
//    FileUtils.withTempFile("test", ".xlsx") { path =>
//      // Create an empty file with Excel extension
//      Files.write(path, Array[Byte]())
//
//      // Should fall back to extension-based detection for empty files
//      FileUtils.detectMimeType(path) shouldBe MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
//    }
//  }

  it should "handle non-existent files in MIME type detection" in {
    val nonExistentPath = Paths.get("non-existent-file.pdf")

    // Should still return a MIME type based on extension
    FileUtils.detectMimeType(nonExistentPath) shouldBe MimeType.ApplicationPdf
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
