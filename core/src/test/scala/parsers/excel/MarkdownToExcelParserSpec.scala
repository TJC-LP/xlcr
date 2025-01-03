package com.tjclp.xlcr
package parsers.excel

import org.apache.poi.ss.usermodel.{WorkbookFactory, CellType}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import utils.FileUtils

import java.nio.file.{Files, Path}
import scala.util.{Failure, Success}

class MarkdownToExcelParserSpec extends AnyFlatSpec with Matchers {

  behavior of "MarkdownToExcelParser"

  it should "convert a single-sheet markdown to Excel" in {
    FileUtils.withTempFile("test", ".md") { markdownPath =>
      val markdown =
        """# Sheet1
          |
          || &lt;sub&gt;row&lt;/sub&gt;&lt;sup&gt;column&lt;/sup&gt; | A | B |
          ||--:|:--|:--|
          ||   1 | VALUE:``Hello``&lt;br&gt;REF:``Sheet1!A1`` | VALUE:``World``&lt;br&gt;REF:``Sheet1!B1`` |
          ||   2 | VALUE:``42``&lt;br&gt;TYPE:``NUMERIC`` | VALUE:``3.14``&lt;br&gt;TYPE:``NUMERIC`` |
          |""".stripMargin

      Files.write(markdownPath, markdown.getBytes)

      val result = MarkdownToExcelParser.extractContent(markdownPath)
      result shouldBe a[Success[_]]

      val excelBytes = result.get.data
      FileUtils.withTempFile("output", ".xlsx") { xlsxPath =>
        Files.write(xlsxPath, excelBytes)
        val workbook = WorkbookFactory.create(xlsxPath.toFile)
        val sheet = workbook.getSheet("Sheet1")
        sheet should not be null

        // Check row 0, cell 0 => "Hello"
        val row0 = sheet.getRow(0)
        row0 should not be null
        row0.getCell(0).getStringCellValue shouldBe "Hello"
        row0.getCell(1).getStringCellValue shouldBe "World"

        // Check row 1 => numeric data
        val row1 = sheet.getRow(1)
        row1 should not be null
        row1.getCell(0).getCellType shouldBe CellType.NUMERIC
        row1.getCell(0).getNumericCellValue shouldBe 42.0
        row1.getCell(1).getCellType shouldBe CellType.NUMERIC
        row1.getCell(1).getNumericCellValue shouldBe 3.14 +- 0.0001

        workbook.close()
      }
    }
  }

  it should "handle multi-sheet markdown properly" in {
    FileUtils.withTempFile("testMulti", ".md") { markdownPath =>
      val markdown =
        """# FirstSheet
          |
          || &lt;sub&gt;row&lt;/sub&gt;&lt;sup&gt;column&lt;/sup&gt; | A | B |
          ||--:|:--|:--|
          ||   1 | VALUE:``Hello``&lt;br&gt;REF:``FirstSheet!A1`` | VALUE:``World``&lt;br&gt;REF:``FirstSheet!B1`` |
          |
          |# SecondSheet
          |
          || &lt;sub&gt;row&lt;/sub&gt;&lt;sup&gt;column&lt;/sup&gt; | A |
          ||--:|:--|
          ||   1 | VALUE:``Foo``&lt;br&gt;REF:``SecondSheet!A1`` |
          ||   2 | VALUE:``Bar``&lt;br&gt;REF:``SecondSheet!A2`` |
          |""".stripMargin

      Files.write(markdownPath, markdown.getBytes)

      val result = MarkdownToExcelParser.extractContent(markdownPath)
      result shouldBe a[Success[_]]

      val excelBytes = result.get.data
      FileUtils.withTempFile("outputMulti", ".xlsx") { xlsxPath =>
        Files.write(xlsxPath, excelBytes)
        val workbook = WorkbookFactory.create(xlsxPath.toFile)
        workbook.getNumberOfSheets shouldBe 2

        val firstSheet = workbook.getSheet("FirstSheet")
        firstSheet should not be null
        firstSheet.getRow(0).getCell(0).getStringCellValue shouldBe "Hello"
        firstSheet.getRow(0).getCell(1).getStringCellValue shouldBe "World"

        val secondSheet = workbook.getSheet("SecondSheet")
        secondSheet should not be null
        secondSheet.getRow(0).getCell(0).getStringCellValue shouldBe "Foo"
        secondSheet.getRow(1).getCell(0).getStringCellValue shouldBe "Bar"

        workbook.close()
      }
    }
  }

