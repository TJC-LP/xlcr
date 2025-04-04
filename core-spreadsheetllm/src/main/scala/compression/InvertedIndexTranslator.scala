package com.tjclp.xlcr
package compression

import compression.models.SheetGrid
import com.tjclp.xlcr.models.excel.ExcelReference
import com.tjclp.xlcr.utils.excel.ExcelUtils

import org.slf4j.LoggerFactory
import scala.collection.immutable.TreeMap

/**
 * InvertedIndexTranslator converts the pruned spreadsheet grid into a compact
 * dictionary format that maps unique cell content to locations.
 *
 * This is the second step in the SpreadsheetLLM compression pipeline, applied after
 * AnchorExtractor has pruned away less-informative cells.
 */
object InvertedIndexTranslator {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Converts a grid of cells into a dictionary mapping unique content to locations.
   * This inverted index approach dramatically reduces redundancy by grouping
   * cells with the same content and merging them into 2D rectangular ranges.
   *
   * @param grid   The sheet grid to process
   * @param config Configuration options including coordinate correction settings
   * @return A map from unique cell content to locations (either a single range or a list of addresses)
   */
  def translate(grid: SheetGrid, config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()): Map[String, Either[String, List[String]]] = {
    if (grid.cells.isEmpty) {
      return Map.empty
    }

    // 1) Convert to a sorted list for stable iteration (row-major order).
    val sortedCells = grid.cells
      .toList
      .sortBy { case ((row, col), _) => (row, col) }

    // 2) Group by *trimmed* content so that any purely-empty or trailing-whitespace cells are standardized.
    //    This also helps skip cells that only contain " " or tabs, if desired.
    val grouped = sortedCells.groupBy { case ((_, _), cellInfo) =>
      cellInfo.value.trim
    }

    // 3) Build addresses from each group. We skip truly empty content, i.e. ""
    val contentToAddressesList = grouped.map { case (content, cellEntries) =>
      if (content.isEmpty) {
        // Skip empties altogether
        (content, Seq.empty[CellAddress])
      } else {
        // For each cell in this group, pick either original coordinates or fallback
        val addresses = cellEntries.map { case ((row, col), cellInfo) =>
          val (finalRow, finalCol) =
            if (config.preserveOriginalCoordinates && cellInfo.originalRow.isDefined && cellInfo.originalCol.isDefined) {
              (cellInfo.originalRow.get, cellInfo.originalCol.get)
            } else {
              (row, col)
            }

          val addr = CellAddress(finalRow, finalCol)
          if (config.verbose && cellInfo.originalRow.isDefined) {
            logger.debug(s"Cell mapping: internal($row,$col) -> A1:${addr.toA1Notation} [${cellInfo.value}]")
          }
          addr
        }
        (content, addresses)
      }
    }

    // 4) Convert it to a list, remove empties, and sort content for stable output
    val filteredList = contentToAddressesList
      .toList
      .filter(_._2.nonEmpty)    // remove truly empty content
      .sortBy(_._1)             // sort by the content string itself

    // 5) Merge addresses for each content into as few rectangular ranges as possible.
    val mergedPairs = filteredList.map { case (content, addresses) =>
      val mergedRanges = mergeConsecutiveAddresses(addresses)

      // Convert the final ranges into the Left/Right Either form
      if (mergedRanges.size == 1) {
        (content, Left[String, List[String]](mergedRanges.head))
      } else {
        (content, Right[String, List[String]](mergedRanges.sorted.toList))
      }
    }

    // 6) Convert to a TreeMap for stable iteration in final output
    //    This yields a sorted map by content string (the keys).
    val result: Map[String, Either[String, List[String]]] =
      TreeMap[String, Either[String, List[String]]](mergedPairs: _*)

    // Logging
    val originalCellCount = grid.cells.size
    val uniqueContentCount = result.size
    val compressionRatio =
      if (uniqueContentCount > 0) {
        originalCellCount.toDouble / uniqueContentCount
      } else {
        1.0
      }

    logger.info(f"Inverted index: $originalCellCount cells -> $uniqueContentCount entries ($compressionRatio%.2fx compression)")
    result
  }

