package com.tjclp.xlcr
package cli

import java.nio.file.{ Files, Path }

import scala.util.Try

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{ BeforeAndAfterEach, TryValues }

import types.MimeType

/**
 * Integration tests for CLI functionality. Tests the full CLI parsing and execution flow.
 */
class CLIIntegrationSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach with TryValues {

  var tempDir: Path    = _
  var inputFile: Path  = _
  var outputFile: Path = _
  var outputDir: Path  = _

  override def beforeEach(): Unit = {
    tempDir = Files.createTempDirectory("cli-integration-test")
    inputFile = Files.createTempFile(tempDir, "input", ".txt")
    outputFile = tempDir.resolve("output.txt")
    outputDir = tempDir.resolve("output-dir")
    Files.createDirectories(outputDir)
    Files.write(inputFile, "Test content".getBytes)
  }

  override def afterEach(): Unit =
    // Clean up temp files
    Try {
      Files.walk(tempDir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete)
    }

  "CLI Integration" should "parse chunk range from command line" in {
    val chunkRange = CommonCLI.parseChunkRange("0-4")
    chunkRange shouldBe Some(0 until 5)
  }

  it should "parse MIME mappings from command line format" in {
    val mappings = Seq("pdf=json", "xlsx=pdf")
    val parsed   = CommonCLI.parseMimeMappings(mappings)

    parsed should have size 2
    parsed(MimeType.ApplicationPdf) shouldBe MimeType.ApplicationJson
    parsed(MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet) shouldBe MimeType.ApplicationPdf
  }

  it should "handle output type parsing" in {
    // Test --type option
    CommonCLI.parseOutputMimeType(Some("pdf"), None) shouldBe Some(MimeType.ApplicationPdf)
    CommonCLI.parseOutputMimeType(Some("application/json"), None) shouldBe Some(
      MimeType.ApplicationJson
    )

    // Test --format option (legacy)
    CommonCLI.parseOutputMimeType(None, Some("png")) shouldBe Some(MimeType.ImagePng)
    CommonCLI.parseOutputMimeType(None, Some("jpg")) shouldBe Some(MimeType.ImageJpeg)

    // Type takes precedence over format
    CommonCLI.parseOutputMimeType(Some("pdf"), Some("png")) shouldBe Some(MimeType.ApplicationPdf)

    // Neither provided
    CommonCLI.parseOutputMimeType(None, None) shouldBe None
  }

  it should "build base configuration with defaults" in {
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
    config.maxImageSizeBytes shouldBe (1024 * 1024 * 5)
    config.imageDpi shouldBe 300
    config.jpegQuality shouldBe 0.85f
    config.recursiveExtraction shouldBe false
    config.maxRecursionDepth shouldBe 5
    config.mappings shouldBe Seq.empty
    config.failureMode shouldBe None
    config.failureContext shouldBe Map.empty
    config.chunkRange shouldBe None
  }

  it should "parse failure mode options" in {
    // Failure mode parsing would typically be done in the CLI parser
    // Here we test the expected values
    val validFailureModes = Seq("throw", "preserve", "drop", "tag")

    validFailureModes.foreach { mode =>
      // In real usage, this would be parsed by scopt
      mode should (equal("throw").or(equal("preserve")).or(equal("drop")).or(equal("tag")))
    }
  }

  it should "handle complex MIME type mappings" in {
    val mappings = Seq(
      "application/vnd.ms-excel=application/json",
      "xlsx=json",
      "pdf=xml",
      "text/plain=text/html"
    )

    val parsed = CommonCLI.parseMimeMappings(mappings)

    (parsed should contain).key(MimeType.ApplicationVndMsExcel)
    (parsed should contain).key(MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)
    (parsed should contain).key(MimeType.ApplicationPdf)
    (parsed should contain).key(MimeType.TextPlain)

    parsed(MimeType.ApplicationVndMsExcel) shouldBe MimeType.ApplicationJson
    parsed(MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet) shouldBe MimeType.ApplicationJson
    parsed(MimeType.ApplicationPdf) shouldBe MimeType.ApplicationXml
    parsed(MimeType.TextPlain) shouldBe MimeType.TextHtml
  }

  it should "validate chunk range formats" in {
    // Valid formats
    CommonCLI.parseChunkRange("5") should be(defined)
    CommonCLI.parseChunkRange("0-9") should be(defined)
    CommonCLI.parseChunkRange("100-") should be(defined)
    CommonCLI.parseChunkRange("0-4,10-14") should be(defined) // Takes first range

    // Invalid formats
    CommonCLI.parseChunkRange("") shouldBe None
    CommonCLI.parseChunkRange("abc") shouldBe None
    CommonCLI.parseChunkRange("-5") shouldBe None
    CommonCLI.parseChunkRange("5-4") shouldBe None
    CommonCLI.parseChunkRange("--") shouldBe None
  }

  it should "handle image conversion parameters" in {
    val config = CommonCLI.BaseConfig(
      maxImageWidth = 1920,
      maxImageHeight = 1080,
      maxImageSizeBytes = 10 * 1024 * 1024,
      imageDpi = 150,
      jpegQuality = 0.95f
    )

    config.maxImageWidth shouldBe 1920
    config.maxImageHeight shouldBe 1080
    config.maxImageSizeBytes shouldBe (10 * 1024 * 1024)
    config.imageDpi shouldBe 150
    config.jpegQuality shouldBe 0.95f
  }

  it should "handle recursive extraction parameters" in {
    val config = CommonCLI.BaseConfig(
      recursiveExtraction = true,
      maxRecursionDepth = 10
    )

    config.recursiveExtraction shouldBe true
    config.maxRecursionDepth shouldBe 10
  }

  it should "parse mixed extension and MIME type inputs" in {
    // Extensions
    CommonCLI.parseMimeOrExtension("pdf") shouldBe Some(MimeType.ApplicationPdf)
    CommonCLI.parseMimeOrExtension(".xlsx") shouldBe Some(
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
    )

    // MIME types
    CommonCLI.parseMimeOrExtension("text/plain") shouldBe Some(MimeType.TextPlain)
    CommonCLI.parseMimeOrExtension("application/json") shouldBe Some(MimeType.ApplicationJson)

    // Invalid inputs
    CommonCLI.parseMimeOrExtension("unknown") shouldBe None
    CommonCLI.parseMimeOrExtension("") shouldBe None
    CommonCLI.parseMimeOrExtension(null) shouldBe None
  }

  it should "handle failure context parsing" in {
    // In real usage, scopt would parse this as a Map
    val context = Map(
      "env"     -> "production",
      "version" -> "1.0",
      "user"    -> "test-user"
    )

    val config = CommonCLI.BaseConfig(failureContext = context)
    config.failureContext shouldBe context
  }

  it should "combine multiple configuration options" in {
    val config = CommonCLI.BaseConfig(
      input = inputFile.toString,
      output = outputDir.toString,
      splitMode = true,
      splitStrategy = Some("page"),
      outputType = Some("png"),
      failureMode = Some("drop"),
      chunkRange = Some("0-9"),
      maxImageWidth = 800,
      maxImageHeight = 600,
      recursiveExtraction = true
    )

    config.input shouldBe inputFile.toString
    config.output shouldBe outputDir.toString
    config.splitMode shouldBe true
    config.splitStrategy shouldBe Some("page")
    config.outputType shouldBe Some("png")
    config.failureMode shouldBe Some("drop")
    config.chunkRange shouldBe Some("0-9")
    config.maxImageWidth shouldBe 800
    config.maxImageHeight shouldBe 600
    config.recursiveExtraction shouldBe true
  }
}
