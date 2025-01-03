package com.tjclp.xlcr
package parsers.excel

import utils.{FileUtils, TestExcelHelpers}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.FileOutputStream
import scala.util.{Success, Using}

class ExcelJsonParserBasicSpec extends AnyFlatSpec with Matchers {

  "ExcelJsonParser" should "extract basic cell values" in {
    FileUtils.withTempFile("test", ".xlsx") { path =>
      TestExcelHelpers.createBasicExcel(path)
      val result = ExcelJsonParser.extractContent(path)
      result shouldBe a[Success[_]]

      val content = new String(result.get.data)
      content should include("\"name\"")
      content should include("\"cells\"")
      content should include("\"value\"")
      content should include("Test Cell")
    }
  }

  it should "handle empty workbooks" in {
    FileUtils.withTempFile("test", ".xlsx") { path =>
      val workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()
      workbook.createSheet("Empty")

      Using.resource(new FileOutputStream(path.toFile)) { fos =>
        workbook.write(fos)
      }
      workbook.close()

      val result = ExcelJsonParser.extractContent(path)
      result shouldBe a[Success[_]]

      val content = new String(result.get.data)
      content should include("\"name\" : \"Empty\"")
      content should include("\"rowCount\" : 0")
      content should include("\"columnCount\" : 0")
    }
  }
}