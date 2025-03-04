package com.tjclp.xlcr
package compression

import compression.models.SheetGrid
import com.tjclp.xlcr.models.excel.ExcelReference
import com.tjclp.xlcr.utils.excel.ExcelUtils

import org.slf4j.LoggerFactory

/**
 * InvertedIndexTranslator converts the pruned spreadsheet grid into a compact
 * dictionary format that maps unique cell content to locations.
 *
 * This is the second step in the SpreadsheetLLM compression pipeline, applied after
 * AnchorExtractor has pruned away less-informative cells.
 */
object InvertedIndexTranslator:
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Converts a grid of cells into a dictionary mapping unique content to locations.
   * This inverted index approach dramatically reduces redundancy by grouping
   * cells with the same content and merging adjacent cells into ranges.
   *
   * @param grid   The sheet grid to process
   * @param config Configuration options including coordinate correction settings
   * @return A map from unique cell content to locations (either a single range or a list of addresses)
   */
  def translate(grid: SheetGrid, config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()): Map[String, Either[String, List[String]]] =
    if grid.cells.isEmpty then
      return Map.empty

    // Step 1: Group cells by their content to create the initial inverted index
    // Force stable iteration by converting to a sorted list first
    val contentToAddresses = grid.cells
      .toList                                  // Convert to list for stable handling
      .sortBy { case ((row, col), _) => (row, col) }  // Sort by row, col for deterministic processing
      .groupBy(_._2.value)                    // Group by content
      .map { case (content, cellEntries) =>
        // Skip empty cells
        if content.trim.isEmpty then
          (content, Seq.empty[CellAddress])
        else
          // Map each cell to its address, properly preserving the original coordinates
          (content, cellEntries.map { case ((row, col), cellInfo) =>
            // Determine which coordinates to use based on configuration and availability
            val (finalRow, finalCol) =
              if config.preserveOriginalCoordinates && cellInfo.originalRow.isDefined && cellInfo.originalCol.isDefined then
                // Use the original coordinates directly from the Excel file
                (cellInfo.originalRow.get, cellInfo.originalCol.get)
              else
                // Fallback to internal coordinates if original not available or preservation disabled
                (row, col)

            // Always use the final coordinates to generate the A1 notation
            val cellAddr = CellAddress(finalRow, finalCol)

            if config.verbose && cellInfo.originalRow.isDefined then
              // Log in verbose mode to help track coordinate transformations
              logger.debug(s"Cell mapping: internal($row,$col) -> A1:${cellAddr.toA1Notation} [${cellInfo.value}]")

            cellAddr
          }.toSeq)
      }
      .toList                                 // Convert back to list to maintain stable order
      .sortBy(_._1)                           // Sort by content string for deterministic processing
      .filter(_._2.nonEmpty)                  // Remove any empty listings

    // Step 2: For each content value, try to merge consecutive addresses into ranges
    val contentToRanges = contentToAddresses.map { case (content, addresses) =>
      val ranges = mergeConsecutiveAddresses(addresses)
      (content, ranges)
    }

    // Step 3: Convert to the final format for output
    val intermediateResult = contentToRanges.map { case (content, ranges) =>
      if ranges.size == 1 then
        // If there's only one range, use it directly
        (content, Left(ranges.head))
      else
        // If there are multiple ranges, return them as a sorted list for deterministic output
        (content, Right(ranges.toList.sorted))
    }
    
    // Convert to TreeMap for deterministic iteration of final map
    val result = scala.collection.immutable.TreeMap[String, Either[String, List[String]]](intermediateResult.toSeq: _*)

    // Log compression results
    val originalCellCount = grid.cells.size
    val uniqueContentCount = result.size
    val compressionRatio = if uniqueContentCount > 0 then
      originalCellCount.toDouble / uniqueContentCount
    else
      1.0

    logger.info(f"Inverted index: $originalCellCount cells -> $uniqueContentCount entries ($compressionRatio%.2fx compression)")

    result

  /**
   * Attempts to merge consecutive cell addresses into ranges to reduce the
   * number of entries needed to represent the same information.
   *
   * @param addresses Sequence of individual cell addresses to merge
   * @return Sequence of A1 notation strings (either individual addresses or ranges)
   */
  private def mergeConsecutiveAddresses(addresses: Seq[CellAddress]): Seq[String] =
    if addresses.isEmpty then
      return Seq.empty
    if addresses.size == 1 then
      return Seq(addresses.head.toA1Notation)

    // Sort addresses by row, then by column for consistent processing
    val sortedAddresses = addresses.sortBy(addr => (addr.row, addr.col))

    // Find horizontal ranges (same row, consecutive columns)
    val horizontalRanges = findRanges(
      sortedAddresses,
      (a, b) => a.row == b.row && a.col + 1 == b.col
    )

    // Find vertical ranges (same column, consecutive rows)
    val verticalRanges = findRanges(
      sortedAddresses,
      (a, b) => a.col == b.col && a.row + 1 == b.row
    )

    // Choose the approach that produces fewer ranges
    // When there's a tie, always prefer horizontal ranges for deterministic results
    val (selectedRanges, rangeType) =
      if horizontalRanges.size < verticalRanges.size then
        (horizontalRanges, "horizontal")
      else if horizontalRanges.size > verticalRanges.size then
        (verticalRanges, "vertical")
      else
        // Deterministic tie-breaking: always choose horizontal ranges
        (horizontalRanges, "horizontal")

    // Convert ranges to A1 notation
    val result = selectedRanges.map {
      case Seq(singleAddr) =>
        singleAddr.toA1Notation
      case rangeAddresses =>
        val startRow = rangeAddresses.head.row
        val startCol = rangeAddresses.head.col
        val endRow = rangeAddresses.last.row
        val endCol = rangeAddresses.last.col
        CellRange(startRow, startCol, endRow, endCol).toA1Notation
    }

    result

  /**
   * Finds ranges of consecutive addresses based on a provided adjacency test.
   * Returns a deterministic sequence of address ranges, ensuring consistent output.
   *
   * @param addresses  Sorted sequence of addresses to check
   * @param isAdjacent Function that tests if two addresses are adjacent
   * @return Sequence of address ranges, with each range containing sorted addresses
   */
  private def findRanges(
                          addresses: Seq[CellAddress],
                          isAdjacent: (CellAddress, CellAddress) => Boolean
                        ): Seq[Seq[CellAddress]] =
    // Ensure input is sorted for deterministic processing
    val sortedAddresses = if addresses != addresses.sortBy(addr => (addr.row, addr.col)) then
      addresses.sortBy(addr => (addr.row, addr.col))
    else
      addresses
      
    val ranges = Seq.newBuilder[Seq[CellAddress]]
    var currentRange = Seq.newBuilder[CellAddress]

    // Add the first address to start a range
    if sortedAddresses.nonEmpty then
      currentRange += sortedAddresses.head

    // Process the rest of the addresses
    for i <- 1 until sortedAddresses.size do
      val prev = sortedAddresses(i - 1)
      val curr = sortedAddresses(i)

      if isAdjacent(prev, curr) then
        // Continue the current range
        currentRange += curr
      else
        // End current range and start a new one
        ranges += currentRange.result()
        currentRange = Seq.newBuilder[CellAddress]
        currentRange += curr

    // Add the last range if it's not empty
    if sortedAddresses.nonEmpty then
      ranges += currentRange.result()

    // Ensure output is deterministic by sorting the result
    ranges.result()

  /**
   * Represents a cell's address in A1 notation (e.g., "A1" or "B5").
   * This is the standard Excel column-row reference format.
   * 
   * Implements Ordered to allow deterministic sorting of addresses.
   */
  case class CellAddress(row: Int, col: Int) extends Ordered[CellAddress]:
    /**
     * Debug method to help identify coordinate translation issues.
     */
    def debug: String = s"internal(r=$row,c=$col) -> $toA1Notation"
    
    /**
     * Compare method for ordering CellAddress instances in a deterministic way.
     * Orders first by row, then by column.
     */
    def compare(that: CellAddress): Int =
      val rowCompare = this.row.compare(that.row)
      if rowCompare != 0 then rowCompare else this.col.compare(that.col)

    /**
     * Convert numeric row, col indices to Excel-style A1 notation.
     *
     * This method converts from our internal 0-based indices to Excel's 1-based row indices.
     * We add 1 to the row index to match Excel's 1-indexed rows.
     *
     * @return The Excel A1 notation for this cell address
     */
    def toA1Notation: String =
      // Ensure column index is valid (non-negative)
      val validCol = math.max(0, col)
      
      // Using core ExcelUtils to ensure consistency with the rest of the codebase
      val excelCol = ExcelReference.Col(validCol)
      val columnName = ExcelUtils.columnToString(excelCol)

      // Ensure row index is valid and convert from 0-based to 1-based row indexing for Excel
      val validRow = math.max(0, row) + 1
      s"$columnName$validRow"

  /**
   * Represents a range of cells in A1 notation (e.g., "A1:B5").
   */
  case class CellRange(startRow: Int, startCol: Int, endRow: Int, endCol: Int):
    /**
     * Convert to Excel-style range notation (e.g., "A1:B5").
     */
    def toA1Notation: String =
      val startAddress = CellAddress(startRow, startCol).toA1Notation
      val endAddress = CellAddress(endRow, endCol).toA1Notation
      s"$startAddress:$endAddress"