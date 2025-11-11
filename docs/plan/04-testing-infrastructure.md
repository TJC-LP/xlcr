# Testing Infrastructure

**Priority**: Highest (Top User Priority)
**Effort**: 5-7 days
**Phase**: Testing Infrastructure

## Current State

### What Exists Today

**Good Foundation**:
- Core module: **59 test files** with solid coverage
- Aspose module: **14 test files** with adequate coverage
- One E2E test: `SimpleSplitE2ESpec.scala`
- Failure mode testing: Multiple specs for error scenarios

### What's Missing

**Critical Gaps**:
1. **No Integration Tests**: No tests covering multiple modules together
2. **No Backend Selection Tests**: Priority system and backend switching untested
3. **Limited E2E Scenarios**: Only one E2E test exists
4. **No Full Pipeline Tests**: Individual components tested, not end-to-end workflows
5. **Test Complexity**: Hard to write tests, no test utilities for common scenarios
6. **No Performance Tests**: Benchmarks exist but not in CI
7. **Mock/Test Doubles**: No easy way to test with controlled backends

### Impact

For production service deployment:
- ❌ Can't verify multi-module interactions work correctly
- ❌ No confidence that backend selection logic works as expected
- ❌ Difficult to reproduce and test bug reports
- ❌ Hard to add new tests (high friction)
- ❌ No regression detection for performance
- ❌ Can't test service scenarios (concurrent conversions, etc.)

## Proposed Solution

**Build comprehensive testing infrastructure**:
1. Integration test module with multi-module testing
2. Test utilities and fixtures for common scenarios
3. E2E test suite covering major workflows
4. Backend mocking/selection utilities
5. Performance test framework integrated in CI
6. Test documentation and examples

### Test Categories

| Category | Purpose | Speed | CI |
|----------|---------|-------|-----|
| **Unit** | Test individual classes/methods | Fast (<1s) | ✅ Always |
| **Integration** | Test module interactions | Medium (1-10s) | ✅ Always |
| **E2E** | Test complete workflows | Slow (10-60s) | ✅ Always |
| **Performance** | Detect regressions | Slow (1-5min) | ⚠️ Nightly |

## Implementation Details

### Step 1: Integration Test Module

**Directory Structure**:
```
core/
  src/
    it/                               # New: Integration Tests
      scala/
        com/tjclp/xlcr/
          integration/
            MultiModuleIntegrationSpec.scala
            BackendSelectionSpec.scala
            PipelineIntegrationSpec.scala
            ParallelProcessingSpec.scala
```

**File**: `build.sbt` configuration

Add IntegrationTest configuration:
```scala
lazy val core = (project in file("core"))
  .configs(IntegrationTest)
  .settings(
    commonSettings,
    Defaults.itSettings,

    // Integration test settings
    IntegrationTest / fork := true,
    IntegrationTest / parallelExecution := false,
    IntegrationTest / testOptions += Tests.Argument("-oD"), // Show durations

    // ... existing settings
  )

// Add integration test task
lazy val integrationTests = taskKey[Unit]("Run integration tests")
integrationTests := (core / IntegrationTest / test).value
```

**SBT Commands**:
```bash
sbt it:test              # Run integration tests
sbt "it:testOnly *BackendSelectionSpec"  # Run specific integration test
sbt test it:test         # Run both unit and integration tests
```

### Step 2: Test Utilities and Fixtures

**New File**: `core/src/test/scala/test/TestFixtures.scala`

