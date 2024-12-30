package com.tjclp.xlcr
package parsers.excel

import models.SheetData

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook

import java.io.FileOutputStream
import java.nio.file.{Files, Path}
import scala.annotation.tailrec
import scala.util.{Failure, Try}

/**
 * Converts JSON representing one or more SheetData objects into an Excel (.xlsx) file.
 *
 * The JSON may be either:
 *  - A single SheetData object (backward compatible).
 *  - An array of SheetData objects (new multi-sheet approach).
 */
object JsonToExcelWriter:
  def jsonToExcelFile(inputJsonPath: Path, outputExcelPath: Path): Try[Unit] =
    if !Files.exists(inputJsonPath) then
      Failure(IllegalArgumentException(s"JSON file does not exist: $inputJsonPath"))
    else
      val jsonContent = String(Files.readAllBytes(inputJsonPath))

      SheetData.fromJsonMultiple(jsonContent) match
        case Left(error) =>
          Failure(RuntimeException(s"Failed to parse single or multiple SheetData from JSON: ${error.getMessage}"))
        case Right(sheetsData) =>
          createExcelFromSheetsData(sheetsData, outputExcelPath)
  end jsonToExcelFile

  private def createExcelFromSheetsData(sheetsData: List[SheetData], outputExcelPath: Path): Try[Unit] = Try:
    val workbook = XSSFWorkbook()
    try
      for sd <- sheetsData do
        val sheet = workbook.createSheet(sd.name)

        for
          rowIndex <- 0 until sd.rowCount
          row = sheet.createRow(rowIndex)
          colIndex <- 0 until sd.columnCount
        do
          val cellRef = cellAddress(colIndex, rowIndex + 1)
          val poiCell = row.createCell(colIndex, CellType.BLANK)

          sd.cells.find(_.address == cellRef) match
            case Some(cellData) =>
              cellData.formula match
                case Some(formula) =>
                  poiCell.removeFormula()
                  poiCell.setCellFormula(formula)
                case None =>
                  if cellData.value.exists(isNumeric) then
                    poiCell.setCellValue(cellData.value.get.toDouble)
                  else if cellData.value.exists(isBoolean) then
                    poiCell.setCellValue(cellData.value.get.toBoolean)
                  else
                    poiCell.setCellValue(cellData.value.getOrElse(""))
            case None =>
              poiCell.setBlank()

      val fos = FileOutputStream(outputExcelPath.toFile)
      try
        workbook.write(fos)
      finally
        fos.close()
    finally
      workbook.close()
  end createExcelFromSheetsData

  private def cellAddress(colIndex: Int, rowIndex: Int): String =
    @tailrec
    def toLetters(n: Int, acc: String = ""): String =
      if n < 0 then acc
      else
        val remainder = n % 26
        val char = (remainder + 'A').toChar
        val next = n / 26 - 1
        toLetters(next, char.toString + acc)
    end toLetters

    s"${toLetters(colIndex)}$rowIndex"
  end cellAddress

  private def isNumeric(str: String): Boolean = str.matches("""-?\d+(\.\d+)?""")

  private def isBoolean(str: String): Boolean = str.equalsIgnoreCase("true") || str.equalsIgnoreCase("false")
