package com.tjclp.xlcr
package compression

import compression.models.CellInfo

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CompressionPipelineSpec extends AnyFlatSpec with Matchers {

  "CompressionPipeline" should "compress a sheet correctly with coordinate correction enabled" in {
    // Create a test sheet with sparse data
    val cells = Map(
      // Header row
      (0, 0) -> CellInfo(0, 0, "ID", isBold = true),
      (0, 1) -> CellInfo(0, 1, "Name", isBold = true),

      // Data row
      (1, 0) -> CellInfo(1, 0, "1", isNumeric = true),
      (1, 1) -> CellInfo(1, 1, "Item 1"),

      // Data row far away - will be remapped but should maintain reference to original position
      (10, 0) -> CellInfo(10, 0, "2", isNumeric = true),
      (10, 1) -> CellInfo(10, 1, "Item 2")
    )

    // Create sheet data that can be passed to compressWorkbook
    val sheetData = Map(
      "TestSheet" -> (cells.values.toSeq, 11, 2)
    )
    
    // Create a configuration with coordinate preservation enabled
    val config = SpreadsheetLLMConfig(
      preserveOriginalCoordinates = true,
      anchorThreshold = 0, // Lower threshold to force pruning of distant cells
      threads = 1 // Single thread for predictable testing
    )

    // Compress the workbook with a single sheet
    val compressedWorkbook = CompressionPipeline.compressWorkbook(
      "test.xlsx",
      sheetData,
      config
    )

    // Get the single sheet
    val compressedSheet = compressedWorkbook.sheets.head

    // Verify basic properties
    compressedSheet.name shouldBe "TestSheet"
    compressedSheet.originalRowCount shouldBe 11
    compressedSheet.originalColumnCount shouldBe 2

    // The cells should be compressed, but the references should maintain logical structure
    // "ID" and "Name" should be in the first row (if they were kept)
    if compressedSheet.content.contains("ID") then
      compressedSheet.content("ID") match {
        case Left(address) => address should startWith("A") // Should be in column A
        case Right(_) => fail("Expected a single cell reference")
      }

    // "1" should be below "ID" if both were kept
    if compressedSheet.content.contains("1") && compressedSheet.content.contains("ID") then
      val idAddress = compressedSheet.content("ID") match {
        case Left(address) => address
        case Right(_) => fail("Expected a single cell reference")
      }
      val idRow = idAddress.drop(1).toInt

      val oneAddress = compressedSheet.content("1") match {
        case Left(address) => address
        case Right(_) => fail("Expected a single cell reference")
      }
      val oneRow = oneAddress.drop(1).toInt

      oneRow shouldBe (idRow + 1) // The "1" should be in the row after "ID"

    // Check that metadata is present
    compressedSheet.compressionMetadata should not be empty
    compressedSheet.compressionMetadata.keys should contain("anchorExtraction")
  }

  it should "compress a workbook with multiple sheets correctly" in {
    // Create simple sheet data
    val sheet1Cells = Map(
      (0, 0) -> CellInfo(0, 0, "Sheet1 Header", isBold = true)
    )
    val sheet2Cells = Map(
      (0, 0) -> CellInfo(0, 0, "Sheet2 Header", isBold = true)
    )

    val sheetData = Map(
      "Sheet1" -> (sheet1Cells.values.toSeq, 1, 1),
      "Sheet2" -> (sheet2Cells.values.toSeq, 1, 1)
    )

    val config = SpreadsheetLLMConfig(
      preserveOriginalCoordinates = true,
      threads = 1 // Single thread for predictable testing
    )

    // Compress the workbook
    val compressedWorkbook = CompressionPipeline.compressWorkbook(
      "test.xlsx",
      sheetData,
      config
    )

    // Verify workbook properties
    compressedWorkbook.fileName shouldBe "test.xlsx"
    compressedWorkbook.sheets.size shouldBe 2
    compressedWorkbook.sheets.map(_.name).toSet shouldBe Set("Sheet1", "Sheet2")

    // Verify content of each sheet
    val sheet1 = compressedWorkbook.sheets.find(_.name == "Sheet1").get
    sheet1.content("Sheet1 Header") shouldBe Left("A1")

    val sheet2 = compressedWorkbook.sheets.find(_.name == "Sheet2").get
    sheet2.content("Sheet2 Header") shouldBe Left("A1")
  }

  it should "apply different compression strategies based on configuration" in {
    // Create a test sheet with data
    val cells = Map(
      (0, 0) -> CellInfo(0, 0, "Header", isBold = true),
      (1, 0) -> CellInfo(1, 0, "Value")
    )

    val sheetData = Map(
      "TestSheet" -> (cells.values.toSeq, 2, 1)
    )

    // Test with anchor extraction disabled
    val configNoAnchor = SpreadsheetLLMConfig(
      disableAnchorExtraction = true,
      preserveOriginalCoordinates = false,
      threads = 1
    )

    val workbookNoAnchor = CompressionPipeline.compressWorkbook(
      "test.xlsx",
      sheetData,
      configNoAnchor
    )

    val resultNoAnchor = workbookNoAnchor.sheets.head

    // All cells should be preserved when anchor extraction is disabled
    resultNoAnchor.content.size shouldBe 2
    resultNoAnchor.content.keys.toSet shouldBe Set("Header", "Value")

    // Test with format aggregation disabled
    val configNoFormat = SpreadsheetLLMConfig(
      disableFormatAggregation = true,
      preserveOriginalCoordinates = false,
      threads = 1
    )

    val workbookNoFormat = CompressionPipeline.compressWorkbook(
      "test.xlsx",
      sheetData,
      configNoFormat
    )

    val resultNoFormat = workbookNoFormat.sheets.head

    // Format aggregation metadata should reflect that it was disabled
    resultNoFormat.compressionMetadata("formatAggregation") shouldBe "disabled"
  }
}