```scala
package com.tjclp.xlcr.test

import com.tjclp.xlcr.mime._
import com.tjclp.xlcr.models.FileContent
import java.nio.file.{Files, Path, Paths}
import scala.util.Using

/**
 * Test fixtures for common document types.
 * Provides sample files and utilities for testing.
 */
object TestFixtures {

  private val testDataDir: Path = Paths.get("data")

  /**
   * Get path to test data file.
   */
  def testFile(name: String): Path = testDataDir.resolve(name)

  /**
   * Common test files available in data/ directory.
   */
  object TestFiles {
    val simplePdf: Path = testFile("simple.pdf")
    val simpleXlsx: Path = testFile("simple.xlsx")
    val simpleDocx: Path = testFile("simple.docx")
    val simplePptx: Path = testFile("simple.pptx")
    val simpleHtml: Path = testFile("simple.html")

    // Multi-page documents
    val multiPagePdf: Path = testFile("multi-page.pdf")
    val largeXlsx: Path = testFile("large.xlsx")

    // Edge cases
    val corruptedPdf: Path = testFile("corrupted.pdf")
    val emptyXlsx: Path = testFile("empty.xlsx")
    val largeFile: Path = testFile("large-10mb.pdf")
  }

  /**
   * Create a temporary file with specific content.
   */
  def createTempFile(prefix: String, suffix: String, content: Array[Byte]): Path = {
    val tempFile = Files.createTempFile(prefix, suffix)
    Files.write(tempFile, content)
    tempFile
  }

  /**
   * Create a FileContent for testing.
   */
  def createFileContent[T <: MimeType](
    mimeType: T,
    data: Array[Byte],
    metadata: Map[String, Any] = Map.empty
  ): FileContent[T] = {
    FileContent(mimeType, data, metadata)
  }

  /**
   * Read test file into byte array.
   */
  def readTestFile(path: Path): Array[Byte] = {
    Using.resource(Files.newInputStream(path)) { is =>
      is.readAllBytes()
    }
  }

  /**
   * Assert that file exists and has content.
   */
  def assertValidFile(path: Path): Unit = {
    assert(Files.exists(path), s"File does not exist: $path")
    assert(Files.size(path) > 0, s"File is empty: $path")
  }

  /**
   * Clean up temporary test files.
   */
  def cleanupTempFiles(paths: Path*): Unit = {
    paths.foreach { path =>
      if (Files.exists(path)) {
        Files.delete(path)
      }
    }
  }
}
```

**New File**: `core/src/test/scala/test/TestBackends.scala`

```scala
package com.tjclp.xlcr.test

import com.tjclp.xlcr.bridges.{Bridge, BridgeInfo, BridgeRegistry}
import com.tjclp.xlcr.mime.MimeType
import com.tjclp.xlcr.models.FileContent
import com.tjclp.xlcr.config.Priority
import scala.util.{Success, Try}

/**
 * Utilities for testing with specific backends.
 */
object TestBackends {

  /**
   * Find bridge with specific backend.
   */
  def findBridgeByBackend[I <: MimeType, O <: MimeType](
    input: I,
    output: O,
    backend: String
  ): Option[Bridge[I, O]] = {
    BridgeRegistry.findBridgeWithBackend(input, output, Some(backend))
  }

  /**
   * Check if specific backend is available.
   */
  def isBackendAvailable(backend: String): Boolean = {
    backend.toLowerCase match {
      case "aspose" => hasAsposeLicense()
      case "libreoffice" => hasLibreOffice()
      case "core" => true
      case _ => false
    }
  }

  /**
   * Skip test if backend not available.
   */
  def requireBackend(backend: String): Unit = {
    org.scalatest.Assumptions.assume(
      isBackendAvailable(backend),
      s"Backend '$backend' not available"
    )
  }

  private def hasAsposeLicense(): Boolean = {
    // Check if Aspose classes are available
    Try(Class.forName("com.aspose.slides.Presentation")).isSuccess
  }

  private def hasLibreOffice(): Boolean = {
    // Check if LibreOffice is installed
    Try {
      val libreOfficeHome = sys.env.get("LIBREOFFICE_HOME")
        .orElse(Some("/Applications/LibreOffice.app/Contents"))

      libreOfficeHome.exists { home =>
        new java.io.File(home).exists()
      }
    }.getOrElse(false)
  }

  /**
   * Create a mock bridge for testing.
   */
  def createMockBridge[I <: MimeType, O <: MimeType](
    input: I,
    output: O,
    converter: FileContent[I] => FileContent[O]
  ): Bridge[I, O] = new Bridge[I, O] {
    override def inputMimeType: I = input
    override def outputMimeType: O = output
    override def priority: Priority = Priority.DEFAULT

    override def convert(
      input: FileContent[I],
      config: Option[BridgeConfig] = None
    ): Try[FileContent[O]] = {
      Success(converter(input))
    }
  }
}
```

**New File**: `core/src/test/scala/test/TestConfig.scala`