  /**
   * Represents a cell's address (row, col), with sorting row-first then column.
   */
  case class CellAddress(row: Int, col: Int) extends Ordered[CellAddress] {
    def compare(that: CellAddress): Int = {
      val c = this.row.compare(that.row)
      if (c != 0) {
        c
      } else {
        this.col.compare(that.col)
      }
    }

    /**
     * Convert numeric (row, col) -> Excel A1 notation, 1-based indexing.
     */
    def toA1Notation: String = {
      val validCol = math.max(0, col)
      val excelCol = ExcelReference.Col(validCol)
      val columnName = ExcelUtils.columnToString(excelCol)
      val validRow = math.max(0, row) + 1
      s"$columnName$validRow"
    }
  }

  /**
   * Represents a rectangular range of cells in (startRow, startCol) -> (endRow, endCol) form.
   * We can convert it to A1 notation: e.g. "A1:B5".
   */
  case class CellRange(startRow: Int, startCol: Int, endRow: Int, endCol: Int) {
    def toA1Notation: String = {
      val startAddress = CellAddress(startRow, startCol).toA1Notation
      val endAddress   = CellAddress(endRow,   endCol).toA1Notation
      s"$startAddress:$endAddress"
    }
  }

  /**
   * Attempts to merge addresses into as few 2D rectangular ranges as possible.
   * 1) We repeatedly pick an unvisited cell, expand the largest rectangle from it,
   * 2) Remove that rectangle's addresses, and repeat.
   * 3) Then we run an optional "coalesceRectangles" pass to unify any adjacent rectangles.
   *
   * @param addresses Sequence of cell addresses to merge
   * @return Sequence of A1 notation strings
   */
  private def mergeConsecutiveAddresses(addresses: Seq[CellAddress]): Seq[String] = {
    if (addresses.isEmpty) return Seq.empty
    if (addresses.size == 1) return Seq(addresses.head.toA1Notation)

    // Convert addresses to a Set for O(1) membership checks
    val addrSet = collection.mutable.Set[CellAddress](addresses: _*)
    val rawRectangles = collection.mutable.ListBuffer[CellRange]()

    // Expand largest rectangle for each top-left corner until we exhaust addrSet
    while (addrSet.nonEmpty) {
      val start = addrSet.head
      val (topRow, leftCol) = (start.row, start.col)

      // We'll track the largest rectangle found from (topRow, leftCol)
      var maxBottom = topRow
      var maxRight = leftCol

      var doneExpanding = false
      var candidateBottom = topRow

      while (!doneExpanding) {
        // Check if we can expand downwards. We only need *any* cell in the next row
        // within the current potential width [leftCol..maxRight] to continue vertically.
        // However, the original logic checks if *the entire next row segment* is potentially valid.
        // Let's stick to the logic of checking row candidateBottom, columns leftCol..maxRight
        // and then expanding width.

        // Check if the row candidateBottom (from leftCol to potentially further right) exists in the set
        var rowSegmentExists = false
        var cCheck = leftCol
        while (cCheck <= maxRight && !rowSegmentExists) {
           if (addrSet.contains(CellAddress(candidateBottom, cCheck))) rowSegmentExists = true
           cCheck += 1
        }
        // A stricter check like the original would be:
        // val fullRowSegmentExists = (leftCol to maxRight).forall(c => addrSet.contains(CellAddress(candidateBottom, c)))

        if (!rowSegmentExists) { // If no cell in candidateBottom row (within current width) exists
          doneExpanding = true
        } else { // Row segment has at least one cell, try expanding horizontally
          // Expand horizontally in the candidateBottom row
          var localRight = maxRight // Start checking from the column after current maxRight
          var keepExpandingHorizontally = true
          while (keepExpandingHorizontally) {
            val testRight = localRight + 1
            // Check if the rectangle (topRow, leftCol) to (candidateBottom, testRight) is full
            if (isFullRectangle(topRow, leftCol, candidateBottom, testRight, addrSet)) {
              localRight = testRight // Expansion successful, update localRight
            } else {
              keepExpandingHorizontally = false // Cannot expand further right for this height
            }
          }

          // After expanding horizontally as much as possible for candidateBottom:
          // If the rectangle (topRow, leftCol) to (candidateBottom, localRight) is indeed full
          // (it should be, based on the inner loop), update maxBottom and maxRight
          // and try the next row down.
          if (isFullRectangle(topRow, leftCol, candidateBottom, localRight, addrSet)) {
              maxBottom = candidateBottom // Valid expansion down to this row
              maxRight = localRight     // Valid expansion right to this column (at this height)
              candidateBottom += 1      // Try the next row
          } else {
              // This case implies a hole appeared, which shouldn't happen with the inner loop logic.
              // Or, the initial check for rowSegmentExists was too loose.
              // If we used the stricter check (fullRowSegmentExists), and it failed, we'd stop here.
              doneExpanding = true
          }
        } // End else (rowSegmentExists)
      } // End while !doneExpanding

      // Remove all cells in the found rectangle [topRow..maxBottom, leftCol..maxRight] from addrSet
      for (r <- topRow to maxBottom) {
        for (c <- leftCol to maxRight) {
          addrSet.remove(CellAddress(r, c))
        }
      }

      // Record this rectangle
      rawRectangles += CellRange(topRow, leftCol, maxBottom, maxRight)
    } // End while addrSet.nonEmpty

    // Now optionally coalesce any adjacent rectangles that form a perfect bigger rectangle
    val coalesced = coalesceRectangles(rawRectangles.toList, addresses.toSet)

    // Convert each final rectangle to A1 notation
    coalesced.map { r =>
      if (r.startRow == r.endRow && r.startCol == r.endCol) {
        // Single cell
        CellAddress(r.startRow, r.startCol).toA1Notation
      } else {
        r.toA1Notation
      }
    }
  }

