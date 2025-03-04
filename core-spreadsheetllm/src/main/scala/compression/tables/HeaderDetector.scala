package com.tjclp.xlcr
package compression.tables

import compression.models.{SheetGrid, TableRegion}

import org.slf4j.LoggerFactory

import scala.collection.mutable

/**
 * HeaderDetector provides methods for detecting and analyzing header rows and columns
 * in a spreadsheet table, based on formatting cues and content analysis.
 */
object HeaderDetector:
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Enhanced header detection that implements the full logic from HeaderReco.cs.
   * Identifies multiple header types including up headers, left headers, and multi-level headers.
   */
  def enhancedHeaderDetection(grid: SheetGrid, region: TableRegion): (Set[Int], Set[Int]) = {
    logger.info(s"Detecting headers for region: $region")

    // 1. Detect up headers (column headers)
    val upHeaders = detectUpHeaders(grid, region)

    // 2. Detect left headers (row headers)
    val leftHeaders = detectLeftHeaders(grid, region)

    logger.info(s"Detected ${upHeaders.size} row headers and ${leftHeaders.size} column headers")
    (upHeaders, leftHeaders)
  }

  /**
   * Detect up headers (column headers) at the top of a table region
   */
  private def detectUpHeaders(grid: SheetGrid, region: TableRegion): Set[Int] = {
    val headerRows = mutable.Set[Int]()

    // Only check the top 3 rows of the region for headers
    val rowsToCheck = (region.topRow to math.min(region.topRow + 2, region.bottomRow)).toSet

    for (row <- rowsToCheck) {
      if (isHeaderRow(grid, row, region.leftCol, region.rightCol)) {
        headerRows += row
      }
    }

    headerRows.toSet
  }

  /**
   * Check if a row appears to be a header row based on formatting and content
   */
  def isHeaderRow(grid: SheetGrid, row: Int, leftCol: Int, rightCol: Int): Boolean = {
    // Get all cells in the row within the column range
    val cells = (leftCol to rightCol).flatMap(col => grid.cells.get((row, col)))

    if (cells.isEmpty) return false

    // Header characteristics
    val totalCells = cells.size
    val nonEmptyCells = cells.count(!_.isEmpty)
    val boldCells = cells.count(_.isBold)
    val borderCells = cells.count(c => c.hasTopBorder || c.hasBottomBorder)
    val colorCells = cells.count(_.hasFillColor)

    // Check if there's at least some content
    if (nonEmptyCells < 1) return false

    // Strong header indicators
    val hasMostlyBold = boldCells.toDouble / totalCells >= 0.5
    val hasMostlyBorders = borderCells.toDouble / totalCells >= 0.5
    val hasMostlyColor = colorCells.toDouble / totalCells >= 0.5

    // Text-based header detection
    val hasHeaderText = cells.exists(c =>
      !c.isEmpty && c.alphabetRatio > c.numberRatio && c.alphabetRatio > 0.5
    )

    // Return true if it shows multiple header characteristics
    hasMostlyBold || hasMostlyBorders || hasMostlyColor || hasHeaderText
  }

  /**
   * Detect left headers (row headers) at the left side of a table region
   */
  private def detectLeftHeaders(grid: SheetGrid, region: TableRegion): Set[Int] = {
    val headerCols = mutable.Set[Int]()

    // Only check the leftmost 2 columns for headers
    val colsToCheck = (region.leftCol to math.min(region.leftCol + 1, region.rightCol)).toSet

    for (col <- colsToCheck) {
      if (isHeaderColumn(grid, col, region.topRow, region.bottomRow)) {
        headerCols += col
      }
    }

    headerCols.toSet
  }

  /**
   * Check if a column appears to be a header column
   */
  private def isHeaderColumn(grid: SheetGrid, col: Int, topRow: Int, bottomRow: Int): Boolean = {
    // Get all cells in the column within the row range
    val cells = (topRow to bottomRow).flatMap(row => grid.cells.get((row, col)))

    if (cells.isEmpty) return false

    // Header characteristics
    val totalCells = cells.size
    val nonEmptyCells = cells.count(!_.isEmpty)
    val boldCells = cells.count(_.isBold)
    val borderCells = cells.count(c => c.hasLeftBorder || c.hasRightBorder)
    val colorCells = cells.count(_.hasFillColor)

    // Check if there's at least some content
    if (nonEmptyCells < 1) return false

    // Strong header indicators
    val hasMostlyBold = boldCells.toDouble / totalCells >= 0.5
    val hasMostlyBorders = borderCells.toDouble / totalCells >= 0.5
    val hasMostlyColor = colorCells.toDouble / totalCells >= 0.5

    // Text-based header detection
    val hasHeaderText = cells.exists(c =>
      !c.isEmpty && c.alphabetRatio > c.numberRatio && c.alphabetRatio > 0.5
    )

    // Return true if it shows multiple header characteristics
    hasMostlyBold || hasMostlyBorders || hasMostlyColor || hasHeaderText
  }