```scala
package com.tjclp.xlcr.test

import com.tjclp.xlcr.config._
import scala.concurrent.duration._

/**
 * Test configuration with fast timeouts and small limits.
 */
object TestConfig {

  def testConfig: ApplicationConfig = ApplicationConfig(
    processing = ProcessingConfig(
      defaultTimeout = 30.seconds,
      maxFileSize = 10L * 1024 * 1024, // 10MB for tests
      enableParallel = false // Deterministic for tests
    ),

    libreOffice = LibreOfficeConfig(
      enabled = true,
      home = sys.env.get("LIBREOFFICE_HOME"),
      maxTasksPerProcess = 10,
      taskExecutionTimeout = 30.seconds,
      taskQueueTimeout = 5.seconds
    ),

    aspose = AsposeConfig(
      enabled = true,
      licensePath = sys.env.get("ASPOSE_LICENSE_PATH"),
      strictLicenseCheck = false // Relaxed for tests
    ),

    performance = PerformanceConfig(
      threadPoolSize = 2,
      bufferSize = 4096,
      maxConcurrentConversions = 4
    ),

    paths = PathsConfig(
      tempDir = System.getProperty("java.io.tmpdir"),
      cacheDir = None
    )
  )
}
```

### Step 3: Integration Tests

**File**: `core/src/it/scala/integration/MultiModuleIntegrationSpec.scala`

```scala
package com.tjclp.xlcr.integration

import com.tjclp.xlcr.test.{TestFixtures, TestBackends, TestConfig}
import com.tjclp.xlcr.Pipeline
import com.tjclp.xlcr.config.ApplicationContext
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import java.nio.file.Files

/**
 * Integration tests spanning multiple modules (core + aspose).
 */
class MultiModuleIntegrationSpec
  extends AnyFlatSpec
  with Matchers
  with BeforeAndAfterAll
  with BeforeAndAfterEach {

  override def beforeAll(): Unit = {
    ApplicationContext.setConfig(TestConfig.testConfig)
  }

  "PDF to PPTX conversion" should "work with Aspose backend" in {
    TestBackends.requireBackend("aspose")

    val inputPath = TestFixtures.TestFiles.simplePdf
    val outputPath = Files.createTempFile("test-output", ".pptx")

    try {
      val pipeline = new Pipeline()
      val result = pipeline.process(inputPath.toString, outputPath.toString, backend = Some("aspose"))

      result.isSuccess shouldBe true
      TestFixtures.assertValidFile(outputPath)

      // Verify output is valid PPTX
      Files.size(outputPath) should be > 1000L // At least 1KB
    } finally {
      TestFixtures.cleanupTempFiles(outputPath)
    }
  }

  "HTML to PPTX conversion" should "preserve content structure" in {
    TestBackends.requireBackend("aspose")

    val inputPath = TestFixtures.TestFiles.simpleHtml
    val outputPath = Files.createTempFile("test-output", ".pptx")

    try {
      val pipeline = new Pipeline()
      val result = pipeline.process(inputPath.toString, outputPath.toString)

      result.isSuccess shouldBe true
      TestFixtures.assertValidFile(outputPath)

      // TODO: Verify slide count, content preservation
    } finally {
      TestFixtures.cleanupTempFiles(outputPath)
    }
  }

  "Round-trip conversion" should "preserve data (PPTX → HTML → PPTX)" in {
    TestBackends.requireBackend("aspose")

    val inputPptx = TestFixtures.TestFiles.simplePptx
    val intermediateHtml = Files.createTempFile("intermediate", ".html")
    val outputPptx = Files.createTempFile("output", ".pptx")

    try {
      val pipeline = new Pipeline()

      // Stage 1: PPTX → HTML
      val htmlResult = pipeline.process(inputPptx.toString, intermediateHtml.toString)
      htmlResult.isSuccess shouldBe true

      // Stage 2: HTML → PPTX
      val pptxResult = pipeline.process(intermediateHtml.toString, outputPptx.toString)
      pptxResult.isSuccess shouldBe true

      TestFixtures.assertValidFile(outputPptx)

      // Output should be reasonable size (not empty, not huge)
      val outputSize = Files.size(outputPptx)
      outputSize should be > 1000L
      outputSize should be < (10L * 1024 * 1024) // < 10MB

    } finally {
      TestFixtures.cleanupTempFiles(intermediateHtml, outputPptx)
    }
  }

  "Large file handling" should "respect max file size config" in {
    // Configure with small max file size
    val smallConfig = TestConfig.testConfig.copy(
      processing = TestConfig.testConfig.processing.copy(maxFileSize = 1024) // 1KB
    )

    val pipeline = new Pipeline()
    val result = pipeline.process(
      TestFixtures.TestFiles.largeFile.toString,
      "/tmp/output.pptx"
    )

    result.isFailure shouldBe true
    result.failed.get.getMessage should include("file size")
  }
}
```

