package com.tjclp.xlcr
package splitters
package excel

import models.FileContent
import types.MimeType

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.ByteArrayOutputStream

object ExcelSheetSplitterSpec extends AnyFlatSpec with Matchers {

  it should "split a workbook into individual sheet files" in {
    // Build an in‑memory workbook with 3 sheets
    val bytes: Array[Byte] = {
      val wb = new XSSFWorkbook()
      wb.createSheet("A")
      wb.createSheet("B")
      wb.createSheet("C")
      val baos = new ByteArrayOutputStream()
      wb.write(baos)
      wb.close()
      baos.toByteArray
    }

    val fc = FileContent(
      bytes,
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
    )
    val chunks = DocumentSplitter.split(
      fc,
      SplitConfig(strategy = Some(SplitStrategy.Sheet))
    )

    chunks should have length 3
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
}
