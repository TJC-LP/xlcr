package com.tjclp.xlcr
package models

import models.excel.ExcelReference
import models.excel.ExcelReference.{Cell, Row, Col, Range, Named}
import utils.excel.ExcelUtils

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ExcelReferenceSpec extends AnyFlatSpec with Matchers {

  "parseA1" should "parse a simple cell reference" in {
    ExcelReference.parseA1("A1") shouldBe Some(Cell("", Row(1), Col(0)))
  }

  it should "parse a cell reference with a sheet name" in {
    ExcelReference.parseA1("Sheet1!B2") shouldBe Some(Cell("Sheet1", Row(2), Col(1)))
  }

  it should "parse a range reference" in {
    val expected = Some(Range(
      Cell("", Row(1), Col(0)),
      Cell("", Row(2), Col(1))
    ))
    ExcelReference.parseA1("A1:B2") shouldBe expected
  }

  it should "parse a range reference with a sheet name" in {
    val expected = Some(Range(
      Cell("Sheet1", Row(1), Col(0)),
      Cell("Sheet1", Row(2), Col(1))
    ))
    ExcelReference.parseA1("Sheet1!A1:B2") shouldBe expected
  }

  it should "parse a named reference" in {
    ExcelReference.parseA1("MyNamedRange") shouldBe Some(Named("MyNamedRange"))
  }

  it should "return None for invalid input" in {
    ExcelReference.parseA1("Invalid!") shouldBe None
  }

  it should "handle multi-letter column references" in {
    ExcelReference.parseA1("AA1") shouldBe Some(Cell("", Row(1), Col(26)))
  }

  "toA1" should "convert a cell reference to A1 notation" in {
    val cell = Cell("Sheet1", Row(1), Col(0))
    cell.toA1 shouldBe "Sheet1!A1"
  }

  it should "convert a range reference to A1 notation" in {
    val range = Range(
      Cell("Sheet1", Row(1), Col(0)),
      Cell("Sheet1", Row(2), Col(1))
    )
    range.toA1 shouldBe "Sheet1!A1:Sheet1!B2"
  }

  it should "convert a named reference to its name" in {
    val named = Named("MyRange")
    named.toA1 shouldBe "MyRange"
  }

  "Cell" should "shift correctly" in {
    val cell: Cell = Cell("Sheet1", Row(1), Col(0))
    cell.shift(1, 1) shouldBe Cell("Sheet1", Row(2), Col(1))
  }

  "Range" should "calculate width, height, and size correctly" in {
    val range: Range = Range(
      Cell("Sheet1", Row(1), Col(0)),
      Cell("Sheet1", Row(3), Col(2))
    )
    range.width shouldBe 3
    range.height shouldBe 3
    range.size shouldBe 9
  }

  it should "check if it contains a cell correctly" in {
    val range: Range = Range(
      Cell("Sheet1", Row(1), Col(0)),
      Cell("Sheet1", Row(3), Col(2))
    )
    val insideCell: Cell = Cell("Sheet1", Row(2), Col(1))
    val outsideCell: Cell = Cell("Sheet1", Row(4), Col(3))

    range.contains(insideCell) shouldBe true
    range.contains(outsideCell) shouldBe false
  }

  it should "handle edge cases in column references" in {
    val maxCol = Col(16383) // Max Excel column (XFD)
    ExcelUtils.columnToString(maxCol) shouldBe "XFD"

    val ex = intercept[IllegalArgumentException] {
      Col(16384) // One more than max
    }
    ex.getMessage should include("must be less than 16384")
  }

  it should "handle complex range operations" in {
    val range: Range = Range(
      Cell("Sheet1", Row(1), Col(0)),
      Cell("Sheet1", Row(3), Col(2))
    )

    // Test range properties
    range.width shouldBe 3
    range.height shouldBe 3
    range.size shouldBe 9

    // Test cell iteration
    val cells = range.cells.toList
    cells.size shouldBe 9
    cells.head.toA1 shouldBe "Sheet1!A1"
    cells.last.toA1 shouldBe "Sheet1!C3"
  }

  it should "validate cell containment in ranges" in {
    val range: Range = Range(
      Cell("Sheet1", Row(1), Col(0)),
      Cell("Sheet1", Row(3), Col(2))
    )

    val insideCell: Cell = Cell("Sheet1", Row(2), Col(1))
    val outsideCell: Cell = Cell("Sheet1", Row(4), Col(3))
    val wrongSheetCell: Cell = Cell("Sheet2", Row(2), Col(1))

    range.contains(insideCell) shouldBe true
    range.contains(outsideCell) shouldBe false
    range.contains(wrongSheetCell) shouldBe false
  }

  it should "handle relative cell positions" in {
    val cell1: Cell = Cell("Sheet1", Row(1), Col(1))
    val cell2: Cell = Cell("Sheet1", Row(3), Col(4))

    val (rowDiff, colDiff) = cell1.relative(cell2)
    rowDiff shouldBe 2
    colDiff shouldBe 3

    cell1.isLeftOf(cell2) shouldBe true
    cell1.isAbove(cell2) shouldBe true
  }
}