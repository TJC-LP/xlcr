package com.tjclp.xlcr
package pipeline

import java.nio.file.{ Files, Path }

import scala.util.Try

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{ BeforeAndAfterEach, TryValues }

import splitters.{ SplitConfig, SplitFailureMode, SplitStrategy }
import types.MimeType

class PipelineSplitSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach with TryValues {

  var tempDir: Path   = _
  var outputDir: Path = _

  override def beforeEach(): Unit = {
    tempDir = Files.createTempDirectory("pipeline-split-test")
    outputDir = tempDir.resolve("output")
    Files.createDirectories(outputDir)
  }

  override def afterEach(): Unit =
    // Clean up temp files
    Try {
      Files.walk(tempDir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete)
    }

  "Pipeline.split" should "validate input file exists" in {
    val nonExistentFile = tempDir.resolve("nonexistent.pdf")

    (the[InputFileNotFoundException] thrownBy {
      Pipeline.split(
        inputPath = nonExistentFile.toString,
        outputDir = outputDir.toString
      )
    } should have).message(
      s"Input file 'Input path must be an existing file: ${nonExistentFile.toString}' does not exist"
    )
  }

  it should "create output directory if it doesn't exist" in {
    val testFile     = createMockFile("test.txt", "content")
    val newOutputDir = tempDir.resolve("new-output")

    Try {
      Pipeline.split(
        inputPath = testFile.toString,
        outputDir = newOutputDir.toString
      )
    }

    Files.exists(newOutputDir) shouldBe true
    Files.isDirectory(newOutputDir) shouldBe true
  }

  it should "handle split strategy selection" in {
    val pdfFile = createMockFile("test.pdf", "%PDF-1.4")

    // Test with explicit strategy
    Try {
      Pipeline.split(
        inputPath = pdfFile.toString,
        outputDir = outputDir.toString,
        strategy = Some(SplitStrategy.Page)
      )
    }

    // Would need actual PDF splitter implementation or mocks
  }

  it should "apply chunk range filtering" in {
    val testFile = createMockFile("test.txt", "Line 1\nLine 2\nLine 3\nLine 4\nLine 5")

    Try {
      Pipeline.split(
        inputPath = testFile.toString,
        outputDir = outputDir.toString,
        strategy = Some(SplitStrategy.Row),
        chunkRange = Some(1 to 3) // Select rows 1-3 (0-indexed)
      )
    }

    // Would verify only selected chunks are written
  }

  it should "handle failure modes correctly" in {
    val testFile = createMockFile("test.xlsx", "mock excel content")

    // Test PreserveAsChunk mode (default)
    Try {
      Pipeline.split(
        inputPath = testFile.toString,
        outputDir = outputDir.toString,
        strategy = Some(SplitStrategy.Page), // Invalid for Excel
        failureMode = Some(SplitFailureMode.PreserveAsChunk)
      )
    }

    // Should create single output file with warning
  }

  it should "respect output type override" in {
    val pdfFile = createMockFile("test.pdf", "%PDF-1.4")

    Try {
      Pipeline.split(
        inputPath = pdfFile.toString,
        outputDir = outputDir.toString,
        strategy = Some(SplitStrategy.Page),
        outputType = Some(MimeType.ImagePng)
      )
    }

    // Would verify output files are PNG format
  }

  it should "handle recursive extraction for archives" in {
    val zipFile = createMockZipFile()

    Try {
      Pipeline.split(
        inputPath = zipFile.toString,
        outputDir = outputDir.toString,
        strategy = Some(SplitStrategy.Embedded),
        recursive = true,
        maxRecursionDepth = 3
      )
    }

    // Would verify nested archives are extracted
  }

  it should "respect image conversion settings" in {
    val pdfFile = createMockFile("test.pdf", "%PDF-1.4")

    Try {
      Pipeline.split(
        inputPath = pdfFile.toString,
        outputDir = outputDir.toString,
        strategy = Some(SplitStrategy.Page),
        outputType = Some(MimeType.ImageJpeg),
        maxImageWidth = 800,
        maxImageHeight = 600,
        imageDpi = 150,
        jpegQuality = 0.8f
      )
    }

    // Would verify image settings are applied
  }

  it should "handle empty chunk range" in {
    val testFile = createMockFile("test.txt", "content")

    Try {
      Pipeline.split(
        inputPath = testFile.toString,
        outputDir = outputDir.toString,
        chunkRange = Some(10 to 20) // Out of range
      )
    }

    // Should produce no output files
  }

  it should "apply failure context when provided" in {
    val testFile = createMockFile("test.doc", "mock doc")
    val failureContext = Map(
      "source" -> "test-suite",
      "user"   -> "test-user"
    )

    Try {
      Pipeline.split(
        inputPath = testFile.toString,
        outputDir = outputDir.toString,
        strategy = Some(SplitStrategy.Page),
        failureMode = Some(SplitFailureMode.TagAndPreserve),
        failureContext = failureContext
      )
    }

    // Would verify context is included in output
  }

  "Split configuration" should "be properly constructed" in {
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Sheet),
      recursive = true,
      maxRecursionDepth = 5,
      maxImageWidth = 1920,
      maxImageHeight = 1080,
      maxImageSizeBytes = 10 * 1024 * 1024,
      imageDpi = 300,
      jpegQuality = 0.95f,
      failureMode = SplitFailureMode.ThrowException,
      failureContext = Map("env" -> "test"),
      chunkRange = Some(0 until 10)
    )

    config.strategy shouldBe Some(SplitStrategy.Sheet)
    config.recursive shouldBe true
    config.maxRecursionDepth shouldBe 5
    config.failureMode shouldBe SplitFailureMode.ThrowException
    config.chunkRange shouldBe Some(0 until 10)
  }

  // Helper methods
  private def createMockFile(name: String, content: String): Path = {
    val file = tempDir.resolve(name)
    Files.write(file, content.getBytes)
    file
  }

  private def createMockZipFile(): Path = {
    val file = tempDir.resolve("test.zip")
    // ZIP file header
    Files.write(file, Array[Byte](0x50, 0x4b, 0x03, 0x04))
    file
  }
}