  /**
   * Helper method: confirms if all cells in [topRow..bottomRow, leftCol..rightCol] are in addrSet.
   */
  private def isFullRectangle(topRow: Int, leftCol: Int, bottomRow: Int, rightCol: Int, addrSet: collection.mutable.Set[CellAddress]): Boolean = {
    var r = topRow
    while (r <= bottomRow) {
      var c = leftCol
      while (c <= rightCol) {
        if (!addrSet.contains(CellAddress(r, c))) {
          return false
        }
        c += 1
      }
      r += 1
    }
    true
  }

  /**
   * Attempt to merge rectangles that share identical row or column boundaries
   * and form a hole-free combined rectangle. This can further reduce the final
   * set of rectangles if the main expansion logic discovered them separately.
   *
   * We'll do a naive repeated approach: for each rectangle, see if we can unify
   * it with another. Stop once no merges occur.
   */
  private def coalesceRectangles(rects: List[CellRange], allAddrs: Set[CellAddress]): List[CellRange] = {
    var changed = true
    var current = rects

    while (changed) {
      changed = false
      val result = collection.mutable.ListBuffer[CellRange]()
      val visited = collection.mutable.Set[Int]()

      var i = 0
      while (i < current.size) {
        if (visited(i)) {
          // Already merged into a previous rectangle, skip
        } else {
          val r1 = current(i)
          var mergedThisRound = false
          var j = i + 1
          while (j < current.size && !mergedThisRound) {
            if (!visited(j)) {
              val r2 = current(j)
              tryMerge(r1, r2, allAddrs) match {
                case Some(m) =>
                  // merged
                  result += m
                  visited += i
                  visited += j
                  changed = true
                  mergedThisRound = true
                case None => () // No merge, continue checking j
              }
            }
            j += 1
          }

          if (!mergedThisRound) {
            // keep r1 as is, it wasn't merged with any subsequent rect
            result += r1
            visited += i // Mark as processed (either merged or kept)
          }
        }
        i += 1
      }

      current = result.toList // Prepare for next iteration
    }

    current
  }

