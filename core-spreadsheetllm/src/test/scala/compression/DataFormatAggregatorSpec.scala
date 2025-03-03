package com.tjclp.xlcr
package compression

import compression.AnchorExtractor.{CellInfo, SheetGrid}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DataFormatAggregatorSpec extends AnyFlatSpec with Matchers {

  "DataFormatAggregator" should "aggregate numeric values correctly" in {
    // Create a map with numeric values
    val contentMap = Map(
      "100" -> Left("A1"),
      "200" -> Left("A2"),
      "300" -> Left("A3"),
      "Header" -> Left("B1")
    )
    
    // Create grid with cells that indicate the numeric types
    val cells = Map(
      (0, 0) -> CellInfo(0, 0, "100", isNumeric = true),
      (1, 0) -> CellInfo(1, 0, "200", isNumeric = true),
      (2, 0) -> CellInfo(2, 0, "300", isNumeric = true),
      (0, 1) -> CellInfo(0, 1, "Header", isNumeric = false)
    )
    val grid = SheetGrid(cells, 3, 2)
    
    // Run aggregation with format-based aggregation only (no semantic compression)
    val config = SpreadsheetLLMConfig(enableSemanticCompression = false)
    val result = DataFormatAggregator.aggregate(contentMap, grid, config)
    
    // The numeric values should be aggregated, but the text preserved
    result.size shouldBe 2
    result.keys should contain("Header")
    // The other key should be a format descriptor for the numeric values
    result.keys.find(_ != "Header").get should include("<Integer>")
  }
  
  it should "apply semantic compression when enabled" in {
    // Create a map with text values that could be semantically grouped
    val contentMap = Map(
      "United States" -> Left("A1"),
      "Canada" -> Left("A2"),
      "Mexico" -> Left("A3"),
      "France" -> Left("A4"),
      "Germany" -> Left("A5"),
      "John Smith" -> Left("B1"),
      "Jane Doe" -> Left("B2"),
      "Robert Jones" -> Left("B3"),
      "Product" -> Left("C1")
    )
    
    // Create grid with cells that all are text type
    val cells = Map(
      (0, 0) -> CellInfo(0, 0, "United States"),
      (1, 0) -> CellInfo(1, 0, "Canada"),
      (2, 0) -> CellInfo(2, 0, "Mexico"),
      (3, 0) -> CellInfo(3, 0, "France"),
      (4, 0) -> CellInfo(4, 0, "Germany"),
      (0, 1) -> CellInfo(0, 1, "John Smith"),
      (1, 1) -> CellInfo(1, 1, "Jane Doe"),
      (2, 1) -> CellInfo(2, 1, "Robert Jones"),
      (0, 2) -> CellInfo(0, 2, "Product")
    )
    val grid = SheetGrid(cells, 5, 3)
    
    // Run aggregation with semantic compression enabled
    val config = SpreadsheetLLMConfig(enableSemanticCompression = true)
    val result = DataFormatAggregator.aggregate(contentMap, grid, config)
    
    // The result should have fewer entries than the original
    result.size should be < contentMap.size
    
    // The country and name patterns should be detected and grouped
    val keys = result.keys.toSeq
    keys.exists(k => k.contains("Country")) shouldBe true 
    keys.exists(k => k.contains("Name")) shouldBe true
    
    // The non-pattern text should be preserved
    result.keys should contain("Product")
  }
  
  it should "not apply semantic compression when disabled" in {
    // Create a map with text values that could be semantically grouped
    val contentMap = Map(
      "United States" -> Left("A1"),
      "Canada" -> Left("A2"),
      "France" -> Left("A3")
    )
    
    // Create grid with cells that all are text type
    val cells = Map(
      (0, 0) -> CellInfo(0, 0, "United States"),
      (1, 0) -> CellInfo(1, 0, "Canada"),
      (2, 0) -> CellInfo(2, 0, "France")
    )
    val grid = SheetGrid(cells, 3, 1)
    
    // Run aggregation with semantic compression disabled
    val config = SpreadsheetLLMConfig(enableSemanticCompression = false)
    val result = DataFormatAggregator.aggregate(contentMap, grid, config)
    
    // The result should have the same number of entries as the original
    result.size shouldBe contentMap.size
    
    // All country names should be preserved individually
    result.keys should contain allOf("United States", "Canada", "France")
  }
}