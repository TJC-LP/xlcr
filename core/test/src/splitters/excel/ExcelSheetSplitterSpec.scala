package com.tjclp.xlcr
package splitters
package excel

import java.io.ByteArrayOutputStream

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import models.FileContent
import types.MimeType

class ExcelSheetSplitterSpec extends AnyFlatSpec with Matchers {

  private def createTestWorkbook(sheetNames: String*): Array[Byte] = {
    val wb = new XSSFWorkbook()
    sheetNames.foreach(wb.createSheet)
    val baos = new ByteArrayOutputStream()
    wb.write(baos)
    wb.close()
    baos.toByteArray
  }

  "ExcelSheetSplitter" should "split a workbook into individual sheet files" in {
    // Build an in‑memory workbook with 3 sheets
    val bytes = createTestWorkbook("A", "B", "C")

    val fc = FileContent(
      bytes,
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
    )
    val chunks = DocumentSplitter.split(
      fc,
      SplitConfig(strategy = Some(SplitStrategy.Sheet))
    )

    (chunks should have).length(3)
    all(
      chunks.map(_.content.mimeType)
    ) shouldBe MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet

    chunks.foreach { c =>
      val wb = org.apache.poi.ss.usermodel.WorkbookFactory
        .create(new java.io.ByteArrayInputStream(c.content.data))
      try wb.getNumberOfSheets shouldBe 1
      finally wb.close()
    }
  }

  it should "limit sheets using chunkRange" in {
    val bytes = createTestWorkbook("Sheet1", "Sheet2", "Sheet3", "Sheet4", "Sheet5")
    val fc    = FileContent(bytes, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)

    // Test limiting to first 3 sheets
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Sheet),
      chunkRange = Some(0 until 3)
    )
    val chunks = DocumentSplitter.split(fc, config)

    chunks.length shouldBe 3
    chunks.map(_.label) shouldBe List("Sheet1", "Sheet2", "Sheet3")
    chunks.map(_.index) shouldBe List(0, 1, 2)
  }

  it should "extract middle range of sheets" in {
    val bytes = createTestWorkbook("A", "B", "C", "D", "E", "F")
    val fc    = FileContent(bytes, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)

    // Extract sheets 2-4 (0-indexed: B, C, D)
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Sheet),
      chunkRange = Some(1 until 4)
    )
    val chunks = DocumentSplitter.split(fc, config)

    chunks.length shouldBe 3
    chunks.map(_.label) shouldBe List("B", "C", "D")
    chunks.map(_.index) shouldBe List(1, 2, 3) // Original indices are preserved
  }

  it should "handle out-of-bounds ranges gracefully" in {
    val bytes = createTestWorkbook("Sheet1", "Sheet2", "Sheet3")
    val fc    = FileContent(bytes, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)

    // Range extends beyond available sheets
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Sheet),
      chunkRange = Some(1 until 10)
    )
    val chunks = DocumentSplitter.split(fc, config)

    chunks.length shouldBe 2
    chunks.map(_.label) shouldBe List("Sheet2", "Sheet3")
  }

  it should "return empty when range is completely out of bounds" in {
    val bytes = createTestWorkbook("Sheet1", "Sheet2")
    val fc    = FileContent(bytes, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)

    val config = SplitConfig(
      strategy = Some(SplitStrategy.Sheet),
      chunkRange = Some(5 until 10)
    )
    val chunks = DocumentSplitter.split(fc, config)

    chunks shouldBe empty
  }

  it should "extract single sheet using chunkRange" in {
    val bytes = createTestWorkbook("First", "Second", "Third", "Fourth")
    val fc    = FileContent(bytes, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)

    // Extract only the third sheet (index 2)
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Sheet),
      chunkRange = Some(2 until 3)
    )
    val chunks = DocumentSplitter.split(fc, config)

    chunks.length shouldBe 1
    chunks.head.label shouldBe "Third"
    chunks.head.index shouldBe 2 // Original index is preserved
    chunks.head.total shouldBe 4 // Original total is preserved
  }

  it should "work with deprecated pageRange for backward compatibility" in {
    val bytes = createTestWorkbook("A", "B", "C", "D")
    val fc    = FileContent(bytes, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)

    // Create config with chunkRange but access via pageRange
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Sheet),
      chunkRange = Some(0 until 2)
    )

    // Verify pageRange returns the same value
    config.pageRange shouldBe config.chunkRange
    config.pageRange shouldBe Some(0 until 2)

    val chunks = DocumentSplitter.split(fc, config)
    chunks.length shouldBe 2
    chunks.map(_.label) shouldBe List("A", "B")
  }
}
