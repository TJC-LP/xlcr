package com.tjclp.xlcr
package compression.models

import compression.AnchorExtractor.Dimension
import models.excel.CellData

import scala.collection.mutable

/**
 * The type of cohesion that created a CohesionRegion
 */
enum CohesionType:
  case Format       // Based on formatting cues like borders, colors, etc
  case Content      // Based on content relationships
  case Merged       // Based on merged cells
  case Formula      // Based on formula relationships
  case Border       // Based on border patterns
  case Pivot        // Part of a pivot table
  case ForcedLayout // Based on layout patterns that suggest strong cohesion

/**
 * Represents a cohesion region within a sheet - an area where cells should be kept together
 * due to their relationship, formatting, or other structural cues.
 * 
 * @param topRow Row index of the top of the region
 * @param bottomRow Row index of the bottom of the region
 * @param leftCol Column index of the left side of the region
 * @param rightCol Column index of the right side of the region
 * @param cohesionType The type of cohesion that caused this region to be created
 * @param strength Relative strength of the cohesion (higher means stronger relationship)
 */
case class CohesionRegion(
  topRow: Int, 
  bottomRow: Int, 
  leftCol: Int, 
  rightCol: Int,
  cohesionType: CohesionType,
  strength: Int = 1
) {
  /** Width of the region */
  def width: Int = rightCol - leftCol + 1
  
  /** Height of the region */
  def height: Int = bottomRow - topRow + 1
  
  /** Area of the region in cells */
  def area: Int = width * height
  
  /** Check if this region contains the given cell coordinates */
  def contains(row: Int, col: Int): Boolean =
    row >= topRow && row <= bottomRow && col >= leftCol && col <= rightCol
    
  /** Check if this region fully contains another region */
  def contains(other: CohesionRegion): Boolean =
    topRow <= other.topRow && bottomRow >= other.bottomRow &&
    leftCol <= other.leftCol && rightCol >= other.rightCol
    
  /** Check if this region contains another table region */
  def contains(tableRegion: TableRegion): Boolean =
    topRow <= tableRegion.topRow && bottomRow >= tableRegion.bottomRow &&
    leftCol <= tableRegion.leftCol && rightCol >= tableRegion.rightCol
    
  /** Check if this region overlaps with another cohesion region */
  def overlaps(other: CohesionRegion): Boolean =
    !(rightCol < other.leftCol || leftCol > other.rightCol ||
      bottomRow < other.topRow || topRow > other.bottomRow)
      
  /** Check if this region overlaps with a table region */
  def overlaps(tableRegion: TableRegion): Boolean =
    !(rightCol < tableRegion.leftCol || leftCol > tableRegion.rightCol ||
      bottomRow < tableRegion.topRow || topRow > tableRegion.bottomRow)
      
  /** 
   * Sophisticated overlap check with support for directional exceptions
   * @param other Region to check for overlap with
   * @param exceptForward If true, don't count as overlap if this region fully contains other
   * @param exceptBackward If true, don't count as overlap if other region fully contains this
   * @return True if regions overlap under the given constraints
   */
  def overlapsWith(other: TableRegion, exceptForward: Boolean = false, exceptBackward: Boolean = false): Boolean = {
    // Skip if this fully contains other and we're excepting forward containment
    if (exceptForward && this.contains(other)) return false
    
    // Skip if other fully contains this and we're excepting backward containment
    if (exceptBackward && other.contains(this.toTableRegion())) return false
    
    // Standard overlap check
    overlaps(other)
  }
  
  /** Convert this cohesion region to a table region */
  def toTableRegion(anchorRows: Set[Int] = Set.empty, anchorCols: Set[Int] = Set.empty): TableRegion =
    TableRegion(topRow, bottomRow, leftCol, rightCol, anchorRows, anchorCols)
}

/**
 * Represents a formula dependency relationship
 * 
 * @param sourceCell The cell containing the formula
 * @param targetCells The cells that the formula references
 */
