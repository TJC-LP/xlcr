package com.tjclp.xlcr.v2.output

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FragmentNamingSpec extends AnyFlatSpec with Matchers {

  // ============================================================================
  // paddingWidth tests
  // ============================================================================

  "paddingWidth" should "return 1 for 0 or negative totals" in {
    FragmentNaming.paddingWidth(0) shouldBe 1
    FragmentNaming.paddingWidth(-5) shouldBe 1
  }

  it should "return 1 for totals 1-9" in {
    FragmentNaming.paddingWidth(1) shouldBe 1
    FragmentNaming.paddingWidth(5) shouldBe 1
    FragmentNaming.paddingWidth(9) shouldBe 1
  }

  it should "return 2 for totals 10-99" in {
    FragmentNaming.paddingWidth(10) shouldBe 2
    FragmentNaming.paddingWidth(50) shouldBe 2
    FragmentNaming.paddingWidth(99) shouldBe 2
  }

  it should "return 3 for totals 100-999" in {
    FragmentNaming.paddingWidth(100) shouldBe 3
    FragmentNaming.paddingWidth(500) shouldBe 3
    FragmentNaming.paddingWidth(999) shouldBe 3
  }

  it should "return 4 for totals 1000-9999" in {
    FragmentNaming.paddingWidth(1000) shouldBe 4
    FragmentNaming.paddingWidth(5000) shouldBe 4
    FragmentNaming.paddingWidth(9999) shouldBe 4
  }

  // ============================================================================
  // padIndex tests
  // ============================================================================

  "padIndex" should "pad indices correctly for single digit totals" in {
    FragmentNaming.padIndex(1, 5) shouldBe "1"
    FragmentNaming.padIndex(5, 9) shouldBe "5"
  }

  it should "pad indices correctly for double digit totals" in {
    FragmentNaming.padIndex(1, 15) shouldBe "01"
    FragmentNaming.padIndex(3, 15) shouldBe "03"
    FragmentNaming.padIndex(15, 15) shouldBe "15"
    FragmentNaming.padIndex(1, 99) shouldBe "01"
  }

  it should "pad indices correctly for triple digit totals" in {
    FragmentNaming.padIndex(1, 100) shouldBe "001"
    FragmentNaming.padIndex(42, 100) shouldBe "042"
    FragmentNaming.padIndex(100, 100) shouldBe "100"
  }

  it should "pad indices correctly for quad digit totals" in {
    FragmentNaming.padIndex(1, 1000) shouldBe "0001"
    FragmentNaming.padIndex(7, 1000) shouldBe "0007"
    FragmentNaming.padIndex(500, 1000) shouldBe "0500"
    FragmentNaming.padIndex(1000, 1000) shouldBe "1000"
  }

  // ============================================================================
  // sanitizeName tests
  // ============================================================================

  "sanitizeName" should "pass through simple names unchanged" in {
    FragmentNaming.sanitizeName("Sheet1") shouldBe "Sheet1"
    FragmentNaming.sanitizeName("Page") shouldBe "Page"
  }

  it should "preserve spaces for lineage tracking" in {
    FragmentNaming.sanitizeName("Sheet 1") shouldBe "Sheet 1"
    FragmentNaming.sanitizeName("Q1 Results 2024") shouldBe "Q1 Results 2024"
  }

  it should "replace only filesystem-invalid characters with underscores" in {
    FragmentNaming.sanitizeName("File/Name") shouldBe "File_Name"
    FragmentNaming.sanitizeName("File\\Name") shouldBe "File_Name"
    FragmentNaming.sanitizeName("File:Name") shouldBe "File_Name"
    FragmentNaming.sanitizeName("File*Name") shouldBe "File_Name"
    FragmentNaming.sanitizeName("File?Name") shouldBe "File_Name"
    FragmentNaming.sanitizeName("File\"Name") shouldBe "File_Name"
    FragmentNaming.sanitizeName("File<Name") shouldBe "File_Name"
    FragmentNaming.sanitizeName("File>Name") shouldBe "File_Name"
    FragmentNaming.sanitizeName("File|Name") shouldBe "File_Name"
  }

  it should "preserve underscores and dots" in {
    FragmentNaming.sanitizeName("File__Name") shouldBe "File__Name"
    FragmentNaming.sanitizeName("File.backup") shouldBe "File.backup"
    FragmentNaming.sanitizeName("my_sheet_1") shouldBe "my_sheet_1"
  }

  it should "preserve leading and trailing characters" in {
    FragmentNaming.sanitizeName("_Name") shouldBe "_Name"
    FragmentNaming.sanitizeName("Name_") shouldBe "Name_"
    FragmentNaming.sanitizeName("  Name  ") shouldBe "  Name  "
  }

  it should "limit length to 200 characters" in {
    val longName = "A" * 250
    FragmentNaming.sanitizeName(longName) shouldBe ("A" * 200)
  }

  it should "preserve unicode characters" in {
    FragmentNaming.sanitizeName("シート1") shouldBe "シート1"
    FragmentNaming.sanitizeName("Données 2024") shouldBe "Données 2024"
  }

  // ============================================================================
  // buildFilename tests
  // ============================================================================

  "buildFilename" should "build correct filename for single digit totals" in {
    FragmentNaming.buildFilename(1, 5, "Sheet 1", "xlsx") shouldBe "1__Sheet 1.xlsx"
  }

  it should "build correct filename for double digit totals" in {
    FragmentNaming.buildFilename(3, 15, "Q1 Results", "xlsx") shouldBe "03__Q1 Results.xlsx"
  }

  it should "build correct filename for triple digit totals" in {
    FragmentNaming.buildFilename(42, 100, "Page 42", "pdf") shouldBe "042__Page 42.pdf"
  }

  it should "build correct filename for quad digit totals" in {
    FragmentNaming.buildFilename(7, 1000, "Slide 7", "pptx") shouldBe "0007__Slide 7.pptx"
  }

  it should "handle extensions with or without leading dot" in {
    FragmentNaming.buildFilename(1, 5, "test", ".pdf") shouldBe "1__test.pdf"
    FragmentNaming.buildFilename(1, 5, "test", "pdf") shouldBe "1__test.pdf"
  }

  it should "only sanitize filesystem-invalid characters" in {
    FragmentNaming.buildFilename(1, 10, "Sheet: Q1/Q2", "xlsx") shouldBe "01__Sheet_ Q1_Q2.xlsx"
  }
}