**File**: `core/src/it/scala/integration/BackendSelectionSpec.scala`

```scala
package com.tjclp.xlcr.integration

import com.tjclp.xlcr.test.{TestBackends, TestConfig}
import com.tjclp.xlcr.bridges.BridgeRegistry
import com.tjclp.xlcr.mime._
import com.tjclp.xlcr.config.{ApplicationContext, Priority}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

/**
 * Integration tests for backend selection logic.
 */
class BackendSelectionSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    ApplicationContext.setConfig(TestConfig.testConfig)
  }

  "BridgeRegistry" should "prioritize Aspose over LibreOffice" in {
    TestBackends.requireBackend("aspose")
    TestBackends.requireBackend("libreoffice")

    val bridge = BridgeRegistry.findBridge(ApplicationPdf, ApplicationVndOpenXmlFormatsPresentationmlPresentation)

    bridge shouldBe defined
    bridge.get.priority shouldBe Priority.HIGH // Aspose bridges are HIGH priority
    bridge.get.getClass.getName should include("Aspose")
  }

  it should "fall back to LibreOffice when Aspose unavailable" in {
    TestBackends.requireBackend("libreoffice")

    // Find bridge for a conversion only LibreOffice supports
    // (or temporarily disable Aspose in config)

    // This test requires ability to disable Aspose dynamically
    // Implementation depends on Step 3 (configuration management)
  }

  it should "honor backend override flag" in {
    TestBackends.requireBackend("libreoffice")

    val bridge = BridgeRegistry.findBridgeWithBackend(
      ApplicationVndOpenXmlFormatsSpreadsheetml,
      ApplicationPdf,
      Some("libreoffice")
    )

    bridge shouldBe defined
    bridge.get.getClass.getName should include("LibreOffice")
  }

  it should "list all available bridges for a conversion" in {
    val bridges = BridgeRegistry.findAllBridges(
      ApplicationPdf,
      ApplicationVndOpenXmlFormatsPresentationmlPresentation
    )

    bridges should not be empty

    // Should be sorted by priority
    val priorities = bridges.map(_.priority.value)
    priorities shouldBe priorities.sorted.reverse
  }

  it should "return None for unsupported conversions" in {
    val bridge = BridgeRegistry.findBridge(
      ImagePng,
      ApplicationVndOpenXmlFormatsSpreadsheetml // PNG → XLSX not supported
    )

    bridge shouldBe None
  }

  "Backend detection" should "correctly identify backend from class name" in {
    val asposeBridge = BridgeRegistry.findBridge(ApplicationPdf, ApplicationVndOpenXmlFormatsPresentationmlPresentation)

    asposeBridge shouldBe defined

    // Test backend detection logic
    val className = asposeBridge.get.getClass.getName.toLowerCase
    className should (include("aspose") or include("libreoffice") or include("core"))
  }
}
```

**File**: `core/src/it/scala/integration/ParallelProcessingSpec.scala`