case class FormulaRelationship(
  sourceCell: (Int, Int),
  targetCells: Set[(Int, Int)]
)

/**
 * Represents a graph of formula dependencies
 * 
 * @param relationships Map from cell coordinates to the cells it references
 */
case class FormulaGraph(
  relationships: Map[(Int, Int), Set[(Int, Int)]]
) {
  // Create the inverse mapping (cells referenced by other cells)
  lazy val inverseRelationships: Map[(Int, Int), Set[(Int, Int)]] = {
    val result = mutable.Map[(Int, Int), mutable.Set[(Int, Int)]]()
    
    for ((source, targets) <- relationships; target <- targets) {
      val sources = result.getOrElseUpdate(target, mutable.Set.empty)
      sources.add(source)
    }
    
    result.map { case (k, v) => k -> v.toSet }.toMap
  }
  
  /** Get all cells that are connected to the given cell through formulas in either direction */
  def getConnectedCells(cell: (Int, Int)): Set[(Int, Int)] = {
    val visited = mutable.Set[(Int, Int)]()
    val toVisit = mutable.Queue[(Int, Int)](cell)
    
    while (toVisit.nonEmpty) {
      val current = toVisit.dequeue()
      if (!visited.contains(current)) {
        visited.add(current)
        
        // Add cells referenced by current cell
        val targets = relationships.getOrElse(current, Set.empty)
        for (target <- targets if !visited.contains(target)) {
          toVisit.enqueue(target)
        }
        
        // Add cells that reference current cell
        val sources = inverseRelationships.getOrElse(current, Set.empty)
        for (source <- sources if !visited.contains(source)) {
          toVisit.enqueue(source)
        }
      }
    }
    
    visited.toSet - cell
  }
  
  /** Find all strongly connected components (clusters of cells that reference each other) */
  def findConnectedComponents(): List[Set[(Int, Int)]] = {
    val allCells = relationships.keySet ++ inverseRelationships.keySet
    val visited = mutable.Set[(Int, Int)]()
    val components = mutable.ListBuffer[Set[(Int, Int)]]()
    
    for (cell <- allCells if !visited.contains(cell)) {
      val component = getConnectedCells(cell) + cell
      components += component
      visited ++= component
    }
    
    components.toList
  }
}

/**
 * Cell information used for anchor analysis.
 *
 * @param row                The 0-based row index
 * @param col                The 0-based column index
 * @param value              The cell content as a string
 * @param isBold             Whether the cell has bold formatting
 * @param isFormula          Whether the cell contains a formula
 * @param isNumeric          Whether the cell contains numeric content
 * @param isDate             Whether the cell contains a date
 * @param isEmpty            Whether the cell is empty
 * @param isFillerContent    Whether the cell contains only filler/placeholder content
 * @param hasTopBorder       Whether the cell has a top border
 * @param hasBottomBorder    Whether the cell has a bottom border
 * @param hasLeftBorder      Whether the cell has a left border
 * @param hasRightBorder     Whether the cell has a right border 
 * @param hasFillColor       Whether the cell has background fill color
 * @param textLength         The length of the cell text (for ratio calculations)
 * @param alphabetRatio      The ratio of alphabet characters to total length
 * @param numberRatio        The ratio of numeric characters to total length
 * @param spCharRatio        The ratio of special characters to total length
 * @param numberFormatString The Excel number format string if available
 * @param originalRow        The original row index before remapping (for debugging)
 * @param originalCol        The original column index before remapping (for debugging)
 * @param cellData           The original CellData that this cell was derived from
 */
