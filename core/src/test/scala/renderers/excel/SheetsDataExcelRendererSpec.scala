package com.tjclp.xlcr
package renderers.excel

import models.FileContent
import models.excel.{CellData, CellDataStyle, FontData, SheetData, SheetsData}
import types.MimeType
import utils.TestExcelHelpers
import utils.excel.ExcelUtils

import org.apache.poi.ss.usermodel.{BorderStyle, CellType, FillPatternType}
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.ByteArrayInputStream
import java.nio.file.{Files, Path}
import scala.util.Using

class SheetsDataExcelRendererSpec extends AnyFlatSpec with Matchers {
  private val renderer = new SheetsDataExcelRenderer()

  "SheetsDataExcelRenderer" should "render basic sheet data correctly" in {
    val sheetData = SheetData(
      name = "Test Sheet",
      index = 0,
      cells = List(
        CellData(
          referenceA1 = "A1",
          cellType = "STRING",
          value = Some("Test"),
          formula = None,
          errorValue = None
        )
      )
    )
    
    val model = SheetsData(List(sheetData))
    val result = renderer.render(model)
    
    // Verify the output
    Using.resource(new ByteArrayInputStream(result.data)) { is =>
      val workbook = new XSSFWorkbook(is)
      workbook.getNumberOfSheets shouldBe 1
      val sheet = workbook.getSheetAt(0)
      sheet.getSheetName shouldBe "Test Sheet"
      
      val cell = sheet.getRow(0).getCell(0)
      cell.getStringCellValue shouldBe "Test"
      
      workbook.close()
    }
  }

  it should "render cell styles correctly" in {
    val style = CellDataStyle(
      backgroundColor = Some("#FF0000"),
      foregroundColor = Some("#00FF00"),
      pattern = Some("SOLID_FOREGROUND"),
      borderTop = Some("THIN")
    )
    
    val font = FontData(
      name = "Arial",
      size = Some(12),
      bold = true,
      italic = false
    )
    
    val sheetData = SheetData(
      name = "Styled Sheet",
      index = 0,
      cells = List(
        CellData(
          referenceA1 = "A1",
          cellType = "STRING",
          value = Some("Styled Text"),
          style = Some(style),
          font = Some(font)
        )
      )
    )
    
    val model = SheetsData(List(sheetData))
    val result = renderer.render(model)
    
    Using.resource(new ByteArrayInputStream(result.data)) { is =>
      val workbook = new XSSFWorkbook(is)
      val sheet = workbook.getSheetAt(0)
      val cell = sheet.getRow(0).getCell(0)
      
      val cellStyle = cell.getCellStyle
      cellStyle.getFillPattern shouldBe FillPatternType.SOLID_FOREGROUND
      cellStyle.getBorderTop shouldBe BorderStyle.THIN
      
      val cellFont = workbook.getFontAt(cellStyle.getFontIndex)
      cellFont.getFontName shouldBe "Arial"
      cellFont.getFontHeightInPoints shouldBe 12
      cellFont.getBold shouldBe true
      cellFont.getItalic shouldBe false
      
      workbook.close()
    }
  }

  it should "render formulas correctly" in {
    val sheetData = SheetData(
      name = "Formula Sheet",
      index = 0,
      cells = List(
        CellData(
          referenceA1 = "A1",
          cellType = "NUMERIC",
          value = Some("10")
        ),
        CellData(
          referenceA1 = "A2",
          cellType = "FORMULA",
          formula = Some("SUM(A1)")
        )
      )
    )
    
    val model = SheetsData(List(sheetData))
    val result = renderer.render(model)
    
    Using.resource(new ByteArrayInputStream(result.data)) { is =>
      val workbook = new XSSFWorkbook(is)
      val sheet = workbook.getSheetAt(0)
      
      val formulaCell = sheet.getRow(1).getCell(0)
      formulaCell.getCellType shouldBe CellType.FORMULA
      formulaCell.getCellFormula shouldBe "SUM(A1)"
      
      workbook.close()
    }
  }

  it should "handle merged regions" in {
    val sheetData = SheetData(
      name = "Merged Sheet",
      index = 0,
      cells = List(
        CellData(
          referenceA1 = "A1",
          cellType = "STRING",
          value = Some("Merged")
        )
      ),
      mergedRegions = List("A1:B2")
    )
    
    val model = SheetsData(List(sheetData))
    val result = renderer.render(model)
    
    Using.resource(new ByteArrayInputStream(result.data)) { is =>
      val workbook = new XSSFWorkbook(is)
      val sheet = workbook.getSheetAt(0)
      
      sheet.getNumMergedRegions shouldBe 1
      val mergedRegion = sheet.getMergedRegion(0)
      mergedRegion.formatAsString shouldBe "A1:B2"
      
      workbook.close()
    }
  }
}