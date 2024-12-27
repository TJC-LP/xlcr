package com.tjclp.xlcr
package domain

import com.tjclp.xlcr.models.ExcelReference
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ExcelReferenceSpec extends AnyFlatSpec with Matchers {

  "ExcelReference.parseA1" should "parse a simple cell reference" in {
    ExcelReference.parseA1("A1") shouldBe Some(ExcelReference.Cell("", ExcelReference.Row(1), ExcelReference.Col(0)))
  }

  it should "parse a cell reference with a sheet name" in {
    ExcelReference.parseA1("Sheet1!B2") shouldBe Some(ExcelReference.Cell("Sheet1", ExcelReference.Row(2), ExcelReference.Col(1)))
  }

  it should "parse a range reference" in {
    val expected = Some(ExcelReference.Range(
      ExcelReference.Cell("", ExcelReference.Row(1), ExcelReference.Col(0)),
      ExcelReference.Cell("", ExcelReference.Row(2), ExcelReference.Col(1))
    ))
    ExcelReference.parseA1("A1:B2") shouldBe expected
  }

  it should "parse a range reference with a sheet name" in {
    val expected = Some(ExcelReference.Range(
      ExcelReference.Cell("Sheet1", ExcelReference.Row(1), ExcelReference.Col(0)),
      ExcelReference.Cell("Sheet1", ExcelReference.Row(2), ExcelReference.Col(1))
    ))
    ExcelReference.parseA1("Sheet1!A1:B2") shouldBe expected
  }

  it should "parse a named reference" in {
    ExcelReference.parseA1("MyNamedRange") shouldBe Some(ExcelReference.Named("MyNamedRange"))
  }

  it should "return None for invalid input" in {
    ExcelReference.parseA1("Invalid!") shouldBe None
  }

  it should "handle multi-letter column references" in {
    ExcelReference.parseA1("AA1") shouldBe Some(ExcelReference.Cell("", ExcelReference.Row(1), ExcelReference.Col(26)))
  }

  "ExcelReference.toA1" should "convert a cell reference to A1 notation" in {
    val cell = ExcelReference.Cell("Sheet1", ExcelReference.Row(1), ExcelReference.Col(0))
    cell.toA1 shouldBe "Sheet1!A1"
  }

  it should "convert a range reference to A1 notation" in {
    val range = ExcelReference.Range(
      ExcelReference.Cell("Sheet1", ExcelReference.Row(1), ExcelReference.Col(0)),
      ExcelReference.Cell("Sheet1", ExcelReference.Row(2), ExcelReference.Col(1))
    )
    range.toA1 shouldBe "Sheet1!A1:Sheet1!B2"
  }

  it should "convert a named reference to its name" in {
    val named = ExcelReference.Named("MyRange")
    named.toA1 shouldBe "MyRange"
  }

  "ExcelReference.Cell" should "shift correctly" in {
    val cell: ExcelReference.Cell = ExcelReference.Cell("Sheet1", ExcelReference.Row(1), ExcelReference.Col(0))
    cell.shift(1, 1) shouldBe ExcelReference.Cell("Sheet1", ExcelReference.Row(2), ExcelReference.Col(1))
  }

  "ExcelReference.Range" should "calculate width, height, and size correctly" in {
    val range: ExcelReference.Range = ExcelReference.Range(
      ExcelReference.Cell("Sheet1", ExcelReference.Row(1), ExcelReference.Col(0)),
      ExcelReference.Cell("Sheet1", ExcelReference.Row(3), ExcelReference.Col(2))
    )
    range.width shouldBe 3
    range.height shouldBe 3
    range.size shouldBe 9
  }

  it should "check if it contains a cell correctly" in {
    val range: ExcelReference.Range = ExcelReference.Range(
      ExcelReference.Cell("Sheet1", ExcelReference.Row(1), ExcelReference.Col(0)),
      ExcelReference.Cell("Sheet1", ExcelReference.Row(3), ExcelReference.Col(2))
    )
    val insideCell: ExcelReference.Cell = ExcelReference.Cell("Sheet1", ExcelReference.Row(2), ExcelReference.Col(1))
    val outsideCell: ExcelReference.Cell = ExcelReference.Cell("Sheet1", ExcelReference.Row(4), ExcelReference.Col(3))

    range.contains(insideCell) shouldBe true
    range.contains(outsideCell) shouldBe false
  }
}
