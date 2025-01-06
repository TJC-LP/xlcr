package com.tjclp.xlcr
package utils.excel

import com.tjclp.xlcr.models.excel.ExcelReference
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ExcelUtilsSpec extends AnyFlatSpec with Matchers {

  "ExcelUtils" should "convert column index to string representation" in {
    ExcelUtils.columnToString(ExcelReference.Col(0)) shouldBe "A"
    ExcelUtils.columnToString(ExcelReference.Col(25)) shouldBe "Z"
    ExcelUtils.columnToString(ExcelReference.Col(26)) shouldBe "AA"
    ExcelUtils.columnToString(ExcelReference.Col(51)) shouldBe "AZ"
    ExcelUtils.columnToString(ExcelReference.Col(52)) shouldBe "BA"
    ExcelUtils.columnToString(ExcelReference.Col(701)) shouldBe "ZZ"
    ExcelUtils.columnToString(ExcelReference.Col(702)) shouldBe "AAA"
  }

  it should "convert column string to index (Col)" in {
    ExcelUtils.stringToColumn("A").value shouldBe 0
    ExcelUtils.stringToColumn("Z").value shouldBe 25
    ExcelUtils.stringToColumn("AA").value shouldBe 26
    ExcelUtils.stringToColumn("AZ").value shouldBe 51
    ExcelUtils.stringToColumn("BA").value shouldBe 52
    ExcelUtils.stringToColumn("ZZ").value shouldBe 701
    ExcelUtils.stringToColumn("AAA").value shouldBe 702
  }

  it should "maintain consistency between conversions" in {
    val testCases = 0 to 1000
    for (index <- testCases) {
      val col = ExcelReference.Col(index)
      val str = ExcelUtils.columnToString(col)
      ExcelUtils.stringToColumn(str).value shouldBe index
    }
  }

  it should "throw an exception for empty or invalid column strings" in {
    an[IllegalArgumentException] should be thrownBy ExcelUtils.stringToColumn("")
    an[IllegalArgumentException] should be thrownBy ExcelUtils.stringToColumn("123")
    an[IllegalArgumentException] should be thrownBy ExcelUtils.stringToColumn("A1")
  }

  it should "throw an exception for negative column indices" in {
    an[IllegalArgumentException] should be thrownBy ExcelUtils.columnToString(ExcelReference.Col(-1))
  }

  it should "handle extremely large column indices" in {
    val maxColIndex = 16383 // Excel's maximum column index
    val col = ExcelReference.Col(maxColIndex)
    val str = ExcelUtils.columnToString(col)
    ExcelUtils.stringToColumn(str).value shouldBe maxColIndex
  }

  it should "throw exception for negative column indices" in {
    val ex = intercept[IllegalArgumentException] {
      ExcelUtils.columnToString(ExcelReference.Col(-1))
    }
    ex.getMessage should include("negative")
  }

  it should "handle column strings of varying lengths consistently" in {
    val testCases = List(
      ("A", 0),
      ("Z", 25),
      ("AA", 26),
      ("ZZ", 701),
      ("AAA", 702),
      ("XFD", 16383) // Excel's maximum column
    )

    for ((str, index) <- testCases) {
      ExcelUtils.stringToColumn(str).value shouldBe index
      ExcelUtils.columnToString(ExcelReference.Col(index)) shouldBe str
    }
  }

  it should "validate Excel's column limitations" in {
    val maxValidCol = ExcelReference.Col(16383) // XFD
    ExcelUtils.columnToString(maxValidCol) shouldBe "XFD"

    val ex = intercept[IllegalArgumentException] {
      ExcelUtils.columnToString(ExcelReference.Col(16384))
    }
    ex.getMessage should include("must be less than 16384")
  }

  it should "handle invalid column strings appropriately" in {
    val invalidInputs = List(
      "", // Empty string
      "123", // Numbers
      "A1", // Mixed with numbers
      "XFDA", // Too long
      "ABC123", // Mixed format
      "$A" // Special characters
    )

    for (invalid <- invalidInputs) {
      an[IllegalArgumentException] should be thrownBy ExcelUtils.stringToColumn(invalid)
    }
  }
}