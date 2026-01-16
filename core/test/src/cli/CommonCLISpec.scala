package com.tjclp.xlcr
package cli

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import types.MimeType

class CommonCLISpec extends AnyFlatSpec with Matchers {

  "parseMimeOrExtension" should "parse known MIME types" in {
    CommonCLI.parseMimeOrExtension("application/pdf") shouldBe Some(MimeType.ApplicationPdf)
    CommonCLI.parseMimeOrExtension("text/plain") shouldBe Some(MimeType.TextPlain)
    CommonCLI.parseMimeOrExtension("application/json") shouldBe Some(MimeType.ApplicationJson)
  }

  it should "parse known file extensions" in {
    CommonCLI.parseMimeOrExtension("pdf") shouldBe Some(MimeType.ApplicationPdf)
    CommonCLI.parseMimeOrExtension("txt") shouldBe Some(MimeType.TextPlain)
    CommonCLI.parseMimeOrExtension("json") shouldBe Some(MimeType.ApplicationJson)
    CommonCLI.parseMimeOrExtension("xlsx") shouldBe Some(
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
    )
  }

  it should "parse extensions with dots" in {
    CommonCLI.parseMimeOrExtension(".pdf") shouldBe Some(MimeType.ApplicationPdf)
    CommonCLI.parseMimeOrExtension(".txt") shouldBe Some(MimeType.TextPlain)
  }

  it should "return None for unknown types" in {
    // Unknown file extensions return None
    CommonCLI.parseMimeOrExtension("unknown") shouldBe None
    CommonCLI.parseMimeOrExtension(".xyz") shouldBe None

    // Unknown but valid MIME types return None (not in predefined list)
    CommonCLI.parseMimeOrExtension("application/unknown") shouldBe None

    // Empty string returns None
    CommonCLI.parseMimeOrExtension("") shouldBe None
  }

  it should "handle null input" in {
    // Null now returns None
    CommonCLI.parseMimeOrExtension(null) shouldBe None
  }

  "parseMimeMappings" should "parse valid mime mappings" in {
    val mappings = Seq(
      "pdf=json",
      "application/pdf=application/json",
      "xlsx=pdf"
    )

    val result = CommonCLI.parseMimeMappings(mappings)

    // The test expects 3 mappings, but "pdf=json" and "application/pdf=application/json"
    // both map to the same key (ApplicationPdf), so we only get 2 entries in the map
    result should have size 2 // Changed from 3
    result(MimeType.ApplicationPdf) shouldBe MimeType.ApplicationJson
    result(MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet) shouldBe MimeType.ApplicationPdf
  }

  it should "skip invalid mappings" in {
    val mappings = Seq(
      "pdf=json",
      "invalid",
      "=json",
      "pdf=",
      "unknown=pdf"
    )

    val result = CommonCLI.parseMimeMappings(mappings)
    result should have size 1
    result(MimeType.ApplicationPdf) shouldBe MimeType.ApplicationJson
  }

  it should "handle empty mappings" in {
    CommonCLI.parseMimeMappings(Seq.empty) shouldBe Map.empty
  }

  "parseOutputMimeType" should "prioritize outputType over format" in {
    val result = CommonCLI.parseOutputMimeType(Some("pdf"), Some("png"))
    result shouldBe Some(MimeType.ApplicationPdf)
  }

  it should "use format when outputType is not provided" in {
    val result = CommonCLI.parseOutputMimeType(None, Some("png"))
    result shouldBe Some(MimeType.ImagePng)
  }

  it should "return None when neither is provided" in {
    CommonCLI.parseOutputMimeType(None, None) shouldBe None
  }

  it should "handle special format values" in {
    CommonCLI.parseOutputMimeType(None, Some("jpg")) shouldBe Some(MimeType.ImageJpeg)
    CommonCLI.parseOutputMimeType(None, Some("jpeg")) shouldBe Some(MimeType.ImageJpeg)
  }

  "parseChunkRange" should "parse single values" in {
    CommonCLI.parseChunkRange("5") shouldBe Some(5 to 5)
    CommonCLI.parseChunkRange("0") shouldBe Some(0 to 0)
    CommonCLI.parseChunkRange("100") shouldBe Some(100 to 100)
  }

  it should "parse closed ranges" in {
    CommonCLI.parseChunkRange("0-4") shouldBe Some(0 until 5)
    CommonCLI.parseChunkRange("10-20") shouldBe Some(10 until 21)
    CommonCLI.parseChunkRange("5-5") shouldBe Some(5 until 6)
  }