```scala
package com.tjclp.xlcr.integration

import com.tjclp.xlcr.test.{TestFixtures, TestConfig}
import com.tjclp.xlcr.{DirectoryPipeline, ParallelDirectoryPipeline}
import com.tjclp.xlcr.config.ApplicationContext
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

/**
 * Integration tests for parallel directory processing.
 */
class ParallelProcessingSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    ApplicationContext.setConfig(TestConfig.testConfig)
  }

  "Parallel directory processing" should "process multiple files concurrently" in {
    val inputDir = createTestDirectory(fileCount = 5)
    val outputDir = Files.createTempDirectory("test-output")

    try {
      val pipeline = new ParallelDirectoryPipeline(
        threads = 2,
        errorMode = ParallelDirectoryPipeline.ErrorMode.ContinueOnError
      )

      val result = pipeline.processDirectory(
        inputDir.toString,
        outputDir.toString,
        "pdf",
        "pptx"
      )

      result.total shouldBe 5
      result.successful should be > 0
      result.failed should be >= 0

      // Verify output files created
      val outputFiles = Files.list(outputDir).iterator().asScala.toList
      outputFiles should not be empty

    } finally {
      cleanupDirectory(inputDir)
      cleanupDirectory(outputDir)
    }
  }

  it should "handle errors gracefully in FailFast mode" in {
    val inputDir = createTestDirectory(
      fileCount = 3,
      includeCorrupted = true
    )
    val outputDir = Files.createTempDirectory("test-output")

    try {
      val pipeline = new ParallelDirectoryPipeline(
        threads = 2,
        errorMode = ParallelDirectoryPipeline.ErrorMode.FailFast
      )

      val result = pipeline.processDirectory(
        inputDir.toString,
        outputDir.toString,
        "pdf",
        "pptx"
      )

      // Should stop on first error
      result.failed shouldBe 1

    } finally {
      cleanupDirectory(inputDir)
      cleanupDirectory(outputDir)
    }
  }

  private def createTestDirectory(fileCount: Int, includeCorrupted: Boolean = false): Path = {
    val dir = Files.createTempDirectory("test-input")

    // Copy test files
    (1 to fileCount).foreach { i =>
      val source = TestFixtures.TestFiles.simplePdf
      val dest = dir.resolve(s"test-$i.pdf")
      Files.copy(source, dest)
    }

    if (includeCorrupted) {
      // Create a corrupted file
      val corrupted = dir.resolve("corrupted.pdf")
      Files.write(corrupted, "NOT A PDF".getBytes)
    }

    dir
  }

  private def cleanupDirectory(dir: Path): Unit = {
    if (Files.exists(dir)) {
      Files.walk(dir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete)
    }
  }
}
```

### Step 4: E2E Test Suite

**File**: `core/src/it/scala/e2e/FullPipelineE2ESpec.scala`

```scala
package com.tjclp.xlcr.e2e

import com.tjclp.xlcr.test.{TestFixtures, TestBackends, TestConfig}
import com.tjclp.xlcr.Main
import com.tjclp.xlcr.config.ApplicationContext
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import java.nio.file.Files

/**
 * End-to-end tests simulating actual CLI usage.
 */
class FullPipelineE2ESpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    ApplicationContext.setConfig(TestConfig.testConfig)
  }

  "CLI conversion" should "convert PDF to PPTX via command line" in {
    TestBackends.requireBackend("aspose")

    val inputPath = TestFixtures.TestFiles.simplePdf
    val outputPath = Files.createTempFile("test-output", ".pptx")

    try {
      // Simulate CLI invocation
      val args = Array(
        "-i", inputPath.toString,
        "-o", outputPath.toString,
        "--backend", "aspose"
      )

      // This would require refactoring Main to be testable
      // For now, use Pipeline directly
      val pipeline = new com.tjclp.xlcr.Pipeline()
      val result = pipeline.process(inputPath.toString, outputPath.toString)

      result.isSuccess shouldBe true
      TestFixtures.assertValidFile(outputPath)

    } finally {
      TestFixtures.cleanupTempFiles(outputPath)
    }
  }

  "Two-stage conversion" should "produce smaller output than direct" in {
    TestBackends.requireBackend("aspose")

    val inputPdf = TestFixtures.TestFiles.multiPagePdf
    val directOutput = Files.createTempFile("direct", ".pptx")
    val htmlIntermediate = Files.createTempFile("intermediate", ".html")
    val twoStageOutput = Files.createTempFile("two-stage", ".pptx")

    try {
      val pipeline = new com.tjclp.xlcr.Pipeline()

      // Direct conversion: PDF → PPTX
      pipeline.process(inputPdf.toString, directOutput.toString)

      // Two-stage conversion: PDF → HTML → PPTX
      pipeline.process(inputPdf.toString, htmlIntermediate.toString)
      pipeline.process(htmlIntermediate.toString, twoStageOutput.toString)

      val directSize = Files.size(directOutput)
      val twoStageSize = Files.size(twoStageOutput)

      // Two-stage should be significantly smaller (as documented in CLAUDE.md)
      twoStageSize should be < (directSize / 10) // At least 10x smaller

    } finally {
      TestFixtures.cleanupTempFiles(directOutput, htmlIntermediate, twoStageOutput)
    }
  }

  "Strip masters workflow" should "produce clean HTML output" in {
    TestBackends.requireBackend("aspose")

    val inputPptx = TestFixtures.TestFiles.simplePptx
    val normalHtml = Files.createTempFile("normal", ".html")
    val strippedHtml = Files.createTempFile("stripped", ".html")

    try {
      val pipeline = new com.tjclp.xlcr.Pipeline()

      // Normal conversion
      pipeline.process(inputPptx.toString, normalHtml.toString)

      // With strip-masters flag
      pipeline.process(
        inputPptx.toString,
        strippedHtml.toString,
        config = Map("stripMasters" -> true)
      )

      val normalSize = Files.size(normalHtml)
      val strippedSize = Files.size(strippedHtml)

      // Stripped should be smaller (no template/layout overhead)
      strippedSize should be < normalSize

    } finally {
      TestFixtures.cleanupTempFiles(normalHtml, strippedHtml)
    }
  }
}
```

