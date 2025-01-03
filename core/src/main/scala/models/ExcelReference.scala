package com.tjclp.xlcr
package models

import utils.excel.ExcelUtils

import scala.util.matching.Regex

enum ExcelReference:
  case Cell(sheet: String, row: ExcelReference.Row, col: ExcelReference.Col)
  case Range(start: Cell, end: Cell)
  case Named(name: String)

  def toA1: String = this match
    case Cell(sheet, row, col) => s"$sheet!${ExcelUtils.columnToString(col)}${row.value}"
    case Range(start, end) => s"${start.toA1}:${end.toA1}"
    case Named(name) => name

object ExcelReference:
  opaque type Row = Int
  opaque type Col = Int
  private val MaxRows = 1_048_576 // 2^20
  private val MaxCols = 16_384 // 2^14, column "XFD"

  private val CellPattern: Regex = """(?:([^!]+)!)?([A-Z]+)(\d+)""".r
  private val RangePattern: Regex = """(?:([^!]+)!)?([A-Z]+)(\d+):([A-Z]+)(\d+)""".r

  def parseA1(reference: String): Option[ExcelReference] =
    reference.trim match
      case CellPattern(sheet, colStr, rowStr) =>
        for
          col <- Col.fromString(colStr)
          row <- Row.fromString(rowStr)
        yield Cell(Option(sheet).getOrElse(""), row, col)

      case RangePattern(sheet, startColStr, startRowStr, endColStr, endRowStr) =>
        for
          startCol <- Col.fromString(startColStr)
          startRow <- Row.fromString(startRowStr)
          endCol <- Col.fromString(endColStr)
          endRow <- Row.fromString(endRowStr)
          sheetName = Option(sheet).getOrElse("")
        yield Range(Cell(sheetName, startRow, startCol), Cell(sheetName, endRow, endCol))

      case name if name.matches("""[A-Za-z_]\w*""") =>
        Some(Named(name))

      case _ => None

  // Validation helpers
  private def validateRow(value: Int): Int =
    require(value >= 0, s"Row index must be non-negative, got $value")
    require(value < MaxRows, s"Row index must be less than $MaxRows, got $value")
    value

  extension (cell: Cell)
    def shift(rowOffset: Int, colOffset: Int): Cell =
      Cell(
        cell.sheet,
        Row(validateRow(cell.row.value + rowOffset)),
        Col(validateCol(cell.col.value + colOffset))
      )
    def relative(other: Cell): (Int, Int) =
      (other.row.value - cell.row.value, other.col.value - cell.col.value)
    def isLeftOf(other: Cell): Boolean =
      cell.sheet == other.sheet && cell.col.value < other.col.value
    def isAbove(other: Cell): Boolean =
      cell.sheet == other.sheet && cell.row.value < other.row.value

  extension (range: Range)
    def sheet: String = range.start.sheet
    def contains(cell: Cell): Boolean =
      cell.sheet == range.sheet &&
        cell.row.value >= range.start.row.value && cell.row.value <= range.end.row.value &&
        cell.col.value >= range.start.col.value && cell.col.value <= range.end.col.value
    def cells: Iterator[Cell] = for
      r <- (range.start.row.value to range.end.row.value).iterator
      c <- (range.start.col.value to range.end.col.value).iterator
    yield Cell(range.sheet, Row(r), Col(c))
    def width: Int = range.end.col.value - range.start.col.value + 1
    def height: Int = range.end.row.value - range.start.row.value + 1
    def size: Int = width * height

  private def validateCol(value: Int): Int =
    require(value >= 0, s"Column index must be non-negative, got $value")
    require(value < MaxCols, s"Column index must be less than $MaxCols, got $value")
    value

  private def createCell(sheet: Option[String], col: Col, row: Row): Cell =
    Cell(sheet.getOrElse(""), row, col)

  object Row:
    def fromString(s: String): Option[Row] =
      s.toIntOption.filter(_ >= 0).map(Row(_))

    def apply(value: Int): Row =
      validateRow(value)

    extension (row: Row)
      def value: Int = row
      def next: Row = Row(row + 1)
      def prev: Row = Row(math.max(0, row - 1))

  object Col:
    def apply(value: Int): Col =
      validateCol(value)

    def fromString(s: String): Option[Col] =
      val colValue = ExcelUtils.stringToColumn(s)
      Some(validateCol(colValue))

    extension (col: Col)
      def value: Int = col
      def next: Col = Col(col + 1)
      def prev: Col = Col(math.max(0, col - 1))