  it should "handle partial or missing rows gracefully" in {
    FileUtils.withTempFile("testPartial", ".md") { markdownPath =>
      val markdown =
        """# MySheet
          |
          || &lt;sub&gt;row&lt;/sub&gt;&lt;sup&gt;column&lt;/sup&gt; | A | B | C |
          ||--:|:--|:--|:--|
          ||   1 | VALUE:``CellA1`` | VALUE:``CellB1`` | VALUE:``CellC1`` |
          ||   2 | VALUE:``Only A2`` |  |  |
          ||   3 | VALUE:``A3``&lt;br&gt;TYPE:``STRING`` | VALUE:``B3`` |  |
          |""".stripMargin

      Files.write(markdownPath, markdown.getBytes)

      val result = MarkdownToExcelParser.extractContent(markdownPath)
      result shouldBe a[Success[_]]
      val excelBytes = result.get.data

      FileUtils.withTempFile("partialOutput", ".xlsx") { xlsxPath =>
        Files.write(xlsxPath, excelBytes)
        val workbook = WorkbookFactory.create(xlsxPath.toFile)
        val sheet = workbook.getSheet("MySheet")
        sheet.getRow(0).getCell(0).getStringCellValue shouldBe "CellA1"
        sheet.getRow(0).getCell(1).getStringCellValue shouldBe "CellB1"
        sheet.getRow(0).getCell(2).getStringCellValue shouldBe "CellC1"

        sheet.getRow(1).getCell(0).getStringCellValue shouldBe "Only A2"
        sheet.getRow(1).getCell(1).getCellType shouldBe CellType.BLANK
        sheet.getRow(1).getCell(2).getCellType shouldBe CellType.BLANK

        sheet.getRow(2).getCell(0).getStringCellValue shouldBe "A3"
        sheet.getRow(2).getCell(1).getStringCellValue shouldBe "B3"
        sheet.getRow(2).getCell(2).getCellType shouldBe CellType.BLANK

        workbook.close()
      }
    }
  }

  it should "handle malformed markdown with no table lines" in {
    FileUtils.withTempFile("testMalformed", ".md") { markdownPath =>
      val markdown =
        """# MalformedSheet
          |
          |Some random lines without any table structure
          |
          |Another line
          |""".stripMargin

      Files.write(markdownPath, markdown.getBytes)

      val result = MarkdownToExcelParser.extractContent(markdownPath)
      result shouldBe a[Success[_]]
      val excelBytes = result.get.data

      // The workbook should exist, but have a sheet with 0 rows
      FileUtils.withTempFile("malformedOutput", ".xlsx") { xlsxPath =>
        Files.write(xlsxPath, excelBytes)
        val workbook = WorkbookFactory.create(xlsxPath.toFile)
        val sheet = workbook.getSheet("MalformedSheet")
        sheet should not be null
        sheet.getPhysicalNumberOfRows shouldBe 0
        workbook.close()
      }
    }
  }

  it should "convert numeric-like strings into numeric cells" in {
    FileUtils.withTempFile("testNumericStrings", ".md") { markdownPath =>
      val markdown =
        """# NumericStrings
          |
          || &lt;sub&gt;row&lt;/sub&gt;&lt;sup&gt;column&lt;/sup&gt; | A |
          ||--:|:--|
          ||   1 | VALUE:``100`` |
          ||   2 | VALUE:``-42.5`` |
          ||   3 | VALUE:``NotReallyANumber`` |
          |""".stripMargin

      Files.write(markdownPath, markdown.getBytes)

      val result = MarkdownToExcelParser.extractContent(markdownPath)
      result shouldBe a[Success[_]]
      val excelBytes = result.get.data

      FileUtils.withTempFile("numericStringsOutput", ".xlsx") { xlsxPath =>
        Files.write(xlsxPath, excelBytes)
        val workbook = WorkbookFactory.create(xlsxPath.toFile)
        val sheet = workbook.getSheet("NumericStrings")

        // row 0 => 100
        val row0Cell0 = sheet.getRow(0).getCell(0)
        row0Cell0.getCellType shouldBe CellType.NUMERIC
        row0Cell0.getNumericCellValue shouldBe 100.0

        // row 1 => -42.5
        val row1Cell0 = sheet.getRow(1).getCell(0)
        row1Cell0.getCellType shouldBe CellType.NUMERIC
        row1Cell0.getNumericCellValue shouldBe -42.5 +- 0.0001

        // row 2 => NotReallyANumber => should remain STRING
        val row2Cell0 = sheet.getRow(2).getCell(0)
        row2Cell0.getCellType shouldBe CellType.STRING
        row2Cell0.getStringCellValue shouldBe "NotReallyANumber"

        workbook.close()
      }
    }
  }

  it should "handle formula-like markdown gracefully as raw text" in {
    FileUtils.withTempFile("testFormulaLike", ".md") { markdownPath =>
      val markdown =
        """# FormulaSheet
          |
          || &lt;sub&gt;row&lt;/sub&gt;&lt;sup&gt;column&lt;/sup&gt; | A |
          ||--:|:--|
          ||   1 | VALUE:``=SUM(A1:B1)`` |
          ||   2 | VALUE:``42.0`` |
          |""".stripMargin

      Files.write(markdownPath, markdown.getBytes)

      val result = MarkdownToExcelParser.extractContent(markdownPath)
      result shouldBe a[Success[_]]
      val excelBytes = result.get.data

      FileUtils.withTempFile("formulaLikeOutput", ".xlsx") { xlsxPath =>
        Files.write(xlsxPath, excelBytes)
        val workbook = WorkbookFactory.create(xlsxPath.toFile)
        val sheet = workbook.getSheet("FormulaSheet")

        // row 0 => "=SUM(A1:B1)" but is just a string cell
        val row0Cell0 = sheet.getRow(0).getCell(0)
        row0Cell0.getCellType shouldBe CellType.STRING
        row0Cell0.getStringCellValue shouldBe "=SUM(A1:B1)"

        // row 1 => 42.0 => numeric
        val row1Cell0 = sheet.getRow(1).getCell(0)
        row1Cell0.getCellType shouldBe CellType.NUMERIC
        row1Cell0.getNumericCellValue shouldBe 42.0 +- 0.0001

        workbook.close()
      }
    }
  }
}