### Step 5: Performance Test Framework

**File**: `core/src/test/scala/performance/ConversionBenchmark.scala`

```scala
package com.tjclp.xlcr.performance

import com.tjclp.xlcr.test.{TestFixtures, TestBackends, TestConfig}
import com.tjclp.xlcr.Pipeline
import com.tjclp.xlcr.config.ApplicationContext
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import java.nio.file.Files
import scala.concurrent.duration._

/**
 * Performance benchmarks for conversion operations.
 * Run with: sbt "testOnly *ConversionBenchmark"
 */
class ConversionBenchmark extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    ApplicationContext.setConfig(TestConfig.testConfig)
  }

  "PDF to PPTX conversion" should "complete within reasonable time" in {
    TestBackends.requireBackend("aspose")

    val inputPath = TestFixtures.TestFiles.simplePdf
    val outputPath = Files.createTempFile("benchmark", ".pptx")

    try {
      val pipeline = new Pipeline()

      val startTime = System.nanoTime()
      val result = pipeline.process(inputPath.toString, outputPath.toString)
      val duration = (System.nanoTime() - startTime).nanos

      result.isSuccess shouldBe true

      println(s"PDF to PPTX conversion took: ${duration.toMillis}ms")

      // Baseline: should complete in under 5 seconds for simple file
      duration should be < 5.seconds

    } finally {
      TestFixtures.cleanupTempFiles(outputPath)
    }
  }

  "Concurrent conversions" should "scale linearly up to CPU cores" in {
    TestBackends.requireBackend("aspose")

    val threads = Seq(1, 2, 4)
    val results = threads.map { threadCount =>
      val duration = benchmarkParallelConversions(threadCount, fileCount = 10)
      (threadCount, duration)
    }

    results.foreach { case (threads, duration) =>
      println(s"$threads threads: ${duration.toMillis}ms")
    }

    // TODO: Assert scaling properties
  }

  private def benchmarkParallelConversions(threads: Int, fileCount: Int): Duration = {
    import java.util.concurrent.{Executors, TimeUnit}

    val executor = Executors.newFixedThreadPool(threads)
    val startTime = System.nanoTime()

    try {
      val tasks = (1 to fileCount).map { i =>
        executor.submit(new Runnable {
          def run(): Unit = {
            val pipeline = new Pipeline()
            val outputPath = Files.createTempFile(s"benchmark-$i", ".pptx")
            try {
              pipeline.process(TestFixtures.TestFiles.simplePdf.toString, outputPath.toString)
            } finally {
              Files.deleteIfExists(outputPath)
            }
          }
        })
      }

      tasks.foreach(_.get(5, TimeUnit.MINUTES))

      (System.nanoTime() - startTime).nanos

    } finally {
      executor.shutdown()
    }
  }
}
```

### Step 6: CI Integration

**File**: `.github/workflows/ci.yml` (update)

Add integration test step:
```yaml
- name: Run unit tests
  run: sbt test

- name: Run integration tests
  run: sbt it:test

- name: Run E2E tests
  run: sbt "it:testOnly com.tjclp.xlcr.e2e.*"

- name: Run performance benchmarks (nightly only)
  if: github.event_name == 'schedule'
  run: sbt "testOnly *Benchmark"
```

### Step 7: Documentation

**New File**: `docs/TESTING.md`

````markdown
# Testing Guide

