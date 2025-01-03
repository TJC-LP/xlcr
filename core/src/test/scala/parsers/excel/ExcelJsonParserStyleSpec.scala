package com.tjclp.xlcr
package parsers.excel

import utils.{FileUtils, TestExcelHelpers}

import org.apache.poi.ss.usermodel.{BorderStyle, Font}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.FileOutputStream
import scala.util.{Success, Using}

class ExcelJsonParserStyleSpec extends AnyFlatSpec with Matchers {

  "ExcelJsonParser" should "preserve cell styles" in {
    FileUtils.withTempFile("test", ".xlsx") { path =>
      TestExcelHelpers.createStyledExcel(path)
      val result = ExcelJsonParser.extractContent(path)
      result shouldBe a[Success[_]]

      val content = new String(result.get.data)
      content should include("\"style\"")
      content should include("\"foregroundColor\"")
      content should include("\"pattern\"")
    }
  }

  it should "handle font styles" in {
    FileUtils.withTempFile("test", ".xlsx") { path =>
      val workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()
      val sheet = workbook.createSheet("Fonts")
      val row = sheet.createRow(0)
      val cell = row.createCell(0)

      cell.setCellValue("Test Font")
      val style = workbook.createCellStyle()
      val font = workbook.createFont()
      font.setBold(true)
      font.setItalic(true)
      font.setUnderline(Font.U_SINGLE)
      style.setFont(font)
      cell.setCellStyle(style)

      Using.resource(new FileOutputStream(path.toFile)) { fos =>
        workbook.write(fos)
      }
      workbook.close()

      val result = ExcelJsonParser.extractContent(path)
      val content = new String(result.get.data)
      content should include("\"font\"")
      content should include("\"bold\" : true")
      content should include("\"italic\" : true")
      content should include("\"underline\"")
    }
  }

  it should "capture border styles" in {
    FileUtils.withTempFile("test", ".xlsx") { path =>
      val workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()
      val sheet = workbook.createSheet("Borders")
      val row = sheet.createRow(0)
      val cell = row.createCell(0)

      cell.setCellValue("Bordered Cell")
      val style = workbook.createCellStyle()
      style.setBorderTop(BorderStyle.THIN)
      style.setBorderBottom(BorderStyle.THICK)
      style.setBorderLeft(BorderStyle.MEDIUM)
      style.setBorderRight(BorderStyle.DOUBLE)
      cell.setCellStyle(style)

      Using.resource(new FileOutputStream(path.toFile)) { fos =>
        workbook.write(fos)
      }
      workbook.close()

      val result = ExcelJsonParser.extractContent(path)
      val content = new String(result.get.data)
      content should include("\"borderTop\" : \"THIN\"")
      content should include("\"borderBottom\" : \"THICK\"")
      content should include("\"borderLeft\" : \"MEDIUM\"")
      content should include("\"borderRight\" : \"DOUBLE\"")
    }
  }
}