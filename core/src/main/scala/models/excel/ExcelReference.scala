package com.tjclp.xlcr
package models.excel

import utils.excel.ExcelUtils

import scala.util.matching.Regex

sealed trait ExcelReference {
  def toA1: String = this match {
    case ExcelReference.Cell(sheet, row, col) => s"$sheet!${ExcelUtils.columnToString(col)}$row"
    case ExcelReference.Range(start, end) => s"${start.toA1}:${end.toA1}"
    case ExcelReference.Named(name) => name
  }
}

object ExcelReference {
  case class Cell(sheet: String, row: Row, col: Col) extends ExcelReference
  case class Range(start: Cell, end: Cell) extends ExcelReference
  case class Named(name: String) extends ExcelReference

  type Row = Int
  type Col = Int
  private val MaxRows = 1048576 // 2^20
  private val MaxCols = 16384 // 2^14, column "XFD"

  private val CellPattern: Regex = """(?:([^!]+)!)?([A-Z]+)(\d+)""".r
  private val RangePattern: Regex = """(?:([^!]+)!)?([A-Z]+)(\d+):([A-Z]+)(\d+)""".r

  def parseA1(reference: String): Option[ExcelReference] = {
    reference.trim match {
      case CellPattern(sheet, colStr, rowStr) =>
        for {
          col <- Col.fromString(colStr)
          row <- Row.fromString(rowStr)
        } yield Cell(Option(sheet).getOrElse(""), row, col)

      case RangePattern(sheet, startColStr, startRowStr, endColStr, endRowStr) =>
        for {
          startCol <- Col.fromString(startColStr)
          startRow <- Row.fromString(startRowStr)
          endCol <- Col.fromString(endColStr)
          endRow <- Row.fromString(endRowStr)
          sheetName = Option(sheet).getOrElse("")
        } yield Range(Cell(sheetName, startRow, startCol), Cell(sheetName, endRow, endCol))

      case name if name.matches("""[A-Za-z_]\w*""") =>
        Some(Named(name))

      case _ => None
    }
  }

  // Validation helpers
  private def validateRow(value: Int): Int = {
    require(value >= 0, s"Row index must be non-negative, got $value")
    require(value < MaxRows, s"Row index must be less than $MaxRows, got $value")
    value
  }

  private def validateCol(value: Int): Int = {
    require(value >= 0, s"Column index must be non-negative, got $value")
    require(value < MaxCols, s"Column index must be less than $MaxCols, got $value")
    value
  }

  private def createCell(sheet: Option[String], col: Col, row: Row): Cell = {
    Cell(sheet.getOrElse(""), row, col)
  }

  // Cell extension methods
  implicit class CellOps(val cell: Cell) extends AnyVal {
    def shift(rowOffset: Int, colOffset: Int): Cell = {
      Cell(
        cell.sheet,
        Row(validateRow(cell.row + rowOffset)),
        Col(validateCol(cell.col + colOffset))
      )
    }

    def relative(other: Cell): (Int, Int) = {
      (other.row - cell.row, other.col - cell.col)
    }

    def isLeftOf(other: Cell): Boolean = {
      cell.sheet == other.sheet && cell.col < other.col
    }

    def isAbove(other: Cell): Boolean = {
      cell.sheet == other.sheet && cell.row < other.row
    }
  }

  // Range extension methods
  implicit class RangeOps(val range: Range) extends AnyVal {
    def sheet: String = range.start.sheet

    def contains(cell: Cell): Boolean = {
      cell.sheet == range.sheet &&
        cell.row >= range.start.row && cell.row <= range.end.row &&
        cell.col >= range.start.col && cell.col <= range.end.col
    }

    def cells: Iterator[Cell] = for {
      r <- (range.start.row to range.end.row).iterator
      c <- (range.start.col to range.end.col).iterator
    } yield Cell(range.sheet, Row(r), Col(c))

    def width: Int = range.end.col - range.start.col + 1
    def height: Int = range.end.row - range.start.row + 1
    def size: Int = width * height
  }

  object Row {
    def fromString(s: String): Option[Row] = {
        // Use scala.collection.compat for toIntOption
      import scala.collection.compat._
      s.toIntOption.filter(_ >= 0).map(Row(_))
    }

    def apply(value: Int): Row = {
      validateRow(value)
    }

    implicit class RowOps(val row: Row) extends AnyVal {
      def value: Int = row
      def next: Row = Row(row + 1)
      def prev: Row = Row(math.max(0, row - 1))
    }
  }

  object Col {
    def apply(value: Int): Col = {
      validateCol(value)
    }

    def fromString(s: String): Option[Col] = {
      val colValue = ExcelUtils.stringToColumn(s)
      Some(validateCol(colValue))
    }

    implicit class ColOps(val col: Col) extends AnyVal {
      def value: Int = col
      def next: Col = Col(col + 1)
      def prev: Col = Col(math.max(0, col - 1))
    }
  }
}
