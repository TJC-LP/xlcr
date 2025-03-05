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
object InvertedIndexTranslator:
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
  def translate(grid: SheetGrid, config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()): Map[String, Either[String, List[String]]] =
    if grid.cells.isEmpty then
      return Map.empty

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
      if content.isEmpty then
        // Skip empties altogether
        (content, Seq.empty[CellAddress])
      else
        // For each cell in this group, pick either original coordinates or fallback
        val addresses = cellEntries.map { case ((row, col), cellInfo) =>
          val (finalRow, finalCol) =
            if config.preserveOriginalCoordinates && cellInfo.originalRow.isDefined && cellInfo.originalCol.isDefined then
              (cellInfo.originalRow.get, cellInfo.originalCol.get)
            else
              (row, col)

          val addr = CellAddress(finalRow, finalCol)
          if config.verbose && cellInfo.originalRow.isDefined then
            logger.debug(s"Cell mapping: internal($row,$col) -> A1:${addr.toA1Notation} [${cellInfo.value}]")
          addr
        }
        (content, addresses)
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
      if mergedRanges.size == 1 then
        (content, Left[String, List[String]](mergedRanges.head))
      else
        (content, Right[String, List[String]](mergedRanges.sorted.toList))
    }

    // 6) Convert to a TreeMap for stable iteration in final output
    //    This yields a sorted map by content string (the keys).
    val result: Map[String, Either[String, List[String]]] =
      TreeMap[String, Either[String, List[String]]](mergedPairs: _*)

    // Logging
    val originalCellCount = grid.cells.size
    val uniqueContentCount = result.size
    val compressionRatio =
      if uniqueContentCount > 0 then originalCellCount.toDouble / uniqueContentCount
      else 1.0

    logger.info(f"Inverted index: $originalCellCount cells -> $uniqueContentCount entries ($compressionRatio%.2fx compression)")
    result

  /**
   * Attempts to merge addresses into as few 2D rectangular ranges as possible.
   * 1) We repeatedly pick an unvisited cell, expand the largest rectangle from it,
   * 2) Remove that rectangle's addresses, and repeat.
   * 3) Then we run an optional "coalesceRectangles" pass to unify any adjacent rectangles.
   *
   * @param addresses Sequence of cell addresses to merge
   * @return Sequence of A1 notation strings
   */
  private def mergeConsecutiveAddresses(addresses: Seq[CellAddress]): Seq[String] =
    if addresses.isEmpty then return Seq.empty
    if addresses.size == 1 then return Seq(addresses.head.toA1Notation)

    // Convert addresses to a Set for O(1) membership checks
    val addrSet = collection.mutable.Set[CellAddress](addresses: _*)
    val rawRectangles = collection.mutable.ListBuffer[CellRange]()

    // Expand largest rectangle for each top-left corner until we exhaust addrSet
    while addrSet.nonEmpty do
      val start = addrSet.head
      val (topRow, leftCol) = (start.row, start.col)

      // We'll track the largest rectangle found from (topRow, leftCol)
      var maxBottom = topRow
      var maxRight = leftCol

      var doneExpanding = false
      var candidateBottom = topRow

      while !doneExpanding do
        // If there's no address in row = candidateBottom, we can't expand further down
        if !addrSet.exists(a => a.row == candidateBottom) then
          doneExpanding = true
        else
          // Expand horizontally in that row
          var localRight = maxRight
          var keepExpanding = true
          while keepExpanding do
            val testRight = localRight + 1
            if isFullRectangle(topRow, leftCol, candidateBottom, testRight, addrSet) then
              localRight = testRight
            else
              keepExpanding = false

          // Now we have a candidate horizontal expansion for row = candidateBottom
          if isFullRectangle(topRow, leftCol, candidateBottom, localRight, addrSet) then
            maxBottom = candidateBottom
            maxRight = localRight
            candidateBottom += 1
          else
            doneExpanding = true

      // Remove all cells in [topRow..maxBottom, leftCol..maxRight] from addrSet
      for
        r <- topRow to maxBottom
        c <- leftCol to maxRight
      do
        addrSet.remove(CellAddress(r, c))

      // Record this rectangle
      rawRectangles += CellRange(topRow, leftCol, maxBottom, maxRight)

    // Now optionally coalesce any adjacent rectangles that form a perfect bigger rectangle
    val coalesced = coalesceRectangles(rawRectangles.toList, addresses.toSet)

    // Convert each final rectangle to A1 notation
    coalesced.map { r =>
      if r.startRow == r.endRow && r.startCol == r.endCol then
        // Single cell
        CellAddress(r.startRow, r.startCol).toA1Notation
      else
        r.toA1Notation
    }

  /**
   * Helper method: confirms if all cells in [topRow..bottomRow, leftCol..rightCol] are in addrSet.
   */
  private def isFullRectangle(topRow: Int, leftCol: Int, bottomRow: Int, rightCol: Int, addrSet: collection.mutable.Set[CellAddress]): Boolean =
    var r = topRow
    while r <= bottomRow do
      var c = leftCol
      while c <= rightCol do
        if !addrSet.contains(CellAddress(r, c)) then
          return false
        c += 1
      r += 1
    true

  /**
   * Attempt to merge rectangles that share identical row or column boundaries
   * and form a hole-free combined rectangle. This can further reduce the final
   * set of rectangles if the main expansion logic discovered them separately.
   *
   * We'll do a naive repeated approach: for each rectangle, see if we can unify
   * it with another. Stop once no merges occur.
   */
  private def coalesceRectangles(rects: List[CellRange], allAddrs: Set[CellAddress]): List[CellRange] =
    var changed = true
    var current = rects

    while changed do
      changed = false
      val result = collection.mutable.ListBuffer[CellRange]()
      val visited = collection.mutable.Set[Int]()

      var i = 0
      while i < current.size do
        if visited(i) then
          i += 1
        else
          val r1 = current(i)
          var mergedThisRound = false
          var j = i + 1
          while j < current.size && !mergedThisRound do
            if !visited(j) then
              val r2 = current(j)
              tryMerge(r1, r2, allAddrs) match
                case Some(m) =>
                  // merged
                  result += m
                  visited += i
                  visited += j
                  changed = true
                  mergedThisRound = true
                case None => ()
            j += 1

          if !mergedThisRound then
            // keep r1 as is
            result += r1

          i += 1

      current = result.toList

    current

  /**
   * If r1 and r2 can be merged (i.e. they share identical columns and adjacent rows,
   * or identical rows and adjacent columns), and the union is hole-free, return Some(merged).
   * Otherwise, None.
   */
  private def tryMerge(r1: CellRange, r2: CellRange, allAddrs: Set[CellAddress]): Option[CellRange] =
    // Sort them so we can unify logic
    val topFirst = if (r1.startRow < r2.startRow || (r1.startRow == r2.startRow && r1.startCol <= r2.startCol)) r1 else r2
    val bottomSecond = if topFirst == r1 then r2 else r1

    // Case 1: Horizontal merge => same top..bottom, adjacent columns
    val sameRows = (r1.startRow == r2.startRow && r1.endRow == r2.endRow)
    if sameRows then
      // Are they side-by-side?
      // i.e. r1.endCol + 1 == r2.startCol, or vice versa
      val adjacentCols =
        (r1.endCol + 1 == r2.startCol) || (r2.endCol + 1 == r1.startCol)
      if adjacentCols then
        val top = r1.startRow
        val left = math.min(r1.startCol, r2.startCol)
        val bottom = r1.endRow
        val right = math.max(r1.endCol, r2.endCol)
        if isFullRect(top, left, bottom, right, allAddrs) then
          return Some(CellRange(top, left, bottom, right))

    // Case 2: Vertical merge => same left..right, adjacent rows
    val sameCols = (r1.startCol == r2.startCol && r1.endCol == r2.endCol)
    if sameCols then
      // Are they stacked vertically?
      val adjacentRows =
        (r1.endRow + 1 == r2.startRow) || (r2.endRow + 1 == r1.startRow)
      if adjacentRows then
        val left = r1.startCol
        val top = math.min(r1.startRow, r2.startRow)
        val right = r1.endCol
        val bottom = math.max(r1.endRow, r2.endRow)
        if isFullRect(top, left, bottom, right, allAddrs) then
          return Some(CellRange(top, left, bottom, right))

    None

  /**
   * Check if every cell in [top..bottom, left..right] is indeed in the set of addresses.
   * This is used by the coalescing function to confirm the union is hole-free.
   */
  private def isFullRect(top: Int, left: Int, bottom: Int, right: Int, allAddrs: Set[CellAddress]): Boolean =
    var r = top
    while r <= bottom do
      var c = left
      while c <= right do
        if !allAddrs.contains(CellAddress(r, c)) then
          return false
        c += 1
      r += 1
    true

  /**
   * Finds groups of consecutive addresses according to the provided adjacency test.
   * (Currently not used by the new 2D merging logic, but kept in case we need it.)
   */
  private def findRanges(
                          addresses: Seq[CellAddress],
                          isAdjacent: (CellAddress, CellAddress) => Boolean
                        ): Seq[Seq[CellAddress]] =
    if addresses.isEmpty then return Seq.empty

    val grouped = Seq.newBuilder[Seq[CellAddress]]
    var current = Seq.newBuilder[CellAddress]

    // Start the first range
    current += addresses.head

    // Accumulate
    for i <- 1 until addresses.size do
      val prev = addresses(i - 1)
      val currAddr = addresses(i)
      if isAdjacent(prev, currAddr) then
        current += currAddr
      else
        // finalize previous group
        grouped += current.result()
        // start a new group
        current = Seq.newBuilder[CellAddress]
        current += currAddr

    // finalize the last group
    if current.result().nonEmpty then
      grouped += current.result()

    grouped.result()

  /**
   * Represents a cell's address (row, col), with sorting row-first then column.
   */
  case class CellAddress(row: Int, col: Int) extends Ordered[CellAddress]:
    def compare(that: CellAddress): Int =
      val c = this.row.compare(that.row)
      if c != 0 then c else this.col.compare(that.col)

    /**
     * Convert numeric (row, col) -> Excel A1 notation, 1-based indexing.
     */
    def toA1Notation: String =
      val validCol = math.max(0, col)
      val excelCol = ExcelReference.Col(validCol)
      val columnName = ExcelUtils.columnToString(excelCol)
      val validRow = math.max(0, row) + 1
      s"$columnName$validRow"

  /**
   * Represents a rectangular range of cells in (startRow, startCol) -> (endRow, endCol) form.
   * We can convert it to A1 notation: e.g. "A1:B5".
   */
  case class CellRange(startRow: Int, startCol: Int, endRow: Int, endCol: Int):
    def toA1Notation: String =
      val startAddress = CellAddress(startRow, startCol).toA1Notation
      val endAddress   = CellAddress(endRow,   endCol).toA1Notation
      s"$startAddress:$endAddress"
