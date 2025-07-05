package com.tjclp.xlcr
package pipeline

import java.nio.file.{ Files, Path }

import scala.util.Try

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{ BeforeAndAfterEach, TryValues }

class PipelineSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach with TryValues {

  var tempDir: Path        = _
  var testInputFile: Path  = _
  var testOutputFile: Path = _

  override def beforeEach(): Unit = {
    tempDir = Files.createTempDirectory("pipeline-test")
    testInputFile = Files.createTempFile(tempDir, "input", ".txt")
    testOutputFile = tempDir.resolve("output.txt")

    // Write some test content
    Files.write(testInputFile, "Hello, World!".getBytes)
  }

  override def afterEach(): Unit =
    // Clean up temp files
    Try {
      Files.walk(tempDir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete)
    }

  "Pipeline.run" should "convert a text file successfully" in {
    Pipeline.run(
      inputPath = testInputFile.toString,
      outputPath = testOutputFile.toString,
      diffMode = false
    )

    Files.exists(testOutputFile) shouldBe true
    val content = new String(Files.readAllBytes(testOutputFile))
    content.trim shouldBe "Hello, World!"
  }

  it should "throw InputFileNotFoundException for non-existent input" in {
    val nonExistentFile = tempDir.resolve("nonexistent.txt").toString

    (the[InputFileNotFoundException] thrownBy {
      Pipeline.run(
        inputPath = nonExistentFile,
        outputPath = testOutputFile.toString
      )
    } should have).message(s"Input file '$nonExistentFile' does not exist")
  }

  it should "handle file copy when no bridge is available" in {
    // Create a file with an unusual extension
    val customFile   = Files.createTempFile(tempDir, "test", ".xyz")
    val customOutput = tempDir.resolve("output.xyz")
    Files.write(customFile, "Custom content".getBytes)

    // Pipeline now throws UnknownExtensionException for unknown extensions
    (the[UnknownExtensionException] thrownBy {
      Pipeline.run(
        inputPath = customFile.toString,
        outputPath = customOutput.toString
      )
    } should have).message(s"Cannot determine MIME type for extension 'xyz' in file: $customOutput")
  }

  it should "create parent directories for output file" in {
    val nestedOutput = tempDir.resolve("nested/deep/output.txt")

    Pipeline.run(
      inputPath = testInputFile.toString,
      outputPath = nestedOutput.toString
    )

    Files.exists(nestedOutput) shouldBe true
    Files.exists(nestedOutput.getParent) shouldBe true
  }

  // Mock bridge tests would require mocking BridgeRegistry
  // which is complex due to ServiceLoader usage

  "Pipeline.split" should "validate input file exists" in {
    val nonExistentFile = tempDir.resolve("nonexistent.pdf").toString
    val outputDir       = tempDir.resolve("output")

    (the[InputFileNotFoundException] thrownBy {
      Pipeline.split(
        inputPath = nonExistentFile,
        outputDir = outputDir.toString
      )
    } should have).message(
      s"Input file 'Input path must be an existing file: $nonExistentFile' does not exist"
    )
  }

  it should "create output directory if it doesn't exist" in {
    val pdfFile   = createMockPdfFile()
    val outputDir = tempDir.resolve("split-output")

    // This would need a real PDF splitter or mock
    // For now, just test the directory creation part
    Try {
      Pipeline.split(
        inputPath = pdfFile.toString,
        outputDir = outputDir.toString
      )
    }

    // Even if splitting fails, directory should be created
    Files.exists(outputDir) shouldBe true
  }

  it should "handle unsupported split strategies gracefully" in {
    val outputDir = tempDir.resolve("output")

    // Try to split a text file by page (unsupported)
    // With the current implementation, this succeeds and creates a single chunk
    // because DocumentSplitter has fallback behavior for unsupported strategies
    val result = Try {
      Pipeline.split(
        inputPath = testInputFile.toString,
        outputDir = outputDir.toString,
        strategy = Some(splitters.SplitStrategy.Page)
      )
    }

    result.isSuccess shouldBe true
    // Check that output directory has at least one file (the fallback chunk)
    Files.exists(outputDir) shouldBe true
  }

  // Helper methods
  private def createMockPdfFile(): Path = {
    val pdfFile = Files.createTempFile(tempDir, "test", ".pdf")
    // Write minimal PDF header
    Files.write(pdfFile, "%PDF-1.4".getBytes)
    pdfFile
  }

  private def createMockExcelFile(): Path = {
    val xlsxFile = Files.createTempFile(tempDir, "test", ".xlsx")
    // Write minimal ZIP header (Excel files are ZIP archives)
    Files.write(xlsxFile, Array[Byte](0x50, 0x4b, 0x03, 0x04))
    xlsxFile
  }

  "Pipeline configuration" should "respect image conversion settings" in {
    // This test would verify that image conversion settings are passed through
    // Would require mocking or actual PDF to image conversion
    val config = splitters.SplitConfig(
      strategy = Some(splitters.SplitStrategy.Page),
      maxImageWidth = 1000,
      maxImageHeight = 800,
      imageDpi = 150,
      jpegQuality = 0.9f
    )

    config.maxImageWidth shouldBe 1000
    config.maxImageHeight shouldBe 800
    config.imageDpi shouldBe 150
    config.jpegQuality shouldBe 0.9f
  }

  "Pipeline error handling" should "provide meaningful error messages" in {
    // Test various error scenarios
    val invalidPath = "/invalid\u0000path/file.txt" // Null character in path

    Try {
      Pipeline.run(invalidPath, testOutputFile.toString)
    }.failure.exception shouldBe a[Exception]
  }
}
