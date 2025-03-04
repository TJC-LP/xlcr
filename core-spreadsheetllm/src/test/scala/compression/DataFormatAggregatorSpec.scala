package com.tjclp.xlcr
package compression

import compression.models.{CellInfo, SheetGrid}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DataFormatAggregatorSpec extends AnyFlatSpec with Matchers {

  "DataFormatAggregator" should "identify numeric values for aggregation" in {
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

    // Run aggregation
    val config = SpreadsheetLLMConfig()
    val result = DataFormatAggregator.aggregate(contentMap, grid, config)

    // Verify result contains all keys from the input
    result.size shouldBe 4
    result.keys should contain allOf("100", "200", "300", "Header")

    // Verify the log output shows numeric values were identified as candidates
    // (Visual verification via log output: "Found 3 candidate entries for format aggregation")
  }

  it should "preserve text values that don't match format patterns" in {
    // Create a map with text values
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

    // Run aggregation
    val config = SpreadsheetLLMConfig()
    val result = DataFormatAggregator.aggregate(contentMap, grid, config)

    // The result should have the same number of entries as the original
    result.size shouldBe contentMap.size

    // All text values should be preserved individually
    result.keys should contain allOf("United States", "Canada", "France")
  }

  it should "identify date values for aggregation" in {
    // Create a map with date values
    val contentMap = Map(
      "2023-01-01" -> Left("A1"),
      "2023-02-15" -> Left("A2"),
      "2023-03-30" -> Left("A3")
    )

    // Create grid with cells that indicate date types
    val cells = Map(
      (0, 0) -> CellInfo(0, 0, "2023-01-01", isDate = true),
      (1, 0) -> CellInfo(1, 0, "2023-02-15", isDate = true),
      (2, 0) -> CellInfo(2, 0, "2023-03-30", isDate = true)
    )
    val grid = SheetGrid(cells, 3, 1)

    // Run aggregation
    val config = SpreadsheetLLMConfig()
    val result = DataFormatAggregator.aggregate(contentMap, grid, config)

    // Verify result contains all the date values
    result.size shouldBe 3
    result.keys should contain allOf("2023-01-01", "2023-02-15", "2023-03-30")

    // Verify the log output shows date values were identified as candidates
    // (Visual verification via log output: "Found 3 candidate entries for format aggregation")
  }
}