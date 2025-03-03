package com.tjclp.xlcr
package compression

import models.spreadsheetllm.{CompressedSheet, CompressedWorkbook}
import compression.AnchorExtractor.{CellInfo, SheetGrid}

import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor

/**
 * CompressionPipeline orchestrates the complete SpreadsheetLLM compression process,
 * applying each step in sequence to transform raw sheet data into a compressed representation.
 *
 * The compression pipeline consists of three main stages:
 * 1. Anchor Extraction: Identifies structural anchors and prunes unnecessary cells
 * 2. Inverted Index Translation: Converts the grid to a compact dictionary format
 * 3. Data Format Aggregation: Groups similar data by type to reduce token usage
 */
object CompressionPipeline:
  private val logger = LoggerFactory.getLogger(getClass)
  
  /**
   * Compresses a sheet using the full SpreadsheetLLM compression pipeline.
   *
   * @param sheetName The name of the sheet
   * @param rawCells The raw cells from the sheet
   * @param rowCount The original number of rows in the sheet
   * @param colCount The original number of columns in the sheet
   * @param config Configuration options for the compression
   * @return A compressed sheet model
   */
  private def compressSheet(
    sheetName: String,
    rawCells: Seq[CellInfo],
    rowCount: Int,
    colCount: Int,
    config: SpreadsheetLLMConfig
  ): CompressedSheet =
    logger.info(s"Starting compression for sheet: $sheetName (${rowCount}x$colCount cells)")
    
    // Create the initial sheet grid from raw cells
    val cellMap = rawCells.map(cell => (cell.row, cell.col) -> cell).toMap
    val grid = SheetGrid(cellMap, rowCount, colCount)
    
    // Metadata to track compression steps
    var compressionMetadata = Map.empty[String, String]
    
    // Step 1: Anchor Extraction (optional)
    val (anchorGrid, anchorMetadata) = 
      if config.disableAnchorExtraction then
        logger.info("Anchor extraction disabled, using full sheet content")
        (grid, Map("anchorExtraction" -> "disabled"))
      else
        logger.info(s"Performing anchor extraction with threshold: ${config.anchorThreshold}")
        val startTime = System.currentTimeMillis()
        val extractedGrid = AnchorExtractor.extract(grid, config.anchorThreshold)
        val endTime = System.currentTimeMillis()
        
        val metadata = Map(
          "anchorExtraction" -> "enabled",
          "anchorThreshold" -> config.anchorThreshold.toString,
          "extractionTimeMs" -> (endTime - startTime).toString,
          "originalCellCount" -> (grid.rowCount * grid.colCount).toString,
          "retainedCellCount" -> extractedGrid.cells.size.toString
        )
        
        (extractedGrid, metadata)
    
    compressionMetadata ++= anchorMetadata
    
    // Step 2: Inverted Index Translation
    logger.info("Performing inverted index translation")
    val startIndexTime = System.currentTimeMillis()
    val indexMap = InvertedIndexTranslator.translate(anchorGrid, config)
    val endIndexTime = System.currentTimeMillis()
    
    val indexMetadata = Map(
      "invertedIndexTranslation" -> "enabled",
      "translationTimeMs" -> (endIndexTime - startIndexTime).toString,
      "uniqueContentCount" -> indexMap.size.toString
    )
    
    compressionMetadata ++= indexMetadata
    
    // Step 3: Data Format Aggregation (optional)
    val (finalContent, formatMetadata) = 
      if config.disableFormatAggregation then
        logger.info("Format aggregation disabled, using raw values")
        (indexMap, Map("formatAggregation" -> "disabled"))
      else
        logger.info("Performing data format aggregation")
        val startFormatTime = System.currentTimeMillis()
        val aggregatedMap = DataFormatAggregator.aggregate(indexMap, anchorGrid)
        val endFormatTime = System.currentTimeMillis()
        
        val metadata = Map(
          "formatAggregation" -> "enabled",
          "aggregationTimeMs" -> (endFormatTime - startFormatTime).toString,
          "finalEntryCount" -> aggregatedMap.size.toString
        )
        
        (aggregatedMap, metadata)
    
    compressionMetadata ++= formatMetadata
    
    // Calculate overall compression metrics
    val originalCellCount = rowCount * colCount
    val finalEntryCount = finalContent.size
    val overallCompressionRatio = 
      if finalEntryCount > 0 then originalCellCount.toDouble / finalEntryCount else 1.0
    
    compressionMetadata += ("overallCompressionRatio" -> f"$overallCompressionRatio%.2f")
    
    logger.info(f"Compression complete for $sheetName: $originalCellCount cells -> $finalEntryCount entries ($overallCompressionRatio%.2fx compression)")
    
    // Create the final CompressedSheet model
    CompressedSheet(
      name = sheetName,
      content = finalContent,
      originalRowCount = rowCount,
      originalColumnCount = colCount,
      compressionMetadata = compressionMetadata
    )
  
  /**
   * Compresses a workbook containing multiple sheets.
   *
   * @param workbookName The name of the workbook (filename)
   * @param sheets Map of sheet names to their raw cell data
   * @param config Configuration options for the compression
   * @return A compressed workbook model
   */
  def compressWorkbook(
    workbookName: String,
    sheets: Map[String, (Seq[CellInfo], Int, Int)], // (cells, rowCount, colCount)
    config: SpreadsheetLLMConfig
  ): CompressedWorkbook =
    logger.info(s"Starting compression for workbook: $workbookName with ${sheets.size} sheets")
    
    // Process each sheet in parallel if multi-threading is enabled
    val compressedSheets = 
      if config.threads > 1 then
        logger.info(s"Using parallel processing with ${config.threads} threads")
        
        // Process sheets in parallel
        import scala.concurrent.{Future, ExecutionContext, Await}
        import scala.concurrent.duration._
        import java.util.concurrent.Executors
        
        val executor = Executors.newFixedThreadPool(config.threads)
        implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(executor)
        
        try
          val futures = sheets.map { case (sheetName, (cells, rowCount, colCount)) =>
            Future {
              compressSheet(sheetName, cells, rowCount, colCount, config)
            }
          }
          
          val futureList = Future.sequence(futures.toList)
          val result = Await.result(futureList, 1.hour)
          result
        finally
          executor.shutdown()
      else
        // Process sheets sequentially
        sheets.map { case (sheetName, (cells, rowCount, colCount)) =>
          compressSheet(sheetName, cells, rowCount, colCount, config)
        }.toList
    
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