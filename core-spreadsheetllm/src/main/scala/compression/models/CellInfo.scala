package com.tjclp.xlcr
package compression.models

import models.excel.CellData

/**
 * Cell information used for anchor analysis.
 *
 * @param row
 *   The 0-based row index
 * @param col
 *   The 0-based column index
 * @param value
 *   The cell content as a string
 * @param isBold
 *   Whether the cell has bold formatting
 * @param isFormula
 *   Whether the cell contains a formula
 * @param isNumeric
 *   Whether the cell contains numeric content
 * @param isDate
 *   Whether the cell contains a date
 * @param isEmpty
 *   Whether the cell is empty
 * @param isFillerContent
 *   Whether the cell contains only filler/placeholder content
 * @param hasTopBorder
 *   Whether the cell has a top border
 * @param hasBottomBorder
 *   Whether the cell has a bottom border
 * @param hasLeftBorder
 *   Whether the cell has a left border
 * @param hasRightBorder
 *   Whether the cell has a right border
 * @param hasFillColor
 *   Whether the cell has background fill color
 * @param textLength
 *   The length of the cell text (for ratio calculations)
 * @param alphabetRatio
 *   The ratio of alphabet characters to total length
 * @param numberRatio
 *   The ratio of numeric characters to total length
 * @param spCharRatio
 *   The ratio of special characters to total length
 * @param numberFormatString
 *   The Excel number format string if available
 * @param originalRow
 *   The original row index before remapping (for debugging)
 * @param originalCol
 *   The original column index before remapping (for debugging)
 * @param cellData
 *   The original CellData that this cell was derived from
 */
case class CellInfo(
  row: Int,
  col: Int,
  value: String,
  isBold: Boolean = false,
  isFormula: Boolean = false,
  isNumeric: Boolean = false,
  isDate: Boolean = false,
  isEmpty: Boolean = false,
  isFillerContent: Boolean = false,
  hasTopBorder: Boolean = false,
  hasBottomBorder: Boolean = false,
  hasLeftBorder: Boolean = false,
  hasRightBorder: Boolean = false,
  hasFillColor: Boolean = false,
  textLength: Int = 0,
  alphabetRatio: Double = 0.0,
  numberRatio: Double = 0.0,
  spCharRatio: Double = 0.0,
  numberFormatString: Option[String] = None,
  originalRow: Option[Int] = None,
  originalCol: Option[Int] = None,
  cellData: Option[CellData] = None
) {

  /**
   * Determines if this cell is effectively empty (either truly empty or just filler content)
   *
   * @return
   *   true if the cell is empty or contains only filler content
   */
  def isEffectivelyEmpty: Boolean = isEmpty || isFillerContent
}
