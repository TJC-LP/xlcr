package com.tjclp.xlcr
package compression.utils

import compression.models.CellInfo
import models.excel.CellData

import org.slf4j.LoggerFactory

/**
 * Utility functions for creating and working with CellInfo objects
 */
object CellInfoUtils:
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Convert from CellData to CellInfo
   *
   * @param cellData The CellData from the core Excel model
   * @return CellInfo suitable for anchor extraction
   */
  def fromCellData(cellData: CellData): CellInfo =
    // Extract the row and column from the cell's A1 reference
    // Keep the original 1-based row index that Excel uses (don't subtract 1)
    val rowIndex = cellData.rowIndex
    val colIndex = cellData.columnIndex

    // Validate column index is not negative
    val validatedColIndex = math.max(0, colIndex)

    if (colIndex < 0) {
      logger.warn(s"Detected negative column index: $colIndex in cell ${cellData.referenceA1}, corrected to 0")
    }

    // Use formatted value if available, otherwise use raw value
    val displayValue = cellData.formattedValue.getOrElse(cellData.value.getOrElse(""))

    // Extract formatting information
    val isBold = cellData.font.exists(_.bold)
    val isFormula = cellData.formula.isDefined
    val isNumeric = cellData.cellType == "NUMERIC"
    val isDate = cellData.dataFormat.exists(format =>
      format.contains("d") && format.contains("m") && format.contains("y") ||
        format == "m/d/yy" || format.contains("date"))
    val isEmpty = displayValue.trim.isEmpty || cellData.cellType == "BLANK"

    // Extract border information from cell style (if available)
    val hasTopBorder = cellData.style.exists(style =>
      style.borderTop.exists(_ != "NONE"))
    val hasBottomBorder = cellData.style.exists(style =>
      style.borderBottom.exists(_ != "NONE"))
    val hasLeftBorder = cellData.style.exists(style =>
      style.borderLeft.exists(_ != "NONE"))
    val hasRightBorder = cellData.style.exists(style =>
      style.borderRight.exists(_ != "NONE"))

    // Check if the cell has a background color
    val hasFillColor = cellData.style.exists(style =>
      style.backgroundColor.isDefined || style.foregroundColor.isDefined)

    // Calculate text feature ratios for header detection
    val text = displayValue
    val textLength = text.length
    val alphabetCount = text.count(_.isLetter)
    val numberCount = text.count(_.isDigit)
    val specialCount = text.count(c => !c.isLetterOrDigit && !c.isWhitespace)

    val alphabetRatio = if (textLength > 0) alphabetCount.toDouble / textLength else 0.0
    val numberRatio = if (textLength > 0) numberCount.toDouble / textLength else 0.0
    val spCharRatio = if (textLength > 0) specialCount.toDouble / textLength else 0.0

    CellInfo(
      row = rowIndex,
      col = validatedColIndex, // Use validated column index
      value = displayValue,
      isBold = isBold,
      isFormula = isFormula,
      isNumeric = isNumeric,
      isDate = isDate,
      isEmpty = isEmpty,
      hasTopBorder = hasTopBorder,
      hasBottomBorder = hasBottomBorder,
      hasLeftBorder = hasLeftBorder,
      hasRightBorder = hasRightBorder,
      hasFillColor = hasFillColor,
      textLength = textLength,
      alphabetRatio = alphabetRatio,
      numberRatio = numberRatio,
      spCharRatio = spCharRatio,
      numberFormatString = cellData.dataFormat,
      cellData = Some(cellData)
    )

  /**
   * Helper method to determine the type pattern (sequence of cell types) in a row or column.
   */
  def typePattern(cells: Seq[CellInfo]): String =
    cells.map { cell =>
      if cell.isEmpty then "E"
      else if cell.isNumeric then "N"
      else if cell.isDate then "D"
      else "T" // Text
    }.mkString

  /**
   * Helper method to determine the formatting pattern in a row or column.
   * This helps detect structure based on borders, colors, and formatting.
   */
  def formatPattern(cells: Seq[CellInfo]): String =
    cells.map { cell =>
      val borderPart = if cell.hasTopBorder || cell.hasBottomBorder ||
        cell.hasLeftBorder || cell.hasRightBorder then "B" else "-"
      val colorPart = if cell.hasFillColor then "C" else "-"
      val boldPart = if cell.isBold then "F" else "-"
      s"$borderPart$colorPart$boldPart"
    }.mkString