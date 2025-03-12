package com.tjclp.xlcr
package compression

import compression.models.{CellInfo, SheetGrid}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class InvertedIndexTranslatorSpec extends AnyFlatSpec with Matchers {

  "InvertedIndexTranslator" should "correctly translate a simple grid to inverted index" in {
    // Create a simple grid with a few cells
    val cells = Map(
      (0, 0) -> CellInfo(0, 0, "Header1"),
      (0, 1) -> CellInfo(0, 1, "Header2"),
      (1, 0) -> CellInfo(1, 0, "Value1"),
      (1, 1) -> CellInfo(1, 1, "Value2")
    )
    val grid = SheetGrid(cells, 2, 2)

    // Translate without coordinate preservation
    val config = SpreadsheetLLMConfig(preserveOriginalCoordinates = false)
    val result = InvertedIndexTranslator.translate(grid, config)

    // Verify results
    result.size shouldBe 4 // 4 unique values
    result("Header1") shouldBe Left("A1")
    result("Header2") shouldBe Left("B1")
    result("Value1") shouldBe Left("A2")
    result("Value2") shouldBe Left("B2")
  }

  it should "merge cells with the same content into ranges" in {
    // Create a grid with repeated values
    val cells = Map(
      (0, 0) -> CellInfo(0, 0, "Same"),
      (0, 1) -> CellInfo(0, 1, "Same"),
      (0, 2) -> CellInfo(0, 2, "Same"),
      (1, 0) -> CellInfo(1, 0, "Different"),
      (1, 1) -> CellInfo(1, 1, "Different")
    )
    val grid = SheetGrid(cells, 2, 3)

    val config = SpreadsheetLLMConfig(preserveOriginalCoordinates = false)
    val result = InvertedIndexTranslator.translate(grid, config)

    // Verify that same values are merged into a range
    result.size shouldBe 2 // 2 unique values
    result("Same") shouldBe Left("A1:C1") // Horizontal range
    result("Different") shouldBe Left("A2:B2") // Horizontal range
  }

  it should "merge cells with the same content while respecting original coordinates" in {
    // Create a grid with repeated values that have been remapped from original coordinates
    val cells = Map(
      // Three cells with the same value "Repeated" in row 10, original columns 5, 6, 7
      (0, 0) -> CellInfo(0, 0, "Repeated", originalRow = Some(10), originalCol = Some(5)),
      (0, 1) -> CellInfo(0, 1, "Repeated", originalRow = Some(10), originalCol = Some(6)),
      (0, 2) -> CellInfo(0, 2, "Repeated", originalRow = Some(10), originalCol = Some(7)),

      // Two cells with the same value "Header" in row 9, original columns 5, 6
      (1, 0) -> CellInfo(1, 0, "Header", originalRow = Some(9), originalCol = Some(5)),
      (1, 1) -> CellInfo(1, 1, "Header", originalRow = Some(9), originalCol = Some(6))
    )
    val grid = SheetGrid(cells, 2, 3)

    // Apply translation while preserving original coordinates
    val config = SpreadsheetLLMConfig(preserveOriginalCoordinates = true)
    val result = InvertedIndexTranslator.translate(grid, config)

    // Verify that cells are properly merged based on their original coordinates
    result.size shouldBe 2 // 2 unique values
    result("Repeated") shouldBe Left("F11:H11") // Original row 10+1, cols 5-7 (F-H) -> F11:H11
    result("Header") shouldBe Left("F10:G10") // Original row 9+1, cols 5-6 (F-G) -> F10:G10
  }

  it should "preserve original coordinates when they are provided" in {
    // Create a grid with remapped coordinates
    val cells = Map(
      (0, 0) -> CellInfo(0, 0, "Header", originalRow = Some(2), originalCol = Some(0)), // Original coordinates
      (0, 1) -> CellInfo(0, 1, "Value", originalRow = Some(2), originalCol = Some(1)) // Original coordinates
    )
    val grid = SheetGrid(cells, 1, 2)

    // Test with coordinate preservation enabled (default)
    val configEnabled = SpreadsheetLLMConfig(preserveOriginalCoordinates = true)
    val resultWithPreservation = InvertedIndexTranslator.translate(grid, configEnabled)

    // Should use the original coordinates directly
    resultWithPreservation("Header") shouldBe Left("A3") // Original position (2,0) -> A3 (1-indexed)
    resultWithPreservation("Value") shouldBe Left("B3") // Original position (2,1) -> B3 (1-indexed)

    // Test with coordinate preservation disabled
    val configDisabled = SpreadsheetLLMConfig(preserveOriginalCoordinates = false)
    val resultWithoutPreservation = InvertedIndexTranslator.translate(grid, configDisabled)

    // Should use internal coordinates when preservation disabled
    resultWithoutPreservation("Header") shouldBe Left("A1") // Internal position (0,0) -> A1
    resultWithoutPreservation("Value") shouldBe Left("B1") // Internal position (0,1) -> B1
  }

  it should "correctly handle coordinate preservation for cells with large shifts" in {
    // Create a grid with cells that have been shifted significantly from their original positions
    val cells = Map(
      (0, 0) -> CellInfo(0, 0, "ShiftedValue", originalRow = Some(30), originalCol = Some(0)) // Large shift of 30 rows
    )
    val grid = SheetGrid(cells, 1, 1)

    // Test with coordinates preservation enabled
    val config = SpreadsheetLLMConfig(preserveOriginalCoordinates = true)
    val result = InvertedIndexTranslator.translate(grid, config)

    // Should use the original coordinates
    result("ShiftedValue") shouldBe Left("A31") // Original (30,0) -> A31
  }

  it should "handle various original coordinate scenarios correctly" in {
    // Test case 1: Standard remapping from different locations
    val cells = Map(
      (0, 0) -> CellInfo(0, 0, "Data", originalRow = Some(20), originalCol = Some(5))
    )
    val grid = SheetGrid(cells, 1, 1)

    // Apply translation with coordinate preservation
    val config = SpreadsheetLLMConfig(preserveOriginalCoordinates = true)
    val result = InvertedIndexTranslator.translate(grid, config)

    // Should use original coordinates directly
    result("Data") shouldBe Left("F21") // Original (20,5) -> F21

    // Test case 2: Very large coordinate shift
    val originalRow = 100
    val originalCol = 10
    val complexCells = Map(
      (1, 1) -> CellInfo(1, 1, "Complex case", originalRow = Some(originalRow), originalCol = Some(originalCol))
    )
    val complexGrid = SheetGrid(complexCells, 2, 2)

    val complexResult = InvertedIndexTranslator.translate(complexGrid, config)

    // The A1 notation for (100,10) is K101
    complexResult("Complex case") shouldBe Left("K101") // Original (100,10) -> K101
  }

  "CellAddress" should "convert correctly to A1 notation" in {
    val address1 = InvertedIndexTranslator.CellAddress(0, 0)
    address1.toA1Notation shouldBe "A1"

    val address2 = InvertedIndexTranslator.CellAddress(9, 2)
    address2.toA1Notation shouldBe "C10"

    val address3 = InvertedIndexTranslator.CellAddress(99, 26)
    address3.toA1Notation shouldBe "AA100"
  }

  "CellRange" should "convert correctly to A1 notation" in {
    val range = InvertedIndexTranslator.CellRange(0, 0, 9, 2)
    range.toA1Notation shouldBe "A1:C10"

    val range2 = InvertedIndexTranslator.CellRange(5, 1, 5, 3)
    range2.toA1Notation shouldBe "B6:D6" // Single row range

    val range3 = InvertedIndexTranslator.CellRange(2, 2, 8, 2)
    range3.toA1Notation shouldBe "C3:C9" // Single column range
  }
}