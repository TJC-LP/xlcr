package com.tjclp.xlcr
package compression

import compression.AnchorExtractor.{CellInfo, SheetGrid}
import models.excel.ExcelReference

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
    
    // Translate without coordinate correction
    val config = SpreadsheetLLMConfig(enableCoordinateCorrection = false)
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
    
    val config = SpreadsheetLLMConfig(enableCoordinateCorrection = false)
    val result = InvertedIndexTranslator.translate(grid, config)
    
    // Verify that same values are merged into a range
    result.size shouldBe 2 // 2 unique values
    result("Same") shouldBe Left("A1:C1") // Horizontal range
    result("Different") shouldBe Left("A2:B2") // Horizontal range
  }
  
  it should "apply coordinate correction when enabled and original coordinates differ" in {
    // Create a grid with remapped coordinates that would trigger correction
    val cells = Map(
      (0, 0) -> CellInfo(0, 0, "Header", originalRow = Some(2)), // Original row is +2 from current
      (0, 1) -> CellInfo(0, 1, "Value", originalRow = Some(2)) // Original row is +2 from current
    )
    val grid = SheetGrid(cells, 1, 2)
    
    // Test with correction enabled (default correction value is 2)
    val configEnabled = SpreadsheetLLMConfig(enableCoordinateCorrection = true)
    val resultWithCorrection = InvertedIndexTranslator.translate(grid, configEnabled)
    
    // Correction should add 2 to the row number
    resultWithCorrection("Header") shouldBe Left("A3") // A1 -> A3 (row 0+2+1)
    resultWithCorrection("Value") shouldBe Left("B3")  // B1 -> B3 (row 0+2+1)
    
    // Test with correction disabled
    val configDisabled = SpreadsheetLLMConfig(enableCoordinateCorrection = false)
    val resultWithoutCorrection = InvertedIndexTranslator.translate(grid, configDisabled)
    
    // No correction should be applied
    resultWithoutCorrection("Header") shouldBe Left("A1") // No adjustment
    resultWithoutCorrection("Value") shouldBe Left("B1")  // No adjustment
  }
  
  it should "use custom correction value when specified" in {
    // Create a grid with remapped coordinates
    val cells = Map(
      (0, 0) -> CellInfo(0, 0, "Value", originalRow = Some(5)) // Original row is +5 from current
    )
    val grid = SheetGrid(cells, 1, 1)
    
    // Test with different correction values
    val config1 = SpreadsheetLLMConfig(enableCoordinateCorrection = true, coordinateCorrectionValue = 1)
    val result1 = InvertedIndexTranslator.translate(grid, config1)
    result1("Value") shouldBe Left("A2") // Only +1 correction applied (0+1+1)
    
    val config5 = SpreadsheetLLMConfig(enableCoordinateCorrection = true, coordinateCorrectionValue = 5)
    val result5 = InvertedIndexTranslator.translate(grid, config5)
    result5("Value") shouldBe Left("A6") // +5 correction applied (0+5+1)
  }
  
  it should "handle the specific 'off by 2' issue for large spreadsheets" in {
    // Simulate the original issue with a large sheet that's been remapped
    val originalSheet = Map(
      (20, 5) -> CellInfo(20, 5, "Data at C22") // C22 in Excel = row 21, col 2 in 0-based
    )
    
    // After remapping, it gets compressed to (0,0) but retains original coordinates
    val remappedCells = Map(
      (0, 0) -> CellInfo(0, 0, "Data at C22", originalRow = Some(20), originalCol = Some(5))
    )
    val remappedGrid = SheetGrid(remappedCells, 1, 1)
    
    // Apply translation with correction
    val config = SpreadsheetLLMConfig(enableCoordinateCorrection = true, coordinateCorrectionValue = 2)
    val result = InvertedIndexTranslator.translate(remappedGrid, config)
    
    // The correction should yield C3 (original was C22)
    // Cell at (0,0) with +2 correction = (2,0) in 0-based = C3 in Excel notation
    result("Data at C22") shouldBe Left("A3") // Not C22 because we're adjusting the row only
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