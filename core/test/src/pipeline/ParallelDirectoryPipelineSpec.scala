package com.tjclp.xlcr
package pipeline

import java.nio.file.{ Files, Path }

import scala.util.Try

import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import processing.{ ErrorMode, ProgressReporter }
import types.MimeType

class ParallelDirectoryPipelineSpec
    extends AnyFlatSpec
    with Matchers
    with BeforeAndAfterEach {

  private var tempDir: Path   = _
  private var inputDir: Path  = _
  private var outputDir: Path = _

  override def beforeEach(): Unit = {
    tempDir = Files.createTempDirectory("parallel-pipeline-spec")
    inputDir = tempDir.resolve("input")
    outputDir = tempDir.resolve("output")
    Files.createDirectories(inputDir)
    Files.createDirectories(outputDir)
  }

  override def afterEach(): Unit =
    Try {
      Files.walk(tempDir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete)
    }

  private def createTextFile(name: String, content: String): Path = {
    val path = inputDir.resolve(name)
    Files.write(path, content.getBytes("UTF-8"))
    path
  }

  "ParallelDirectoryPipeline.runParallel" should "treat errors as skipped when using skip-on-error mode" in {
    createTextFile("broken.txt", "content that will fail conversion")

    val mappings = Map[MimeType, MimeType](
      MimeType.TextPlain -> MimeType.ApplicationPdf // forces UnsupportedConversionException
    )

    val summary = ParallelDirectoryPipeline.runParallel(
      inputDir = inputDir.toString,
      outputDir = outputDir.toString,
      mimeMappings = mappings,
      config = ParallelDirectoryPipeline.Config(
        threads = 2,
        errorMode = ErrorMode.SkipOnError,
        progressReporter = ProgressReporter.NoOp
      )
    )

    summary.totalFiles shouldBe 1
    summary.successfulFiles shouldBe 0
    summary.failedFiles shouldBe 0
    summary.skippedFiles shouldBe 1
  }
}
