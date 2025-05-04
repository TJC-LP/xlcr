package com.tjclp.xlcr
package compression

import anchors.AnchorAnalyzer
import models.{CellInfo, SheetGrid, TableRegion}
import tables.TableDetector
import com.tjclp.xlcr.models.spreadsheetllm.{
  CompressedSheet,
  CompressedWorkbook
}

import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor

/** CompressionPipeline orchestrates the complete SpreadsheetLLM compression process,
  * applying each step in sequence to transform raw sheet data into a compressed representation.
  *
  * The compression pipeline consists of three main stages:
  * 1. Anchor Extraction: Identifies structural anchors and prunes unnecessary cells
  * 2. Inverted Index Translation: Converts the grid to a compact dictionary format
  * 3. Data Format Aggregation: Groups similar data by type to reduce token usage
  */
object CompressionPipeline {
  private val logger = LoggerFactory.getLogger(getClass)

  /** Compresses a workbook containing multiple sheets.
    *
    * @param workbookName The name of the workbook (filename)
    * @param sheets       Map of sheet names to their raw cell data
    * @param config       Configuration options for the compression
    * @return A compressed workbook model
    */
  def compressWorkbook(
      workbookName: String,
      sheets: Map[
        String,
        (Seq[CellInfo], Int, Int)
      ], // (cells, rowCount, colCount)
      config: SpreadsheetLLMConfig
  ): CompressedWorkbook = {
    logger.info(
      s"Starting compression for workbook: $workbookName with ${sheets.size} sheets"
    )

    // Process each sheet in parallel if multi-threading is enabled
    val compressedSheets =
      if (config.threads > 1) {
        logger.info(s"Using parallel processing with ${config.threads} threads")

        // Process sheets in parallel
        import java.util.concurrent.Executors
        import scala.concurrent.duration._
        import scala.concurrent.{Await, ExecutionContext, Future}

        val executor = Executors.newFixedThreadPool(config.threads)
        implicit val ec: ExecutionContextExecutor =
          ExecutionContext.fromExecutor(executor)

        try {
          val futures = sheets.map {
            case (sheetName, (cells, rowCount, colCount)) =>
              Future {
                compressSheet(sheetName, cells, rowCount, colCount, config)
              }
          }

          val futureList = Future.sequence(futures.toList)
          val result = Await.result(futureList, 1.hour)
          result
        } finally {
          executor.shutdown()
        }
      } else {
        // Process sheets sequentially
        logger.info("Using sequential processing")
        sheets.map { case (sheetName, (cells, rowCount, colCount)) =>
          compressSheet(sheetName, cells, rowCount, colCount, config)
        }.toList
      }

    // Create the workbook metadata
    val metadata = Map(
      "fileName" -> workbookName,
      "sheetCount" -> sheets.size.toString,
      "threadCount" -> config.threads.toString,
      "compressionDate" -> java.time.LocalDateTime.now().toString
    )

    // Create the final CompressedWorkbook model
    CompressedWorkbook(
      fileName = workbookName,
      sheets = compressedSheets,
      metadata = metadata
    )
  }

