package com.tjclp.xlcr
package compression

import compression.anchors.AnchorAnalyzer
import compression.models.SheetGrid
import compression.tables.TableDetector
import compression.utils.SheetGridUtils
import com.tjclp.xlcr.models.excel.SheetData
import org.slf4j.LoggerFactory

/**
 * AnchorExtractor identifies structural anchors in a spreadsheet and prunes away
 * less-informative cells. This is the first step in the SpreadsheetLLM compression pipeline.
 *
 * Anchors are heterogeneous rows and columns that define table boundaries and structural
 * elements (like headers, footers, or column labels). Cells that are far from any anchor
 * are pruned to reduce the spreadsheet size while preserving its structure.
 */
object AnchorExtractor {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Main entry point for the anchor extraction process.
   *
   * @param sheetData       The sheet data to process
   * @param anchorThreshold How many neighboring rows/columns to keep around anchors
   * @param config          Configuration options for table detection
   * @return A new grid with non-anchor cells pruned but original coordinates preserved
   */
  def extract(
    sheetData: SheetData,
    anchorThreshold: Int,
    config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()
  ): SheetData = {
    // Convert to internal SheetGrid format
    val grid = SheetGridUtils.fromSheetData(sheetData)

    // Extract anchors
    val prunedGrid = extract(grid, anchorThreshold, config)

    // Convert back to SheetData
    val prunedCells = prunedGrid.cells.values.toList.flatMap(_.cellData)
    sheetData.copy(cells = prunedCells)
  }

  /**
   * Main entry point for the anchor extraction process.
   *
   * @param grid            The sheet grid to process
   * @param anchorThreshold How many neighboring rows/columns to keep around anchors
   * @param config          Configuration options for table detection
   * @return A new grid with non-anchor cells pruned but original coordinates preserved
   */
  def extract(
    grid: SheetGrid,
    anchorThreshold: Int,
    config: SpreadsheetLLMConfig
  ): SheetGrid = {
    // Step 1: Identify structural anchors
    val (anchorRows, anchorCols) = AnchorAnalyzer.identifyAnchors(grid)

    // Step 2: Detect table regions (for logging purposes)
    val tableRegions = TableDetector.detectTableRegions(grid, anchorRows, anchorCols, config)

    // Log information about detected tables
    if (tableRegions.nonEmpty) {
      logger.info(s"Detected ${tableRegions.size} table regions in the sheet")
      tableRegions.zipWithIndex.foreach { case (table, idx) =>
        logger.info(f"  Table ${idx + 1}: (${table.topRow},${table.leftCol}) to (${table.bottomRow},${table.rightCol}) - ${table.width}x${table.height} cells")
      }
    }

    // Step 3: Expand anchors to include neighbors within threshold
    val (rowsToKeep, colsToKeep) = AnchorAnalyzer.expandAnchors(
      anchorRows, anchorCols, grid.rowCount, grid.colCount, anchorThreshold
    )

    // Step 4: Filter the grid to only keep cells in the anchor rows and columns
    // but preserve original coordinates (no remapping)
    val prunedGrid = grid.filterToKeep(rowsToKeep, colsToKeep)

    // Log compression statistics
    val originalCellCount = grid.rowCount * grid.colCount
    val retainedCellCount = prunedGrid.cells.size
    val compressionRatio = if (originalCellCount > 0) {
      originalCellCount.toDouble / retainedCellCount
    } else {
      1.0
    }

    logger.info(f"Anchor extraction: $originalCellCount cells -> $retainedCellCount cells ($compressionRatio%.2fx compression)")

    // Return the pruned grid directly without coordinate remapping
    prunedGrid
  }

  // Common data structures and types needed across modules
  sealed trait Dimension
  object Dimension {
    case object Row extends Dimension
    case object Column extends Dimension
  }
}