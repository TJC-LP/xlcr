package com.tjclp.xlcr
package compression

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import compression.anchors.AnchorAnalyzer
import compression.models.{ CellInfo, SheetGrid }

class AnchorExtractorSpec extends AnyFlatSpec with Matchers {

  "AnchorAnalyzer" should "identify anchor rows and columns correctly" in {
    // Create a test grid with some patterns that should be identified as anchors
    val cells = Map(
      // Header row (heterogeneous with a mix of text and formatting)
      (1, 0) -> CellInfo(1, 0, "ID", isBold = true),
      (1, 1) -> CellInfo(1, 1, "Name", isBold = true),
      (1, 2) -> CellInfo(1, 2, "Value", isBold = true),

      // Data rows (homogeneous within columns)
      (2, 0) -> CellInfo(2, 0, "1", isNumeric = true),
      (2, 1) -> CellInfo(2, 1, "Item 1"),
      (2, 2) -> CellInfo(2, 2, "10.5", isNumeric = true),
      (3, 0) -> CellInfo(3, 0, "2", isNumeric = true),
      (3, 1) -> CellInfo(3, 1, "Item 2"),
      (3, 2) -> CellInfo(3, 2, "20.3", isNumeric = true)
    )
    val grid = SheetGrid(cells, 4, 3) // Row count is now 4 (rows 1-3)

    // Identify anchors
    val (anchorRows, anchorCols) = AnchorAnalyzer.identifyAnchors(grid)

    // The header row should be identified as an anchor
    anchorRows should contain(1)

    // The ID column (0) and Value column (2) contain numbers, so they should be anchors
    anchorCols should contain(0)
    anchorCols should contain(2)
  }

  "SheetGrid" should "preserve original coordinates when remapping" in {
    // Create a sparse grid with gaps
    val cells = Map(
      (1, 0)  -> CellInfo(1, 0, "A1"),
      (1, 5)  -> CellInfo(1, 5, "F1"),   // Gap in columns
      (11, 0) -> CellInfo(11, 0, "A11"), // Gap in rows
      (11, 5) -> CellInfo(11, 5, "F11")
    )
    val grid = SheetGrid(cells, 12, 6) // Rows 1-11 (12 rows total)

    // Remap coordinates to close gaps
    val remappedGrid = grid.remapCoordinates()

    // Should be compressed to a 2x2 grid
    remappedGrid.rowCount shouldBe 2
    remappedGrid.colCount shouldBe 2

    // Check that original coordinates are preserved
    val remappedA1 = remappedGrid.cells((0, 0))
    remappedA1.originalRow shouldBe Some(1)
    remappedA1.originalCol shouldBe Some(0)

    val remappedF11 = remappedGrid.cells((1, 1))
    remappedF11.originalRow shouldBe Some(11)
    remappedF11.originalCol shouldBe Some(5)
  }

  it should "filter and keep only specified rows and columns" in {
    // Create a test grid
    val cells = Map(
      (1, 0) -> CellInfo(1, 0, "A1"),
      (1, 1) -> CellInfo(1, 1, "B1"),
      (2, 0) -> CellInfo(2, 0, "A2"),
      (2, 1) -> CellInfo(2, 1, "B2")
    )
    val grid = SheetGrid(cells, 3, 2) // Rows 1-2, Columns 0-1

    // Keep only the first row
    val rowsToKeep = Set(1)
    val colsToKeep = Set(0, 1)

    val filteredGrid = grid.filterToKeep(rowsToKeep, colsToKeep)

    // Should only have 2 cells in 1 row and 2 columns
    filteredGrid.cells.size shouldBe 2
    (filteredGrid.cells should contain).key(1, 0)
    (filteredGrid.cells should contain).key(1, 1)
    filteredGrid.cells should not contain key(2, 0)
    filteredGrid.cells should not contain key(2, 1)
  }

  "AnchorExtractor" should "extract anchors and prune unnecessary cells" in {
    // Create a test grid with some clear anchors
    val cells = Map(
      // Header row (clear anchor)
      (1, 0) -> CellInfo(1, 0, "ID", isBold = true),
      (1, 1) -> CellInfo(1, 1, "Name", isBold = true),
      (1, 2) -> CellInfo(1, 2, "Value", isBold = true),

      // Data rows
      (2, 0) -> CellInfo(2, 0, "1", isNumeric = true),
      (2, 1) -> CellInfo(2, 1, "Item 1"),
      (2, 2) -> CellInfo(2, 2, "10.5", isNumeric = true),

      // More data, further away
      (11, 0) -> CellInfo(11, 0, "10", isNumeric = true),
      (11, 1) -> CellInfo(11, 1, "Item 10"),
      (11, 2) -> CellInfo(11, 2, "100.5", isNumeric = true)
    )
    val grid = SheetGrid(cells, 12, 3) // Rows 1-11, Columns 0-2

    // Extract with threshold of 1
    val extractedGrid = AnchorExtractor.extract(grid, 1, SpreadsheetLLMConfig())

    // Verify that key rows are preserved
    // Row 1 is a header with bold text - definite anchor
    (extractedGrid.cells should contain).key(1, 0)

    // Row 2 is data
    (extractedGrid.cells should contain).key(2, 1)

    // Row 11 is preserved even though it's far from other data
    // This is because our anchor detection identified it as an anchor row itself
    // due to its heterogeneous content (mix of number in col 0, text in col 1, and number in col 2)
    (extractedGrid.cells should contain).key(11, 0)
  }
}
