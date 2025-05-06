package com.tjclp.xlcr
package compression

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import compression.models.{ CellInfo, SheetGrid }

class DataFormatAggregatorSpec extends AnyFlatSpec with Matchers {

  "DataFormatAggregator" should "aggregate numeric values" in {
    // Create a map with numeric values
    val contentMap = Map(
      "100"    -> Left("A1"),
      "200"    -> Left("A2"),
      "300"    -> Left("A3"),
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

    // Verify the numbers are aggregated into a single representation
    result.size shouldBe 2

    // The text value should be preserved
    result.keys should contain("Header")

    // The numeric values should be aggregated
    result.keys should not contain allOf("100", "200", "300")

    // Should see an IntNum format entry instead
    result.keys.exists(_.contains("<Int")) shouldBe true

    // Verify the log output shows numeric values were identified as candidates
    // (Visual verification via log output: "Found 3 candidate entries for format aggregation")
  }

  it should "preserve text values that don't match format patterns" in {
    // Create a map with text values
    val contentMap = Map(
      "United States" -> Left("A1"),
      "Canada"        -> Left("A2"),
      "France"        -> Left("A3")
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
    (result.keys should contain).allOf("United States", "Canada", "France")
  }

  it should "aggregate date values even with single entries" in {
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

    // Each date should be replaced with a format descriptor
    result.keys should not contain allOf("2023-01-01", "2023-02-15", "2023-03-30")

    // Should see Date format entries instead
    result.keys.exists(_.contains("<Date")) shouldBe true

    // Should consolidate similar date formats
    result.size should be <= 3
  }

  it should "always aggregate single date entries" in {
    // Create a map with just a single date value
    val contentMap = Map(
      "2023-01-01" -> Left("A1")
    )

    // Create grid with a cell that indicates date type
    val cells = Map(
      (0, 0) -> CellInfo(0, 0, "2023-01-01", isDate = true)
    )
    val grid = SheetGrid(cells, 1, 1)

    // Run aggregation
    val config = SpreadsheetLLMConfig()
    val result = DataFormatAggregator.aggregate(contentMap, grid, config)

    // The date should be replaced with a format descriptor even though it's the only entry
    result.keys should not contain "2023-01-01"

    // Should see a Date format entry instead
    result.keys.exists(_.contains("<Date")) shouldBe true
  }
}