case class CellInfo(
                     row: Int,
                     col: Int,
                     value: String,
                     isBold: Boolean = false,
                     isFormula: Boolean = false,
                     isNumeric: Boolean = false,
                     isDate: Boolean = false,
                     isEmpty: Boolean = false,
                     isFillerContent: Boolean = false,
                     hasTopBorder: Boolean = false,
                     hasBottomBorder: Boolean = false,
                     hasLeftBorder: Boolean = false,
                     hasRightBorder: Boolean = false,
                     hasFillColor: Boolean = false,
                     textLength: Int = 0,
                     alphabetRatio: Double = 0.0,
                     numberRatio: Double = 0.0,
                     spCharRatio: Double = 0.0,
                     numberFormatString: Option[String] = None,
                     originalRow: Option[Int] = None,
                     originalCol: Option[Int] = None,
                     cellData: Option[CellData] = None
                   ):
  /**
   * Determines if this cell is effectively empty (either truly empty or just filler content)
   * 
   * @return true if the cell is empty or contains only filler content
   */
  def isEffectivelyEmpty: Boolean = isEmpty || isFillerContent

/**
 * Information about a table detected in the grid
 *
 * @param topRow     The top row index of the table
 * @param bottomRow  The bottom row index of the table
 * @param leftCol    The leftmost column index of the table
 * @param rightCol   The rightmost column index of the table
 * @param anchorRows Set of row indices that are anchors within this table
 * @param anchorCols Set of column indices that are anchors within this table
 */
case class TableRegion(
                        topRow: Int,
                        bottomRow: Int,
                        leftCol: Int,
                        rightCol: Int,
                        anchorRows: Set[Int],
                        anchorCols: Set[Int]
                      ):
  def area: Int = width * height

  def width: Int = rightCol - leftCol + 1

  def height: Int = bottomRow - topRow + 1

  /** Get all rows in this table region */
  def allRows: Set[Int] = (topRow to bottomRow).toSet

  /** Get all columns in this table region */
  def allCols: Set[Int] = (leftCol to rightCol).toSet
  
  /** Check if this region contains the given cell coordinates */
  def contains(row: Int, col: Int): Boolean =
    row >= topRow && row <= bottomRow && col >= leftCol && col <= rightCol
    
  /** Check if this region fully contains another region */
  def contains(other: TableRegion): Boolean =
    topRow <= other.topRow && bottomRow >= other.bottomRow &&
    leftCol <= other.leftCol && rightCol >= other.rightCol
    
  /** Check if this region contains another region with some tolerance for boundary differences */
  def containsWithTolerance(other: TableRegion, tolerance: Int): Boolean =
    (topRow - tolerance) <= other.topRow && (bottomRow + tolerance) >= other.bottomRow &&
    (leftCol - tolerance) <= other.leftCol && (rightCol + tolerance) >= other.rightCol
    
  /** Check if this region overlaps with another region */
  def overlaps(other: TableRegion): Boolean =
    !(rightCol < other.leftCol || leftCol > other.rightCol ||
      bottomRow < other.topRow || topRow > other.bottomRow)
      
  /** 
   * Sophisticated overlap check with support for directional exceptions
   * @param other Region to check for overlap with
   * @param exceptForward If true, don't count as overlap if this region fully contains other
   * @param exceptBackward If true, don't count as overlap if other region fully contains this
   * @return True if regions overlap under the given constraints
   */
  def overlapsWith(other: TableRegion, exceptForward: Boolean = false, exceptBackward: Boolean = false): Boolean = {
    // Skip if this fully contains other and we're excepting forward containment
    if (exceptForward && this.contains(other)) return false
    
    // Skip if other fully contains this and we're excepting backward containment
    if (exceptBackward && other.contains(this)) return false
    
    // Standard overlap check
    overlaps(other)
  }

/**
 * Represents a spreadsheet grid for anchor extraction.
 */
