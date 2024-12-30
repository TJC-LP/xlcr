package com.tjclp.xlcr
package parsers.excel

import types.MimeType
import utils.FileUtils

import org.apache.poi.ss.usermodel.{FillPatternType, IndexedColors}
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.util.{Success, Using}
import scala.xml.XML

class ExcelSvgParserSpec extends AnyFlatSpec with Matchers {

  "ExcelSvgParser" should "generate valid SVG from a simple Excel file" in {
    FileUtils.withTempFile("test", ".xlsx") { path =>
      createSimpleExcelFile(path)

      val result = ExcelSvgParser.extractContent(path)
      result shouldBe a[Success[_]]

      val content = result.get
      content.contentType shouldBe MimeType.ImageSvgXml.mimeType

      val svgString = new String(content.data, StandardCharsets.UTF_8)
      svgString should include("<?xml version=\"1.0\"")
      svgString should include("<svg")
      svgString should include("</svg>")

      // Verify it's valid XML
      noException should be thrownBy XML.loadString(svgString)
    }
  }

  it should "preserve font styles in generated SVG" in {
    FileUtils.withTempFile("test", ".xlsx") { path =>
      createStyledExcelFile(path)

      val result = ExcelSvgParser.extractContent(path)
      val svgString = new String(result.get.data, StandardCharsets.UTF_8)

      // Check for style definitions
      svgString should include("font-family")
      svgString should include("font-weight=\"bold\"")
      svgString should include("font-style=\"italic\"")

      // Check for colors
      svgString should include("fill=\"#")
    }
  }

  it should "handle multiple sheets" in {
    FileUtils.withTempFile("test", ".xlsx") { path =>
      createMultiSheetExcelFile(path)

      val result = ExcelSvgParser.extractContent(path)
      result shouldBe a[Success[_]]

      val svgString = new String(result.get.data, StandardCharsets.UTF_8)
      svgString should include("<!-- Sheet: Sheet1 -->")
      svgString should include("<!-- Sheet: Sheet2 -->")
    }
  }

  it should "fail gracefully with invalid input" in {
    FileUtils.withTempFile("test", ".txt") { path =>
      Files.write(path, "Not an Excel file".getBytes)

      val result = ExcelSvgParser.extractContent(path)
      result shouldBe a[scala.util.Failure[_]]
    }
  }

  it should "generate SVG with correct dimensions" in {
    FileUtils.withTempFile("test", ".xlsx") { path =>
      createSimpleExcelFile(path)

      val result = ExcelSvgParser.extractContent(path)
      val svgString = new String(result.get.data, StandardCharsets.UTF_8)

      // Check for width and height attributes
      svgString should include("""width="100"""") // Based on default colWidth
      svgString should include("""height="20"""") // Based on default rowHeight
    }
  }

  private def createSimpleExcelFile(path: Path): Unit = {
    val workbook = new XSSFWorkbook()
    val sheet = workbook.createSheet("Sheet1")
    val row = sheet.createRow(0)
    val cell = row.createCell(0)
    cell.setCellValue("Test Cell")

    Using.resource(new FileOutputStream(path.toFile)) { fos =>
      workbook.write(fos)
    }
    workbook.close()
  }

  private def createStyledExcelFile(path: Path): Unit = {
    val workbook = new XSSFWorkbook()
    val sheet = workbook.createSheet("Sheet1")
    val row = sheet.createRow(0)
    val cell = row.createCell(0)

    // Create and apply cell style
    val style = workbook.createCellStyle()
    val font = workbook.createFont()
    font.setBold(true)
    font.setItalic(true)
    font.setColor(IndexedColors.RED.getIndex)
    style.setFont(font)

    // Set background color
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND)
    style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex)

    cell.setCellStyle(style)
    cell.setCellValue("Styled Cell")

    Using.resource(new FileOutputStream(path.toFile)) { fos =>
      workbook.write(fos)
    }
    workbook.close()
  }

  private def createMultiSheetExcelFile(path: Path): Unit = {
    val workbook = new XSSFWorkbook()

    // Create Sheet1
    val sheet1 = workbook.createSheet("Sheet1")
    val row1 = sheet1.createRow(0)
    row1.createCell(0).setCellValue("Sheet 1 Cell")

    // Create Sheet2
    val sheet2 = workbook.createSheet("Sheet2")
    val row2 = sheet2.createRow(0)
    row2.createCell(0).setCellValue("Sheet 2 Cell")

    Using.resource(new FileOutputStream(path.toFile)) { fos =>
      workbook.write(fos)
    }
    workbook.close()
  }
}