  /** Compresses a sheet using the full SpreadsheetLLM compression pipeline.
    *
    * @param sheetName The name of the sheet
    * @param rawCells  The raw cells from the sheet
    * @param rowCount  The original number of rows in the sheet
    * @param colCount  The original number of columns in the sheet
    * @param config    Configuration options for the compression
    * @return A compressed sheet model
    */
  private def compressSheet(
      sheetName: String,
      rawCells: Seq[CellInfo],
      rowCount: Int,
      colCount: Int,
      config: SpreadsheetLLMConfig
  ): CompressedSheet = {
    logger.info(
      s"Starting compression for sheet: $sheetName (${rowCount}x$colCount cells)"
    )

    // Create the initial sheet grid from raw cells
    val cellMap = rawCells.map(cell => (cell.row, cell.col) -> cell).toMap
    val grid = SheetGrid(cellMap, rowCount, colCount)

    // Metadata to track compression steps
    var compressionMetadata = Map.empty[String, String]

    // Step 1: Anchor Extraction and Table Detection (optional)
    val (anchorGrid, anchorMetadata, detectedTables) =
      if (config.disableAnchorExtraction) {
        logger.info("Anchor extraction disabled, using full sheet content")
        (grid, Map("anchorExtraction" -> "disabled"), List.empty)
      } else {
        logger.info(
          s"Performing anchor extraction with threshold: ${config.anchorThreshold}"
        )
        val startTime = System.currentTimeMillis()

        // First identify anchors to detect tables
        val (anchorRows, anchorCols) = AnchorAnalyzer.identifyAnchors(grid)

        // Detect table regions (if table detection is enabled)
        val tableRegions: List[TableRegion] =
          if (config.enableTableDetection) {
            val regions = TableDetector.detectTableRegions(
              grid,
              anchorRows,
              anchorCols,
              config
            )
            if (regions.nonEmpty) {
              logger.info(
                s"Detected ${regions.size} tables using enhanced column detection"
              )
              // Log more details about table types if verbose
              if (config.verbose) {
                val columnDominant = regions.count(r =>
                  r.width > 0 && r.height / r.width.toDouble >= 3.0
                )
                val rowDominant = regions.count(r =>
                  r.height > 0 && r.width / r.height.toDouble >= 3.0
                )
                val standard = regions.size - columnDominant - rowDominant
                logger.info(
                  s"Table types: $standard standard, $rowDominant row-dominant, $columnDominant column-dominant"
                )

              }

              // List all detected table ranges in the log
              regions.zipWithIndex.foreach { case (region, idx) =>
                val topLeft = InvertedIndexTranslator.CellAddress(
                  region.topRow,
                  region.leftCol
                )
                val bottomRight = InvertedIndexTranslator.CellAddress(
                  region.bottomRow,
                  region.rightCol
                )
                val range =
                  s"${topLeft.toA1Notation}:${bottomRight.toA1Notation}"
                logger.info(
                  f"Table ${idx + 1}: $range (${region.width}x${region.height}) - anchor rows: ${region.anchorRows.size}, anchor cols: ${region.anchorCols.size}"
                )
              }
            }
            regions
          } else {
            logger.info("Table detection disabled")
            List.empty
          }

        // Then extract the grid with pruning
        val extractedGrid =
          AnchorExtractor.extract(grid, config.anchorThreshold, config)

        val endTime = System.currentTimeMillis()

        val metadata = Map(
          "anchorExtraction" -> "enabled",
          "anchorThreshold" -> config.anchorThreshold.toString,
          "extractionTimeMs" -> (endTime - startTime).toString,
          "originalCellCount" -> (grid.rowCount * grid.colCount).toString,
          "retainedCellCount" -> extractedGrid.cells.size.toString,
          "tablesDetected" -> tableRegions.size.toString
        )

        // Convert TableRegion objects to ranges for later use
        val tables = tableRegions.map { region =>
          val topLeft =
            InvertedIndexTranslator.CellAddress(region.topRow, region.leftCol)
          val bottomRight = InvertedIndexTranslator.CellAddress(
            region.bottomRow,
            region.rightCol
          )
          val range = s"${topLeft.toA1Notation}:${bottomRight.toA1Notation}"

          // Try to detect if the first row is a header (usually an anchor row)
          val hasHeader = region.anchorRows.contains(region.topRow)
          val headerRow = if (hasHeader) Some(region.topRow) else None

          (range, hasHeader, headerRow)
        }

        (extractedGrid, metadata, tables)
      }

    compressionMetadata ++= anchorMetadata

    // Step 2: Data Format Aggregation on cells (optional)
    // Apply format aggregation BEFORE building the dictionary
    val (processedGrid, formatMetadata) =
      if (config.disableFormatAggregation) {
        logger.info(
          "Format aggregation disabled, using raw values in the anchored grid"
        )
        (anchorGrid, Map("formatAggregation" -> "disabled"))
      } else {
        logger.info(
          "Performing data format aggregation on grid cells before dictionary compression"
        )
        val startFormatTime = System.currentTimeMillis()

        // Apply cell-based format aggregation
        val aggregatedGrid =
          DataFormatAggregator.aggregateCells(anchorGrid, config)
        val endFormatTime = System.currentTimeMillis()

        val metadata = Map(
          "formatAggregation" -> "enabled",
          "cellBasedAggregation" -> "true",
          "aggregationTimeMs" -> (endFormatTime - startFormatTime).toString
        )

        (aggregatedGrid, metadata)
      }

    compressionMetadata ++= formatMetadata

    // Step 3: Inverted Index Translation (on the already type-aggregated grid)
    logger.info("Performing inverted index translation")
    val startIndexTime = System.currentTimeMillis()
    val finalContent = InvertedIndexTranslator.translate(processedGrid, config)
    val endIndexTime = System.currentTimeMillis()

    val indexMetadata = Map(
      "invertedIndexTranslation" -> "enabled",
      "translationTimeMs" -> (endIndexTime - startIndexTime).toString,
      "uniqueContentCount" -> finalContent.size.toString
    )

    compressionMetadata ++= indexMetadata

    // Calculate overall compression metrics
    val originalCellCount = rowCount * colCount
    val finalEntryCount = finalContent.size
    val overallCompressionRatio =
      if (finalEntryCount > 0) originalCellCount.toDouble / finalEntryCount
      else 1.0

    compressionMetadata += ("overallCompressionRatio" -> f"$overallCompressionRatio%.2f")

    logger.info(
      f"Compression complete for $sheetName: $originalCellCount cells -> $finalEntryCount entries ($overallCompressionRatio%.2fx compression)"
    )

    // Collect formula information
    val formulas = rawCells
      .filter(_.isFormula)
      .flatMap { cell =>
        // Extract formula text from the original cellData
        cell.cellData.flatMap(_.formula).map { formula =>
          val address =
            InvertedIndexTranslator.CellAddress(cell.row, cell.col).toA1Notation
          // Ensure formula starts with = if it doesn't already
          val normalizedFormula =
            if (formula.startsWith("=")) formula else s"=$formula"
          (normalizedFormula, address)
        }
      }
      .toMap

    // Create the basic compressed sheet
    var compressedSheet = CompressedSheet(
      name = sheetName,
      content = finalContent,
      formulas = formulas,
      originalRowCount = rowCount,
      originalColumnCount = colCount,
      compressionMetadata = compressionMetadata
    )

    // Add detected tables
    for ((range, hasHeaders, headerRow) <- detectedTables) {
      compressedSheet = compressedSheet.addTable(range, hasHeaders, headerRow)
    }

    // Log detected tables
    if (compressedSheet.tables.nonEmpty) {
      logger.info(
        s"Added ${compressedSheet.tables.size} tables to compressed sheet model:"
      )
      compressedSheet.tables.foreach { table =>
        logger.info(
          s"- Table ${table.id}: ${table.range}, hasHeaders=${table.hasHeaders}"
        )
      }
    }

    compressedSheet
  }
}
