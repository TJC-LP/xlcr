package com.tjclp.xlcr.output

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import com.tjclp.xlcr.types.Mime

class MimeExtensionsSpec extends AnyFlatSpec with Matchers {

  // ============================================================================
  // getExtension (String) tests
  // ============================================================================

  "getExtension(String)" should "return correct extension for PDF" in {
    MimeExtensions.getExtension("application/pdf") shouldBe Some("pdf")
  }

  it should "return correct extension for text types" in {
    MimeExtensions.getExtension("text/plain") shouldBe Some("txt")
    MimeExtensions.getExtension("text/html") shouldBe Some("html")
    MimeExtensions.getExtension("text/csv") shouldBe Some("csv")
    MimeExtensions.getExtension("text/markdown") shouldBe Some("md")
  }

  it should "return correct extension for MS Office formats" in {
    MimeExtensions.getExtension("application/msword") shouldBe Some("doc")
    MimeExtensions.getExtension("application/vnd.ms-excel") shouldBe Some("xls")
    MimeExtensions.getExtension("application/vnd.ms-powerpoint") shouldBe Some("ppt")
  }

  it should "return correct extension for Office Open XML formats" in {
    MimeExtensions.getExtension(
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    ) shouldBe Some("docx")
    MimeExtensions.getExtension(
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    ) shouldBe Some("xlsx")
    MimeExtensions.getExtension(
      "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    ) shouldBe Some("pptx")
  }

  it should "return correct extension for OpenDocument formats" in {
    MimeExtensions.getExtension("application/vnd.oasis.opendocument.text") shouldBe Some("odt")
    MimeExtensions.getExtension("application/vnd.oasis.opendocument.spreadsheet") shouldBe Some(
      "ods"
    )
    MimeExtensions.getExtension("application/vnd.oasis.opendocument.presentation") shouldBe Some(
      "odp"
    )
  }

  it should "return correct extension for image types" in {
    MimeExtensions.getExtension("image/jpeg") shouldBe Some("jpg")
    MimeExtensions.getExtension("image/png") shouldBe Some("png")
    MimeExtensions.getExtension("image/gif") shouldBe Some("gif")
    MimeExtensions.getExtension("image/svg+xml") shouldBe Some("svg")
  }

  it should "return None for unknown MIME types" in {
    MimeExtensions.getExtension("application/unknown") shouldBe None
    MimeExtensions.getExtension("x-custom/type") shouldBe None
  }

  it should "handle MIME types with parameters (charset etc)" in {
    MimeExtensions.getExtension("text/html; charset=utf-8") shouldBe Some("html")
    MimeExtensions.getExtension("application/json; charset=utf-8") shouldBe Some("json")
  }

  it should "be case insensitive" in {
    MimeExtensions.getExtension("APPLICATION/PDF") shouldBe Some("pdf")
    MimeExtensions.getExtension("Text/Plain") shouldBe Some("txt")
  }

  // ============================================================================
  // getExtension (Mime) tests
  // ============================================================================

  "getExtension(Mime)" should "work with Mime types" in {
    MimeExtensions.getExtension(Mime.pdf) shouldBe Some("pdf")
    MimeExtensions.getExtension(Mime.html) shouldBe Some("html")
    MimeExtensions.getExtension(Mime.xlsx) shouldBe Some("xlsx")
  }

  // ============================================================================
  // getExtensionOrDefault tests
  // ============================================================================

  "getExtensionOrDefault" should "return extension for known types" in {
    MimeExtensions.getExtensionOrDefault("application/pdf") shouldBe "pdf"
    MimeExtensions.getExtensionOrDefault(Mime.xlsx) shouldBe "xlsx"
  }

  it should "return default for unknown types" in {
    MimeExtensions.getExtensionOrDefault("application/unknown") shouldBe "bin"
    MimeExtensions.getExtensionOrDefault("x-custom/type", "dat") shouldBe "dat"
  }
}
