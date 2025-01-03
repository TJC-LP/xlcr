package com.tjclp.xlcr
package parsers.excel

import utils.{FileUtils, TestExcelHelpers}

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.FileOutputStream
import scala.util.{Success, Using}

class ExcelJsonParserFormatSpec extends AnyFlatSpec with Matchers {

  "ExcelJsonParser" should "preserve number formats" in {
    FileUtils.withTempFile("test", ".xlsx") { path =>
      TestExcelHelpers.createMultiDataTypeExcel(path)
      val result = ExcelJsonParser.extractContent(path)
      result shouldBe a[Success[_]]

      val content = new String(result.get.data)
      content should include("\"cellType\" : \"NUMERIC\"")
      content should include("\"value\" : \"42.5\"")
      content should include("\"dataFormat\"")
    }
  }

  it should "handle custom date formats" in {
    FileUtils.withTempFile("test", ".xlsx") { path =>
      val workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()
      val sheet = workbook.createSheet("Dates")
      val row = sheet.createRow(0)
      val cell = row.createCell(0)

      cell.setCellValue(new java.util.Date())
      val style = workbook.createCellStyle()
      style.setDataFormat(workbook.getCreationHelper.createDataFormat().getFormat("yyyy-mm-dd"))
      cell.setCellStyle(style)

      Using.resource(new FileOutputStream(path.toFile)) { fos =>
        workbook.write(fos)
      }
      workbook.close()

      val result = ExcelJsonParser.extractContent(path)
      result shouldBe a[Success[_]]

      val content = new String(result.get.data)
      content should include("\"dataFormat\" : \"yyyy-mm-dd\"")
      content should include("\"formattedValue\"")
    }
  }

  it should "preserve currency formats" in {
    FileUtils.withTempFile("test", ".xlsx") { path =>
      val workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()
      val sheet = workbook.createSheet("Currency")
      val row = sheet.createRow(0)
      val cell = row.createCell(0)

      cell.setCellValue(123.45)
      val style = workbook.createCellStyle()
      style.setDataFormat(workbook.getCreationHelper.createDataFormat().getFormat("$#,##0.00"))
      cell.setCellStyle(style)

      Using.resource(new FileOutputStream(path.toFile)) { fos =>
        workbook.write(fos)
      }
      workbook.close()

      val result = ExcelJsonParser.extractContent(path)
      val content = new String(result.get.data)
      content should include("\"dataFormat\" : \"$#,##0.00\"")
    }
  }
}