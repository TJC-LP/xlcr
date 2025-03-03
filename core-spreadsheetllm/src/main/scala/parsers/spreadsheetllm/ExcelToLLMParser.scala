package com.tjclp.xlcr
package parsers.spreadsheetllm

import compression.AnchorExtractor
import compression.CompressionPipeline
import models.FileContent
import models.excel.{CellData, SheetData, SheetsData}
import models.spreadsheetllm.CompressedWorkbook
import parsers.Parser
import types.MimeType

import org.slf4j.{Logger, LoggerFactory}

import java.nio.file.Paths

/**
 * Parser that converts Excel files to CompressedWorkbook models using
 * the SpreadsheetLLM compression algorithms.
 *
 * This parser leverages the core Excel models and applies the SpreadsheetLLM
 * compression pipeline for LLM-friendly output.
 */
trait ExcelToLLMParser[I <: MimeType] extends Parser[I, CompressedWorkbook]:
  protected val logger: Logger = LoggerFactory.getLogger(getClass)
  
  /** Configuration for the compression pipeline */
  protected val config: SpreadsheetLLMConfig
  
  /** The underlying Excel parser to use */
  protected def excelParser: Parser[I, SheetsData]
  
  /**
   * Parse Excel file content into a CompressedWorkbook model.
   *
   * @param content The Excel file content
   * @return The compressed workbook model
   */
  override def parse(content: FileContent[I]): CompressedWorkbook =
    logger.info(s"Parsing Excel file with ${content.data.length} bytes using core Excel models")
    
    // First parse the Excel file into core Excel models
    val sheetsData = excelParser.parse(content)
    
    // Extract file name from the configuration if available
    val fileName = extractFileName(config.input)
    
    // Convert SheetsData to sheet grids and apply compression
    val sheetGrids = convertSheetsDataToGrids(sheetsData)
    
    // Apply the compression pipeline
    val compressedWorkbook = CompressionPipeline.compressWorkbook(
      fileName,
      sheetGrids,
      config
    )
    
    logger.info(s"Successfully compressed workbook with ${sheetsData.sheets.size} sheets")
    compressedWorkbook
  
  /**
   * Convert SheetsData to the format expected by CompressionPipeline.
   *
   * @param sheetsData The Excel sheets data
   * @return Map of sheet names to (cells, rowCount, colCount)
   */
  private def convertSheetsDataToGrids(
    sheetsData: SheetsData
  ): Map[String, (Seq[AnchorExtractor.CellInfo], Int, Int)] =
    sheetsData.sheets.map { sheetData =>
      val sheetName = sheetData.name
      
      logger.info(s"Converting sheet data for: $sheetName")
      
      // Use the SheetGrid.fromSheetData helper to create a SheetGrid
      val sheetGrid = AnchorExtractor.SheetGrid.fromSheetData(sheetData)
      
      // Extract just the cells from the grid
      val cells = sheetGrid.cells.values.toSeq
      
      (sheetName, (cells, sheetData.rowCount, sheetData.columnCount))
    }.toMap
  
  /**
   * Extract filename from the input path.
   * 
   * @param inputPath The input file path
   * @return The extracted filename
   */
  private def extractFileName(inputPath: String): String =
    // Extract just the filename portion without path
    val path = Paths.get(inputPath)
    val fileName = path.getFileName.toString
    
    // If no filename could be extracted, use a generic name
    if fileName.isEmpty then "excel-file.xlsx" else fileName

/**
 * Parser for XLSX files to CompressedWorkbook models.
 */
class XlsxToLLMParser(val config: SpreadsheetLLMConfig, val excelParser: Parser[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type, SheetsData]) 
  extends ExcelToLLMParser[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type]

/**
 * Parser for XLS files to CompressedWorkbook models.
 */
class XlsToLLMParser(val config: SpreadsheetLLMConfig, val excelParser: Parser[MimeType.ApplicationVndMsExcel.type, SheetsData])
  extends ExcelToLLMParser[MimeType.ApplicationVndMsExcel.type]

/**
 * Factory for creating Excel parsers.
 */
object ExcelToLLMParser:
  import parsers.excel.SheetsDataExcelParser
  
  /**
   * Create a parser for XLSX files.
   */
  def forXlsx(config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()): XlsxToLLMParser =
    new XlsxToLLMParser(config, new SheetsDataExcelParser())
  
  /**
   * Create a parser for XLS files.
   */
  def forXls(config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()): XlsToLLMParser =
    // TODO: Implement XLS parser support by creating SheetsDataExcelParser for XLS
    // For now, use the XLSX parser which can usually handle XLS files too
    new XlsToLLMParser(config, 
      new SheetsDataExcelParser().asInstanceOf[Parser[MimeType.ApplicationVndMsExcel.type, SheetsData]])