## Test Categories

### Unit Tests (`src/test/scala`)
Fast tests for individual classes and methods.

```bash
sbt test
sbt "testOnly com.tjclp.xlcr.ConfigSpec"
```

### Integration Tests (`src/it/scala`)
Tests spanning multiple modules or components.

```bash
sbt it:test
sbt "it:testOnly *BackendSelectionSpec"
```

### E2E Tests (`src/it/scala/e2e`)
Complete workflow tests simulating real usage.

```bash
sbt "it:testOnly com.tjclp.xlcr.e2e.*"
```

### Performance Tests
Benchmarks for conversion operations.

```bash
sbt "testOnly *Benchmark"
```

## Writing Tests

### Using Test Fixtures

```scala
import com.tjclp.xlcr.test.TestFixtures

val inputFile = TestFixtures.TestFiles.simplePdf
val outputFile = Files.createTempFile("test", ".pptx")

try {
  // Your test logic
} finally {
  TestFixtures.cleanupTempFiles(outputFile)
}
```

### Testing with Specific Backend

```scala
import com.tjclp.xlcr.test.TestBackends

// Skip test if backend not available
TestBackends.requireBackend("aspose")

// Find bridge by backend
val bridge = TestBackends.findBridgeByBackend(input, output, "aspose")
```

### Mock Bridges

```scala
val mockBridge = TestBackends.createMockBridge(
  ApplicationPdf,
  ApplicationVndOpenXmlFormatsPresentationmlPresentation,
  input => FileContent(output, "mock data".getBytes)
)
```

## Test Data

Sample files are in `data/` directory:
- `simple.pdf` - Single page PDF
- `multi-page.pdf` - Multi-page document
- `simple.xlsx` - Simple spreadsheet
- `corrupted.pdf` - Intentionally corrupted file
- `large-10mb.pdf` - Large file for size tests

## CI Integration

All unit and integration tests run on every commit.
Performance benchmarks run nightly.

## Best Practices

1. **Use fixtures** - Don't create test data manually
2. **Clean up** - Always delete temporary files
3. **Skip gracefully** - Use `requireBackend` for backend-specific tests
4. **Isolate** - Integration tests should not depend on each other
5. **Fast** - Keep unit tests under 1 second each
````

## Success Criteria

1. ✅ Integration test module (`src/it/scala`) created
2. ✅ Test utilities (TestFixtures, TestBackends, TestConfig) implemented
3. ✅ 15+ integration tests covering:
   - Multi-module interactions (3+ tests)
   - Backend selection (5+ tests)
   - Parallel processing (3+ tests)
   - E2E workflows (5+ tests)
4. ✅ Performance benchmark framework created
5. ✅ CI runs all test categories
6. ✅ Test coverage >80% for core module
7. ✅ `docs/TESTING.md` documentation created
8. ✅ Test data files organized in `data/` directory

## Rollout Plan

**Days 1-2**: Foundation
- Create integration test module structure
- Implement test utilities (Fixtures, Backends, Config)
- Write documentation

**Days 3-4**: Integration Tests
- MultiModuleIntegrationSpec (3 tests)
- BackendSelectionSpec (5 tests)
- ParallelProcessingSpec (3 tests)

**Days 5-6**: E2E Tests
- FullPipelineE2ESpec (5 tests)
- Test two-stage workflow
- Test strip-masters workflow

**Day 7**: Performance & Polish
- ConversionBenchmark implementation
- CI integration
- Code review and cleanup
- Measure coverage

## Dependencies

- **02-error-handling.md**: Rich errors make test assertions easier
- **03-configuration-management.md**: TestConfig depends on ApplicationConfig

## Risks and Mitigation

| Risk | Mitigation |
|------|------------|
| Tests slow down CI | Parallel execution, optimize test data size |
| Flaky tests (timing) | Use deterministic test config, avoid timing assertions |
| Backend availability in CI | Skip tests gracefully, document setup requirements |
| Test maintenance burden | Good fixtures reduce duplication |

## Future Enhancements

1. **Contract Tests**: Verify bridge implementations satisfy contracts
2. **Property-Based Testing**: Use ScalaCheck for exhaustive testing
3. **Visual Regression**: Compare output files visually
4. **Load Testing**: Test service under sustained load
5. **Chaos Engineering**: Inject failures to test resilience