case class SheetGrid(
                      cells: Map[(Int, Int), CellInfo],
                      rowCount: Int,
                      colCount: Int
                    ):
  /**
   * Get all cells in a specific row or column based on dimension.
   */
  def getCells(dim: Dimension, index: Int): Seq[CellInfo] = dim match
    case Dimension.Row => getRow(index)
    case Dimension.Column => getCol(index)

  /**
   * Get all cells in a specific row.
   */
  def getRow(row: Int): Seq[CellInfo] =
    (0 until colCount).flatMap(col => cells.get((row, col)))

  /**
   * Get all cells in a specific column.
   */
  def getCol(col: Int): Seq[CellInfo] =
    (0 until rowCount).flatMap(row => cells.get((row, col)))

  /**
   * Get dimension count (rowCount or colCount).
   */
  def getDimCount(dim: Dimension): Int = dim match
    case Dimension.Row => rowCount
    case Dimension.Column => colCount

  /**
   * Filter the grid to only include cells in the specified rows and columns.
   */
  def filterToKeep(rowsToKeep: Set[Int], colsToKeep: Set[Int]): SheetGrid =
    val filteredCells = cells.filter { case ((r, c), _) =>
      rowsToKeep.contains(r) && colsToKeep.contains(c)
    }
    SheetGrid(filteredCells, rowCount, colCount)

  /**
   * Remap coordinates to close gaps after pruning.
   * This maintains logical structure while creating a more compact representation.
   */
  def remapCoordinates(): SheetGrid =
    // Create new row and column indices that are continuous
    val sortedRows = cells.keys.map(_._1).toSeq.distinct.sorted
    val sortedCols = cells.keys.map(_._2).toSeq.distinct.sorted

    val rowMap = sortedRows.zipWithIndex.toMap
    val colMap = sortedCols.zipWithIndex.toMap

    // Remap each cell to its new coordinates
    val remappedCells = cells.map { case ((oldRow, oldCol), cellInfo) =>
      val newRow = rowMap(oldRow)
      val newCol = colMap(oldCol)

      // Store original row/col in the cell info for later debugging
      val originalRow = cellInfo.originalRow.getOrElse(oldRow)
      val originalCol = cellInfo.originalCol.getOrElse(oldCol)

      // Important: We need to update both the map key AND the CellInfo's internal coordinates
      (newRow, newCol) -> cellInfo.copy(
        row = newRow,
        col = newCol,
        originalRow = Some(originalRow),
        originalCol = Some(originalCol)
      )
    }

    SheetGrid(remappedCells, sortedRows.size, sortedCols.size)
    
  /**
   * Checks if the given cell coordinates are within the valid bounds of the grid
   */
  def isInBounds(row: Int, col: Int): Boolean =
    row >= 0 && row < rowCount && col >= 0 && col < colCount
    
  /**
   * Extracts formula references from the grid
   * @return Map from cell coordinates to the cells they reference through formulas
   */
  def extractFormulaReferences(): Map[(Int, Int), Set[(Int, Int)]] = {
    cells.filter { case (_, cell) => cell.isFormula }
      .map { case (coords, cell) => 
        coords -> cell.cellData.flatMap(_.formula)
          .map(parseFormulaReferences)
          .getOrElse(Set.empty[(Int, Int)])
      }
  }
  
  /**
   * Parse an Excel formula to extract all cell references
   * @param formula The Excel formula as a string (e.g. "=SUM(A1:B2)")
   * @return Set of cell coordinates referenced by the formula
   */
  private def parseFormulaReferences(formula: String): Set[(Int, Int)] = {
    // A simple regex-based parser for demonstration
    // A production parser would need to handle complex Excel formula syntax
    val cellRefPattern = """([A-Z]+)(\d+)""".r
    val refs = mutable.Set[(Int, Int)]()
    
    // Extract single cell references like A1, B2, etc.
    for (matched <- cellRefPattern.findAllMatchIn(formula)) {
      val col = matched.group(1)
      val row = matched.group(2)
      val colIndex = colNameToIndex(col)
      val rowIndex = row.toInt - 1 // Convert from 1-based to 0-based
      if (isInBounds(rowIndex, colIndex)) {
        refs.add((rowIndex, colIndex))
      }
    }
    
    refs.toSet
  }
  
  /**
   * Convert an Excel column name to a 0-based index (A->0, B->1, Z->25, AA->26, etc.)
   */
  private def colNameToIndex(colName: String): Int = {
    colName.foldLeft(0) { (acc, c) =>
      acc * 26 + (c.toUpper - 'A' + 1)
    } - 1
  }