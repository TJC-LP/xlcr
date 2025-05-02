package com.tjclp.xlcr
package splitters
package excel

import models.FileContent
import types.MimeType

import org.odftoolkit.odfdom.doc.OdfSpreadsheetDocument
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

class OdsSheetSplitterSpec extends AnyFlatSpec with Matchers {
  private val logger = LoggerFactory.getLogger(getClass)

  // Helper to create a test ODS file with multiple sheets
  private def createTestOdsFile(numSheets: Int = 3): Array[Byte] = {
    val odsDoc = OdfSpreadsheetDocument.newSpreadsheetDocument()

    // First sheet is already created
    val firstSheet = odsDoc.getTableList.get(0)
    firstSheet.setTableName("Sheet1")

    // Add some content to first sheet
    val cell1 = firstSheet.getCellByPosition(0, 0)
    cell1.setStringValue("Test data for Sheet1")

    // Create additional sheets
    for (i <- 1 until numSheets) {
      val sheet = org.odftoolkit.odfdom.doc.table.OdfTable.newTable(odsDoc)
      sheet.setTableName(s"Sheet${i + 1}")

      // Add some content
      val cell = sheet.getCellByPosition(0, 0)
      cell.setStringValue(s"Test data for Sheet${i + 1}")
    }

    // Save to byte array
    val baos = new ByteArrayOutputStream()
    odsDoc.save(baos)
    odsDoc.close()

    baos.toByteArray
  }

  "OdsSheetSplitter" should "handle empty or null input" in {
    // Create typed FileContent
    val emptyContent = FileContent(
      Array.emptyByteArray,
      MimeType.ApplicationVndOasisOpendocumentSpreadsheet
    )
      .asInstanceOf[FileContent[
        MimeType.ApplicationVndOasisOpendocumentSpreadsheet.type
      ]]
    val splitter = new OdsSheetSplitter()

    // This should not crash, but return the original content
    val result =
      splitter.split(emptyContent, SplitConfig(Some(SplitStrategy.Sheet)))
    result.size should be(1)
  }

  it should "split an ODS file into individual sheets" in {
    // Create test ODS with 3 sheets
    try {
      val odsBytes = createTestOdsFile(3)
      val content = FileContent(
        odsBytes,
        MimeType.ApplicationVndOasisOpendocumentSpreadsheet
      )
        .asInstanceOf[FileContent[
          MimeType.ApplicationVndOasisOpendocumentSpreadsheet.type
        ]]
      val splitter = new OdsSheetSplitter()

      // Split the ODS file
      val result =
        splitter.split(content, SplitConfig(Some(SplitStrategy.Sheet)))

      // Verify we got 3 chunks
      result.size should be(3)

      // Check each chunk
      for (i <- 0 until 3) {
        val chunk = result(i)
        chunk.label should be(s"Sheet${i + 1}")
        chunk.index should be(i)
        chunk.total should be(3)

        // Verify that each chunk has the right content by loading it back as an ODS
        val doc = OdfSpreadsheetDocument.loadDocument(
          new ByteArrayInputStream(chunk.content.data)
        )

        // Should have only one sheet
        doc.getTableList.size should be(1)

        // The sheet should have the correct name
        val sheet = doc.getTableList.get(0)
        sheet.getTableName should be(s"Sheet${i + 1}")

        // The sheet should have the correct content
        val cell = sheet.getCellByPosition(0, 0)
        cell.getStringValue should be(s"Test data for Sheet${i + 1}")

        doc.close()
      }
    } catch {
      case e: Exception =>
        logger.error("Test failed", e)
        fail(s"Test failed with exception: ${e.getMessage}")
    }
  }

  it should "return the original content when Sheet strategy is not requested" in {
    // Create test ODS
    val odsBytes = createTestOdsFile(2)
    val content =
      FileContent(odsBytes, MimeType.ApplicationVndOasisOpendocumentSpreadsheet)
        .asInstanceOf[FileContent[
          MimeType.ApplicationVndOasisOpendocumentSpreadsheet.type
        ]]
    val splitter = new OdsSheetSplitter()

    // Use a different strategy
    val result = splitter.split(content, SplitConfig(Some(SplitStrategy.Page)))

    // Should just return the original
    result.size should be(1)
    result.head.label should be("workbook")
    result.head.index should be(0)
    result.head.content.data should be(content.data)
  }

  it should "return the original content for unsupported MIME types" in {
    // Since OdsSheetSplitter now has a specific type bound, we have to test
    // this using the broader DocumentSplitter.split method

    // Define a test using the DocumentSplitter entry point
    def testTextContentViaSplitter(): Unit = {
      // Create a text content that should not be processed
      val textBytes = "test data".getBytes
      val textContent = FileContent(textBytes, MimeType.TextPlain)

      // Split using the facade
      val result = DocumentSplitter.split(
        textContent,
        SplitConfig(Some(SplitStrategy.Sheet))
      )

      // Should just return a single chunk with the original content
      result.size should be(1)
      result.head.content.data should be(textBytes)
    }

    testTextContentViaSplitter()
  }
}
