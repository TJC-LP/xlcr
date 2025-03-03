package com.tjclp.xlcr
package parsers.spreadsheetllm

import compression.AnchorExtractor.CellInfo
import compression.CompressionPipeline
import models.FileContent
import models.spreadsheetllm.CompressedWorkbook
import parsers.Parser
import types.MimeType

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.{CellType, WorkbookFactory, Workbook as PoiWorkbook}
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.{Logger, LoggerFactory}

import java.io.ByteArrayInputStream
import scala.jdk.CollectionConverters.*
import scala.util.Try

/**
 * Parser that converts Excel files to CompressedWorkbook models using
 * the SpreadsheetLLM compression algorithms.
 *
 * This parser supports both modern XLSX files and legacy XLS files.
 * It extracts the relevant information from the Excel files and applies
 * the SpreadsheetLLM compression pipeline.
 */
trait ExcelToLLMParser[I <: MimeType] extends Parser[I, CompressedWorkbook]:
  protected val logger: Logger = LoggerFactory.getLogger(getClass)
  
  /** Configuration for the compression pipeline */
  protected val config: SpreadsheetLLMConfig
  
  /**
   * Parse Excel file content into a CompressedWorkbook model.
   *
   * @param content The Excel file content
   * @return The compressed workbook model
   */
  override def parse(content: FileContent[I]): CompressedWorkbook =
    logger.info(s"Parsing Excel file with ${content.data.length} bytes")
    
    // Load the workbook using Apache POI
    val workbook = loadWorkbook(content.data)
    
    try
      // Extract file name from the configuration if available
      val fileName = extractFileName(config.input)
      
      // Extract cells from each sheet
      val sheets = extractSheets(workbook)
      
      // Apply the compression pipeline
      val compressedWorkbook = CompressionPipeline.compressWorkbook(
        fileName,
        sheets,
        config
      )
      
      logger.info(s"Successfully compressed workbook with ${sheets.size} sheets")
      compressedWorkbook
    finally
      // Clean up resources
      workbook.close()
  
  /**
   * Load a workbook from byte array.
   *
   * @param data The Excel file bytes
   * @return Apache POI workbook object
   */
  protected def loadWorkbook(data: Array[Byte]): PoiWorkbook =
    val input = new ByteArrayInputStream(data)
    WorkbookFactory.create(input)
  
  /**
   * Extract sheet data from an Apache POI workbook.
   *
   * @param workbook The POI workbook
   * @return Map of sheet names to (cells, rowCount, colCount)
   */
  private def extractSheets(
    workbook: PoiWorkbook
  ): Map[String, (Seq[CellInfo], Int, Int)] =
    // Process each sheet in the workbook
    (0 until workbook.getNumberOfSheets).map { sheetIndex =>
      val sheet = workbook.getSheetAt(sheetIndex)
      val sheetName = sheet.getSheetName
      
      logger.info(s"Extracting data from sheet: $sheetName")
      
      // Find the sheet dimensions
      val rowCount = if sheet.getLastRowNum >= 0 then sheet.getLastRowNum + 1 else 0
      val colCount = if rowCount > 0 then
        (0 until rowCount)
          .flatMap(i => Option(sheet.getRow(i)))
          .map(r => if r.getLastCellNum >= 0 then r.getLastCellNum.toInt else 0)
          .maxOption
          .getOrElse(0)
      else
        0
      
      logger.info(s"Sheet dimensions: ${rowCount}x$colCount")
      
      // Extract cells from the sheet
      val cells = for
        rowIndex <- 0 until rowCount
        row = sheet.getRow(rowIndex)
        if row != null
        colIndex <- 0 until colCount
        cell = row.getCell(colIndex)
        if cell != null
      yield
        // Extract cell information
        val value = extractCellValue(cell)
        val isBold = Try(
          workbook.getFontAt(cell.getCellStyle.getFontIndex).getBold
        ).getOrElse(false)
        val isFormula = cell.getCellType == CellType.FORMULA
        val isNumeric = cell.getCellType == CellType.NUMERIC
        val isDate = Try(cell.getCellStyle.getDataFormat == 14 || cell.getCellStyle.getDataFormat == 22).getOrElse(false)
        val isEmpty = cell.getCellType == CellType.BLANK || value.trim.isEmpty
        
        CellInfo(
          row = rowIndex,
          col = colIndex,
          value = value,
          isBold = isBold,
          isFormula = isFormula,
          isNumeric = isNumeric,
          isDate = isDate,
          isEmpty = isEmpty
        )
      
      (sheetName, (cells, rowCount, colCount))
    }.toMap
  
  /**
   * Extract filename from the input path.
   * 
   * @param inputPath The input file path
   * @return The extracted filename
   */
  private def extractFileName(inputPath: String): String =
    // Extract just the filename portion without path
    val path = java.nio.file.Paths.get(inputPath)
    val fileName = path.getFileName.toString
    
    // If no filename could be extracted, use a generic name
    if fileName.isEmpty then "excel-file.xlsx" else fileName
  
  /**
   * Extract the display value from a cell.
   *
   * @param cell The POI cell
   * @return The display value as a string
   */
  private def extractCellValue(cell: org.apache.poi.ss.usermodel.Cell): String =
    import org.apache.poi.ss.usermodel.CellType.*
    
    cell.getCellType match
      case STRING => cell.getStringCellValue
      case NUMERIC =>
        if org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell) then
          Try(cell.getDateCellValue.toString).getOrElse("")
        else
          val value = cell.getNumericCellValue
          // Format numeric values nicely
          if value == Math.floor(value) && !java.lang.Double.isInfinite(value) then
            value.toInt.toString
          else
            value.toString
      case BOOLEAN => cell.getBooleanCellValue.toString
      case FORMULA =>
        Try {
          cell.getCachedFormulaResultType match
            case NUMERIC => 
              if org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell) then
                cell.getDateCellValue.toString
              else
                cell.getNumericCellValue.toString
            case STRING => cell.getStringCellValue
            case BOOLEAN => cell.getBooleanCellValue.toString
            case _ => cell.getCellFormula
        }.getOrElse(cell.getCellFormula)
      case BLANK => ""
      case ERROR => "#ERROR"
      case _ => ""

/**
 * Parser for XLSX files to CompressedWorkbook models.
 */
class XlsxToLLMParser(val config: SpreadsheetLLMConfig) 
  extends ExcelToLLMParser[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type]:
  
  override protected def loadWorkbook(data: Array[Byte]): PoiWorkbook =
    val input = new ByteArrayInputStream(data)
    new XSSFWorkbook(input)

/**
 * Parser for XLS files to CompressedWorkbook models.
 */
class XlsToLLMParser(val config: SpreadsheetLLMConfig)
  extends ExcelToLLMParser[MimeType.ApplicationVndMsExcel.type]:
  
  override protected def loadWorkbook(data: Array[Byte]): PoiWorkbook =
    val input = new ByteArrayInputStream(data)
    new HSSFWorkbook(input)

/**
 * Factory for creating Excel parsers.
 */
object ExcelToLLMParser:
  /**
   * Create a parser for XLSX files.
   */
  def forXlsx(config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()): XlsxToLLMParser =
    new XlsxToLLMParser(config)
  
  /**
   * Create a parser for XLS files.
   */
  def forXls(config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()): XlsToLLMParser =
    new XlsToLLMParser(config)