  /**
   * If r1 and r2 can be merged (i.e. they share identical columns and adjacent rows,
   * or identical rows and adjacent columns), and the union is hole-free, return Some(merged).
   * Otherwise, None.
   */
  private def tryMerge(r1: CellRange, r2: CellRange, allAddrs: Set[CellAddress]): Option[CellRange] = {
    // Sort them so we can unify logic (optional, but helps)
    // Note: Removed explicit topFirst/bottomSecond sorting as the logic checks both orders anyway
    // val topFirst = if (r1.startRow < r2.startRow || (r1.startRow == r2.startRow && r1.startCol <= r2.startCol)) r1 else r2
    // val bottomSecond = if topFirst == r1 then r2 else r1

    // Case 1: Horizontal merge => same top..bottom, adjacent columns
    val sameRows = (r1.startRow == r2.startRow && r1.endRow == r2.endRow)
    if (sameRows) {
      // Are they side-by-side? Check both orders r1-r2 and r2-r1
      val adjacentCols = (r1.endCol + 1 == r2.startCol) || (r2.endCol + 1 == r1.startCol)
      if (adjacentCols) {
        val top = r1.startRow
        val left = math.min(r1.startCol, r2.startCol)
        val bottom = r1.endRow
        val right = math.max(r1.endCol, r2.endCol)
        if (isFullRect(top, left, bottom, right, allAddrs)) {
          return Some(CellRange(top, left, bottom, right))
        }
      }
    }

    // Case 2: Vertical merge => same left..right, adjacent rows
    val sameCols = (r1.startCol == r2.startCol && r1.endCol == r2.endCol)
    if (sameCols) {
      // Are they stacked vertically? Check both orders r1 above r2, r2 above r1
      val adjacentRows = (r1.endRow + 1 == r2.startRow) || (r2.endRow + 1 == r1.startRow)
      if (adjacentRows) {
        val left = r1.startCol
        val top = math.min(r1.startRow, r2.startRow)
        val right = r1.endCol
        val bottom = math.max(r1.endRow, r2.endRow)
        if (isFullRect(top, left, bottom, right, allAddrs)) {
          return Some(CellRange(top, left, bottom, right))
        }
      }
    }

    None
  }

  /**
   * Check if every cell in [top..bottom, left..right] is indeed in the set of addresses.
   * This is used by the coalescing function to confirm the union is hole-free.
   */
  private def isFullRect(top: Int, left: Int, bottom: Int, right: Int, allAddrs: Set[CellAddress]): Boolean = {
    var r = top
    while (r <= bottom) {
      var c = left
      while (c <= right) {
        if (!allAddrs.contains(CellAddress(r, c))) {
          return false
        }
        c += 1
      }
      r += 1
    }
    true
  }

  /**
   * Finds groups of consecutive addresses according to the provided adjacency test.
   * (Currently not used by the new 2D merging logic, but kept in case we need it.)
   */
  private def findRanges(
                          addresses: Seq[CellAddress],
                          isAdjacent: (CellAddress, CellAddress) => Boolean
                        ): Seq[Seq[CellAddress]] = {
    if (addresses.isEmpty) return Seq.empty

    val grouped = Seq.newBuilder[Seq[CellAddress]]
    var current = Seq.newBuilder[CellAddress]

    // Start the first range
    current += addresses.head

    // Accumulate
    for (i <- 1 until addresses.size) {
      val prev = addresses(i - 1)
      val currAddr = addresses(i)
      if (isAdjacent(prev, currAddr)) {
        current += currAddr
      } else {
        // finalize previous group
        grouped += current.result()
        // start a new group
        current = Seq.newBuilder[CellAddress]
        current += currAddr
      }
    }

    // finalize the last group
    val lastGroupResult = current.result()
    if (lastGroupResult.nonEmpty) {
      grouped += lastGroupResult
    }

    grouped.result()
  }
}