  it should "parse open-ended ranges" in {
    val result = CommonCLI.parseChunkRange("95-")
    result should be(defined)
    result.get.start shouldBe 95
    result.get.end shouldBe Int.MaxValue
  }

  it should "handle comma-separated values by taking the first" in {
    CommonCLI.parseChunkRange("0-4,10-14") shouldBe Some(0 until 5)
    CommonCLI.parseChunkRange("5,10,15") shouldBe Some(5 to 5)
  }

  it should "handle whitespace" in {
    CommonCLI.parseChunkRange(" 5 ") shouldBe Some(5 to 5)
    CommonCLI.parseChunkRange(" 0 - 4 ") shouldBe Some(0 until 5)
    CommonCLI.parseChunkRange(" 95 - ") should be(defined)
  }

  it should "return None for invalid formats" in {
    CommonCLI.parseChunkRange("") shouldBe None
    CommonCLI.parseChunkRange("abc") shouldBe None
    CommonCLI.parseChunkRange("5-4") shouldBe None // Invalid range
    CommonCLI.parseChunkRange("-5") shouldBe None
    CommonCLI.parseChunkRange("--") shouldBe None
    CommonCLI.parseChunkRange("5-10-15") shouldBe None
  }

  "BaseConfig" should "have sensible defaults" in {
    val config = CommonCLI.BaseConfig()
    config.input shouldBe ""
    config.output shouldBe ""
    config.diffMode shouldBe false
    config.splitMode shouldBe false
    config.splitStrategy shouldBe None
    config.outputType shouldBe None
    config.outputFormat shouldBe None
    config.maxImageWidth shouldBe 2000
    config.maxImageHeight shouldBe 2000
    config.maxImageSizeBytes shouldBe (5 * 1024 * 1024)
    config.imageDpi shouldBe 300
    config.jpegQuality shouldBe 0.85f
    config.recursiveExtraction shouldBe false
    config.maxRecursionDepth shouldBe 5
    config.mappings shouldBe Seq.empty
    config.failureMode shouldBe None
    config.failureContext shouldBe Map.empty
    config.chunkRange shouldBe None
  }

  "baseParser" should "create a valid parser" in {
    val parser = CommonCLI.baseParser[CommonCLI.BaseConfig]("test-prog", "1.0")
    // Basic test that parser exists and doesn't throw
    parser should not be null

    // Test parsing some basic arguments
    import scopt.OParser
    val args   = Array("-i", "input.txt", "-o", "output.txt")
    val result = OParser.parse(parser, args, CommonCLI.BaseConfig())

    result should be(defined)
    result.get.input shouldBe "input.txt"
    result.get.output shouldBe "output.txt"
  }

  it should "parse all supported options" in {
    val parser = CommonCLI.baseParser[CommonCLI.BaseConfig]("test-prog", "1.0")
    import scopt.OParser

    val args = Array(
      "-i",
      "input.xlsx",
      "-o",
      "output.pdf",
      "--split",
      "--strategy",
      "sheet",
      "--type",
      "pdf",
      "--format",
      "png",
      "--max-width",
      "1000",
      "--max-height",
      "800",
      "--max-size",
      "1048576",
      "--dpi",
      "150",
      "--quality",
      "0.9",
      "--recursive",
      "--max-recursion-depth",
      "3",
      "--failure-mode",
      "throw",
      "--chunk-range",
      "0-4"
    )

    val result = OParser.parse(parser, args, CommonCLI.BaseConfig())
    result should be(defined)

    val config = result.get
    config.input shouldBe "input.xlsx"
    config.output shouldBe "output.pdf"
    config.splitMode shouldBe true
    config.splitStrategy shouldBe Some("sheet")
    config.outputType shouldBe Some("pdf")
    config.outputFormat shouldBe Some("png")
    config.maxImageWidth shouldBe 1000
    config.maxImageHeight shouldBe 800
    config.maxImageSizeBytes shouldBe 1048576
    config.imageDpi shouldBe 150
    config.jpegQuality shouldBe 0.9f
    config.recursiveExtraction shouldBe true
    config.maxRecursionDepth shouldBe 3
    config.failureMode shouldBe Some("throw")
    config.chunkRange shouldBe Some("0-4")
  }
}
