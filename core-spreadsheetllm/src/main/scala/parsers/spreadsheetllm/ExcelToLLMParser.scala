package com.tjclp.xlcr
package parsers.spreadsheetllm

import compression.CompressionPipeline
import compression.models.CellInfo
import compression.utils.SheetGridUtils
import models.FileContent
import models.excel.SheetsData
import models.spreadsheetllm.CompressedWorkbook
import parsers.{ParserConfig, SimpleParser}
import types.MimeType

import org.slf4j.{Logger, LoggerFactory}

import java.nio.file.Paths
import scala.util.Try // Import Try

/** Parser that converts Excel files to CompressedWorkbook models using
  * the SpreadsheetLLM compression algorithms.
  *
  * This parser leverages the core Excel models and applies the SpreadsheetLLM
  * compression pipeline for LLM-friendly output.
  * Compatible with Scala 2.12.
  */
trait ExcelToLLMParser[I <: MimeType]
    extends SimpleParser[I, CompressedWorkbook] {
  protected val logger: Logger = LoggerFactory.getLogger(getClass)

  /** Configuration for the compression pipeline */
  protected val config: SpreadsheetLLMConfig

  /** Parse Excel file content into a CompressedWorkbook model.
    *
    * @param content The Excel file content
    * @return The compressed workbook model
    */
  override def parse(content: FileContent[I]): CompressedWorkbook = {
    logger.info(
      s"Parsing Excel file with ${content.data.length} bytes using core Excel models"
    )

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

    logger.info(
      s"Successfully compressed workbook with ${sheetsData.sheets.size} sheets"
    )
    compressedWorkbook
  }

  /** Convert SheetsData to the format expected by CompressionPipeline.
    *
    * @param sheetsData The Excel sheets data
    * @return Map of sheet names to (cells, rowCount, colCount)
    */
  private def convertSheetsDataToGrids(
      sheetsData: SheetsData
  ): Map[String, (Seq[CellInfo], Int, Int)] = {
    sheetsData.sheets.map { sheetData =>
      val sheetName = sheetData.name

      logger.info(s"Converting sheet data for: $sheetName")

      // Use the SheetGrid.fromSheetData helper to create a SheetGrid
      val sheetGrid = SheetGridUtils.fromSheetData(sheetData)

      // Extract just the cells from the grid
      val cells = sheetGrid.cells.values.toSeq

      // Log sheet dimensions
      logger.debug(
        s"Sheet $sheetName has dimensions: ${sheetData.rowCount}x${sheetData.columnCount} with ${cells.size} non-empty cells"
      )

      // Log content cells to help with debugging
      if (config.verbose) {
        val contentCells = cells.filterNot(_.isEffectivelyEmpty)
        logger.debug(s"Sheet $sheetName has ${contentCells.size} content cells")
        contentCells.take(10).foreach { cell =>
          logger.debug(f"Cell at (${cell.row},${cell.col}): '${cell.value
            .take(30)}', isNumeric=${cell.isNumeric}, isDate=${cell.isDate}")
        }
      }

      (sheetName, (cells, sheetData.rowCount, sheetData.columnCount))
    }.toMap
  }

  /** Extract filename from the input path.
    * Uses Try for safer file operations.
    *
    * @param inputPath The input file path
    * @return The extracted filename
    */
  private def extractFileName(inputPath: String): String = {
    // Use Try to safely extract filename
    Try {
      val path = Paths.get(inputPath)
      path.getFileName.toString
    }.toOption // Convert Try[String] to Option[String]
      .filter(name =>
        name != null && name.nonEmpty
      ) // Ensure filename is not null or empty
      .getOrElse {
        logger.warn(
          s"Could not extract a valid filename from input path: '$inputPath'. Using default 'excel-file.xlsx'."
        )
        "excel-file.xlsx" // Default filename if extraction fails or yields empty string
      }
  }

  /** The underlying Excel parser to use */
  protected def excelParser: parsers.Parser[I, SheetsData]
}

/** Factory for creating Excel parsers that output CompressedWorkbook models.
  * Compatible with Scala 2.12.
  */
object ExcelToLLMParser {

  import parsers.excel.SheetsDataExcelParser

  import org.slf4j.LoggerFactory // Import needed for logger in forXls

  /** Create a parser for XLSX files (.xlsx).
    *
    * @param config Configuration for the SpreadsheetLLM compression. Defaults to default config.
    * @return An XlsxToLLMParser instance.
    */
  def forXlsx(
      config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()
  ): XlsxToLLMParser = {
    new XlsxToLLMParser(config, new SheetsDataExcelParser())
  }

  /** Create a parser for XLS files (.xls).
    *
    * @param config Configuration for the SpreadsheetLLM compression. Defaults to default config.
    * @return An XlsToLLMParser instance.
    */
  def forXls(
      config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()
  ): XlsToLLMParser = {
    val logger = LoggerFactory.getLogger(getClass)
    logger.warn(
      "Creating XLS parser using SheetsDataExcelParser. Ensure this parser implementation supports HSSF (.xls) format. Casting parser type."
    )

    // TODO: Implement proper XLS parser support potentially using a different underlying parser (e.g., one based on Apache POI HSSF).
    // For now, use the default SheetsDataExcelParser and cast its type.
    // This cast is potentially unsafe if SheetsDataExcelParser only supports XLSX (XSSF).
    val specificParser = new SheetsDataExcelParser()
      .asInstanceOf[SimpleParser[
        MimeType.ApplicationVndMsExcel.type,
        SheetsData
      ]]
    new XlsToLLMParser(config, specificParser)
  }
}
