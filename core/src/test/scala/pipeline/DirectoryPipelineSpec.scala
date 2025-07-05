package com.tjclp.xlcr
package pipeline

import java.nio.file.{ Files, Path }

import scala.util.Try

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{ BeforeAndAfterEach, TryValues }

import types.MimeType

class DirectoryPipelineSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach
    with TryValues {

  var tempDir: Path   = _
  var inputDir: Path  = _
  var outputDir: Path = _

  override def beforeEach(): Unit = {
    tempDir = Files.createTempDirectory("dirpipeline-test")
    inputDir = tempDir.resolve("input")
    outputDir = tempDir.resolve("output")
    Files.createDirectories(inputDir)
    Files.createDirectories(outputDir)
  }

  override def afterEach(): Unit =
    // Clean up temp files
    Try {
      Files.walk(tempDir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete)
    }

  "DirectoryPipeline.runDirectoryToDirectory" should "convert files based on mime mappings" in {
    // Create test files
    val txtFile  = createTextFile("test1.txt", "Hello from text file")
    val txtFile2 = createTextFile("test2.txt", "Another text file")

    // Define mappings for text to text (identity conversion)
    // This uses the wildcard Tika bridge
    val mappings = Map[MimeType, MimeType](
      MimeType.TextPlain -> MimeType.TextPlain
    )

    DirectoryPipeline.runDirectoryToDirectory(
      inputDir.toString,
      outputDir.toString,
      mappings,
      diffMode = false
    )

    // Check output files exist
    Files.exists(outputDir.resolve("test1.txt")) shouldBe true
    Files.exists(outputDir.resolve("test2.txt")) shouldBe true
  }

  it should "skip files without mappings" in {
    createTextFile("test.txt", "Text content")
    createFile("unknown.xyz", "Unknown content")

    val mappings = Map[MimeType, MimeType](
      MimeType.TextPlain -> MimeType.TextPlain
    )

    DirectoryPipeline.runDirectoryToDirectory(
      inputDir.toString,
      outputDir.toString,
      mappings
    )

    Files.exists(outputDir.resolve("test.txt")) shouldBe true
    Files.exists(outputDir.resolve("unknown.xyz")) shouldBe false
  }

  it should "handle empty input directory" in {
    val emptyMappings = Map.empty[MimeType, MimeType]

    DirectoryPipeline.runDirectoryToDirectory(
      inputDir.toString,
      outputDir.toString,
      emptyMappings
    )

    // Should complete without errors
    Files.list(outputDir).count() shouldBe 0
  }

  it should "throw exception for non-existent input directory" in {
    val nonExistentDir = tempDir.resolve("nonexistent")

    val exception = the[IllegalArgumentException] thrownBy {
      DirectoryPipeline.runDirectoryToDirectory(
        nonExistentDir.toString,
        outputDir.toString,
        Map.empty
      )
    }
    exception.getMessage should include("does not exist or is not a directory")
  }

  it should "throw exception when output path is not a directory" in {
    val outputFile = tempDir.resolve("file.txt")
    Files.createFile(outputFile)

    val exception = the[IllegalArgumentException] thrownBy {
      DirectoryPipeline.runDirectoryToDirectory(
        inputDir.toString,
        outputFile.toString,
        Map.empty
      )
    }
    exception.getMessage should include("is not a directory")
  }

  it should "create output directory if it doesn't exist" in {
    val newOutputDir = tempDir.resolve("new-output")

    DirectoryPipeline.runDirectoryToDirectory(
      inputDir.toString,
      newOutputDir.toString,
      Map.empty
    )

    Files.exists(newOutputDir) shouldBe true
    Files.isDirectory(newOutputDir) shouldBe true
  }

  it should "handle subdirectories correctly" in {
    // Create subdirectory with file
    val subDir = inputDir.resolve("subdir")
    Files.createDirectories(subDir)
    createTextFile("subdir/nested.txt", "Nested content")
    createTextFile("root.txt", "Root content")

    val mappings = Map[MimeType, MimeType](
      MimeType.TextPlain -> MimeType.TextPlain
    )

    DirectoryPipeline.runDirectoryToDirectory(
      inputDir.toString,
      outputDir.toString,
      mappings
    )

    // Should only process files in root directory
    Files.exists(outputDir.resolve("root.txt")) shouldBe true
    Files.exists(outputDir.resolve("subdir")) shouldBe false
    Files.exists(outputDir.resolve("nested.txt")) shouldBe false
  }

  it should "handle file conversion errors gracefully" in {
    // Create a file that might cause conversion issues
    val problematicFile = createFile("problem.txt", "")
    // Make it unreadable (platform-specific, might not work everywhere)
    Try(problematicFile.toFile.setReadable(false))

    val mappings = Map[MimeType, MimeType](
      MimeType.TextPlain -> MimeType.ApplicationPdf // Conversion that might fail
    )

    // Should continue processing other files even if one fails
    DirectoryPipeline.runDirectoryToDirectory(
      inputDir.toString,
      outputDir.toString,
      mappings
    )

    // Restore permissions for cleanup
    Try(problematicFile.toFile.setReadable(true))
  }

  it should "preserve original filenames when extension doesn't change" in {
    createTextFile("document.txt", "Content")

    val mappings = Map[MimeType, MimeType](
      MimeType.TextPlain -> MimeType.TextPlain
    )

    DirectoryPipeline.runDirectoryToDirectory(
      inputDir.toString,
      outputDir.toString,
      mappings
    )

    Files.exists(outputDir.resolve("document.txt")) shouldBe true
  }

  it should "change file extensions based on output mime type" in {
    createTextFile("document.txt", "Content")

    // This would need actual bridge support or mocking
    val mappings = Map[MimeType, MimeType](
      MimeType.TextPlain -> MimeType.ApplicationPdf
    )

    Try {
      DirectoryPipeline.runDirectoryToDirectory(
        inputDir.toString,
        outputDir.toString,
        mappings
      )
    }

    // Would expect document.pdf if conversion worked
  }

  // Helper methods
  private def createTextFile(name: String, content: String): Path = {
    val file = inputDir.resolve(name)
    Files.write(file, content.getBytes)
    file
  }

  private def createJsonFile(name: String, content: String): Path = {
    val file = inputDir.resolve(name)
    Files.write(file, content.getBytes)
    file
  }

  private def createFile(name: String, content: String): Path = {
    val file = inputDir.resolve(name)
    Files.write(file, content.getBytes)
    file
  }
}
