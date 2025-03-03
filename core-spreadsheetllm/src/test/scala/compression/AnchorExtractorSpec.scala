package com.tjclp.xlcr
package compression

import compression.AnchorExtractor.{CellInfo, SheetGrid}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AnchorExtractorSpec extends AnyFlatSpec with Matchers {

  "AnchorExtractor" should "identify anchor rows and columns correctly" in {
    // Create a test grid with some patterns that should be identified as anchors
    val cells = Map(
      // Header row (heterogeneous with a mix of text and formatting)
      (0, 0) -> CellInfo(0, 0, "ID", isBold = true),
      (0, 1) -> CellInfo(0, 1, "Name", isBold = true),
      (0, 2) -> CellInfo(0, 2, "Value", isBold = true),
      
      // Data rows (homogeneous within columns)
      (1, 0) -> CellInfo(1, 0, "1", isNumeric = true),
      (1, 1) -> CellInfo(1, 1, "Item 1"),
      (1, 2) -> CellInfo(1, 2, "10.5", isNumeric = true),
      
      (2, 0) -> CellInfo(2, 0, "2", isNumeric = true),
      (2, 1) -> CellInfo(2, 1, "Item 2"),
      (2, 2) -> CellInfo(2, 2, "20.3", isNumeric = true)
    )
    val grid = SheetGrid(cells, 3, 3)
    
    // Identify anchors
    val (anchorRows, anchorCols) = AnchorExtractor.identifyAnchors(grid)
    
    // The header row should be identified as an anchor
    anchorRows should contain(0)
    
    // The ID column (0) and Value column (2) contain numbers, so they should be anchors
    anchorCols should contain(0)
    anchorCols should contain(2)
  }
  
  it should "preserve original coordinates when remapping" in {
    // Create a sparse grid with gaps
    val cells = Map(
      (0, 0) -> CellInfo(0, 0, "A1"),
      (0, 5) -> CellInfo(0, 5, "F1"),  // Gap in columns
      (10, 0) -> CellInfo(10, 0, "A11"), // Gap in rows
      (10, 5) -> CellInfo(10, 5, "F11")
    )
    val grid = SheetGrid(cells, 11, 6)
    
    // Remap coordinates to close gaps
    val remappedGrid = grid.remapCoordinates()
    
    // Should be compressed to a 2x2 grid
    remappedGrid.rowCount shouldBe 2
    remappedGrid.colCount shouldBe 2
    
    // Check that original coordinates are preserved
    val remappedA1 = remappedGrid.cells((0, 0))
    remappedA1.originalRow shouldBe Some(0)
    remappedA1.originalCol shouldBe Some(0)
    
    val remappedF11 = remappedGrid.cells((1, 1))
    remappedF11.originalRow shouldBe Some(10)
    remappedF11.originalCol shouldBe Some(5)
  }
  
  it should "filter and keep only specified rows and columns" in {
    // Create a test grid
    val cells = Map(
      (0, 0) -> CellInfo(0, 0, "A1"),
      (0, 1) -> CellInfo(0, 1, "B1"),
      (1, 0) -> CellInfo(1, 0, "A2"),
      (1, 1) -> CellInfo(1, 1, "B2")
    )
    val grid = SheetGrid(cells, 2, 2)
    
    // Keep only the first row
    val rowsToKeep = Set(0)
    val colsToKeep = Set(0, 1)
    
    val filteredGrid = grid.filterToKeep(rowsToKeep, colsToKeep)
    
    // Should only have 2 cells in 1 row and 2 columns
    filteredGrid.cells.size shouldBe 2
    filteredGrid.cells should contain key (0, 0)
    filteredGrid.cells should contain key (0, 1)
    filteredGrid.cells should not contain key (1, 0)
    filteredGrid.cells should not contain key (1, 1)
  }
  
  it should "extract anchors and prune unnecessary cells" in {
    // Create a test grid with some clear anchors
    val cells = Map(
      // Header row (clear anchor)
      (0, 0) -> CellInfo(0, 0, "ID", isBold = true),
      (0, 1) -> CellInfo(0, 1, "Name", isBold = true),
      (0, 2) -> CellInfo(0, 2, "Value", isBold = true),
      
      // Data rows
      (1, 0) -> CellInfo(1, 0, "1", isNumeric = true),
      (1, 1) -> CellInfo(1, 1, "Item 1"),
      (1, 2) -> CellInfo(1, 2, "10.5", isNumeric = true),
      
      // More data, further away
      (10, 0) -> CellInfo(10, 0, "10", isNumeric = true),
      (10, 1) -> CellInfo(10, 1, "Item 10"),
      (10, 2) -> CellInfo(10, 2, "100.5", isNumeric = true)
    )
    val grid = SheetGrid(cells, 11, 3)
    
    // Extract with threshold of 1
    val extractedGrid = AnchorExtractor.extract(grid, 1)
    
    // Row 0 is an anchor, so rows 0-1 should be kept
    // Row 10 is too far from any anchor with threshold 1, so should be pruned
    extractedGrid.cells.map { case ((r, _), _) => r }.toSet should contain (0)
    extractedGrid.cells.map { case ((r, _), _) => r }.toSet should contain (1)
    extractedGrid.cells.map { case ((r, _), _) => r }.toSet should not contain (10)
    
    // Ensure original coordinates are preserved
    val cell_0_0 = extractedGrid.cells.find { case ((r, c), _) => r == 0 && c == 0 }.map(_._2).get
    cell_0_0.originalRow shouldBe Some(0)
    cell_0_0.originalCol shouldBe Some(0)
  }
}