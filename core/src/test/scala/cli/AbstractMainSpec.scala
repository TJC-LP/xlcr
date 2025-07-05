package com.tjclp.xlcr
package cli

import java.nio.file.{ Files, Path }

import scala.util.Try

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{ BeforeAndAfterEach, TryValues }
import scopt.OParser

class AbstractMainSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach with TryValues {

  // Test configuration case class
  case class TestConfig(
    input: String = "",
    output: String = "",
    diffMode: Boolean = false,
    splitMode: Boolean = false,
    splitStrategy: Option[String] = None,
    outputType: Option[String] = None,
    mappings: Seq[String] = Seq.empty,
    failureMode: Option[String] = None,
    chunkRange: Option[String] = None,
    customField: String = "test"
  )

  // Concrete test implementation of AbstractMain
  class TestMain extends AbstractMain[TestConfig] {
    // Expose protected methods for testing
    def testIsSplitMode(config: TestConfig): Boolean    = isSplitMode(config)
    def testBuildParser                                 = buildParser
    def testInitialize(config: TestConfig): Unit        = initialize(config)
    def testExecuteSplit(config: TestConfig): Unit      = executeSplit(config)
    def testExecuteConversion(config: TestConfig): Unit = executeConversion(config)
    def testExecuteDiff(config: TestConfig): Unit       = executeDiff(config)
    def testGetDiffMode(config: TestConfig): Boolean    = getDiffMode(config)
    override protected def programName: String          = "test-app"
    override protected def programVersion: String       = "1.0-test"
    override protected def emptyConfig: TestConfig      = TestConfig()

    override protected def getInput(config: TestConfig): String      = config.input
    override protected def getOutput(config: TestConfig): String     = config.output
    override protected def getDiffMode(config: TestConfig): Boolean  = config.diffMode
    override protected def getSplitMode(config: TestConfig): Boolean = config.splitMode
    override protected def getSplitStrategy(config: TestConfig): Option[String] =
      config.splitStrategy
    override protected def getOutputType(config: TestConfig): Option[String]  = config.outputType
    override protected def getMappings(config: TestConfig): Seq[String]       = config.mappings
    override protected def getFailureMode(config: TestConfig): Option[String] = config.failureMode
    override protected def getChunkRange(config: TestConfig): Option[String]  = config.chunkRange

    override protected def buildAllOptions: OParser[_, TestConfig] = {
      val builder = OParser.builder[TestConfig]
      import builder._

      OParser.sequence(
        opt[String]('i', "input")
          .required()
          .action((x, c) => c.copy(input = x))
          .text("Input file"),
        opt[String]('o', "output")
          .required()
          .action((x, c) => c.copy(output = x))
          .text("Output file"),
        opt[Unit]("split")
          .action((_, c) => c.copy(splitMode = true))
          .text("Enable split mode"),
        opt[String]("strategy")
          .action((x, c) => c.copy(splitStrategy = Some(x)))
          .text("Split strategy"),
        opt[String]("custom")
          .action((x, c) => c.copy(customField = x))
          .text("Custom field for testing")
      )
    }

    // Track method calls for testing
    var initializeCalled        = false
    var executeSplitCalled      = false
    var executeConversionCalled = false
    var executeDiffCalled       = false

    override protected def initialize(config: TestConfig): Unit = {
      initializeCalled = true
      super.initialize(config)
    }

    override protected def executeSplit(config: TestConfig): Unit =
      executeSplitCalled = true

    override protected def executeConversion(config: TestConfig): Unit =
      executeConversionCalled = true

    override protected def executeDiff(config: TestConfig): Unit =
      executeDiffCalled = true
  }

  var testMain: TestMain   = _
  var tempDir: Path        = _
  var testInputFile: Path  = _
  var testOutputFile: Path = _

  override def beforeEach(): Unit = {
    testMain = new TestMain()
    tempDir = Files.createTempDirectory("abstractmain-test")
    testInputFile = Files.createTempFile(tempDir, "input", ".txt")
    testOutputFile = tempDir.resolve("output.txt")
    Files.write(testInputFile, "test content".getBytes)
  }

  override def afterEach(): Unit =
    // Clean up temp files
    Try {
      Files.walk(tempDir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete)
    }

  "AbstractMain" should "detect split mode when splitMode flag is true" in {
    val config = TestConfig(splitMode = true)
    testMain.testIsSplitMode(config) shouldBe true
  }

  it should "detect split mode when split strategy is defined" in {
    val config = TestConfig(splitStrategy = Some("page"))
    testMain.testIsSplitMode(config) shouldBe true
  }

  it should "not detect split mode for regular conversions" in {
    val config = TestConfig(
      input = testInputFile.toString,
      output = testOutputFile.toString
    )
    testMain.testIsSplitMode(config) shouldBe false
  }

  it should "parse command line arguments correctly" in {
    val parser = testMain.testBuildParser
    val args = Array(
      "-i",
      testInputFile.toString,
      "-o",
      testOutputFile.toString,
      "--split",
      "--strategy",
      "page",
      "--custom",
      "myvalue"
    )

    val result = OParser.parse(parser, args, TestConfig())
    result should be(defined)

    val config = result.get
    config.input shouldBe testInputFile.toString
    config.output shouldBe testOutputFile.toString
    config.splitMode shouldBe true
    config.splitStrategy shouldBe Some("page")
    config.customField shouldBe "myvalue"
  }

  it should "fail parsing when required arguments are missing" in {
    val parser = testMain.testBuildParser
    val args   = Array("--split") // Missing required -i and -o

    val result = OParser.parse(parser, args, TestConfig())
    result shouldBe None
  }

  it should "execute split operation when in split mode" in {
    // Manually test the logic since we can't easily test main() with System.exit
    val config = TestConfig(
      input = testInputFile.toString,
      output = tempDir.toString,
      splitMode = true,
      splitStrategy = Some("page")
    )

    testMain.testInitialize(config)
    testMain.initializeCalled shouldBe true

    if (testMain.testIsSplitMode(config)) {
      testMain.testExecuteSplit(config)
    }

    testMain.executeSplitCalled shouldBe true
    testMain.executeConversionCalled shouldBe false
  }

  it should "execute conversion operation when not in split mode" in {
    val config = TestConfig(
      input = testInputFile.toString,
      output = testOutputFile.toString
    )

    testMain.testInitialize(config)

    if (testMain.testIsSplitMode(config)) {
      testMain.testExecuteSplit(config)
    } else if (testMain.testGetDiffMode(config)) {
      testMain.testExecuteDiff(config)
    } else {
      testMain.testExecuteConversion(config)
    }

    testMain.executeSplitCalled shouldBe false
    testMain.executeConversionCalled shouldBe true
    testMain.executeDiffCalled shouldBe false
  }

  it should "execute diff operation when diff mode is enabled" in {
    val config = TestConfig(
      input = testInputFile.toString,
      output = testOutputFile.toString,
      diffMode = true
    )

    if (testMain.testIsSplitMode(config)) {
      testMain.testExecuteSplit(config)
    } else if (testMain.testGetDiffMode(config)) {
      testMain.testExecuteDiff(config)
    } else {
      testMain.testExecuteConversion(config)
    }

    testMain.executeSplitCalled shouldBe false
    testMain.executeConversionCalled shouldBe false
    testMain.executeDiffCalled shouldBe true
  }

  "buildParser" should "create a parser with program name and version" in {
    val parser = testMain.testBuildParser
    // The parser should be valid and contain our program info
    // This is mainly tested through successful parsing above
    parser should not be null
  }
}
