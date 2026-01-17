package com.tjclp.xlcr.v2.cli

import java.nio.file.Paths

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Tests for the Decline-based CLI command parsing.
 */
class CommandsSpec extends AnyFlatSpec with Matchers:

  import Commands._

  // ============================================================================
  // Convert Command Tests
  // ============================================================================

  "Commands.parse" should "parse convert command with -i and -o" in {
    val result = parse(Seq("convert", "-i", "input.pdf", "-o", "output.html"))

    result.isRight shouldBe true
    result.toOption.get match
      case CliCommand.Convert(args) =>
        args.input shouldBe Paths.get("input.pdf")
        args.output shouldBe Paths.get("output.html")
        args.verbose shouldBe false
        args.backend shouldBe None
      case _ => fail("Expected Convert command")
  }

  it should "parse convert command with --input and --output" in {
    val result = parse(Seq("convert", "--input", "doc.docx", "--output", "doc.pdf"))

    result.isRight shouldBe true
    result.toOption.get match
      case CliCommand.Convert(args) =>
        args.input shouldBe Paths.get("doc.docx")
        args.output shouldBe Paths.get("doc.pdf")
      case _ => fail("Expected Convert command")
  }

  it should "parse convert command with verbose flag" in {
    val result = parse(Seq("convert", "-i", "in.pdf", "-o", "out.html", "-v"))

    result.isRight shouldBe true
    result.toOption.get match
      case CliCommand.Convert(args) =>
        args.verbose shouldBe true
      case _ => fail("Expected Convert command")
  }

  it should "parse convert command with --verbose flag" in {
    val result = parse(Seq("convert", "-i", "in.pdf", "-o", "out.html", "--verbose"))

    result.isRight shouldBe true
    result.toOption.get match
      case CliCommand.Convert(args) =>
        args.verbose shouldBe true
      case _ => fail("Expected Convert command")
  }

  it should "parse convert command with backend option" in {
    val result = parse(Seq("convert", "-i", "in.pdf", "-o", "out.pptx", "-b", "aspose"))

    result.isRight shouldBe true
    result.toOption.get match
      case CliCommand.Convert(args) =>
        args.backend shouldBe Some(Backend.Aspose)
      case _ => fail("Expected Convert command")
  }

  it should "parse convert command with --backend option" in {
    val result = parse(Seq("convert", "-i", "in.pdf", "-o", "out.pptx", "--backend", "libreoffice"))

    result.isRight shouldBe true
    result.toOption.get match
      case CliCommand.Convert(args) =>
        args.backend shouldBe Some(Backend.LibreOffice)
      case _ => fail("Expected Convert command")
  }

  it should "parse convert command with all options" in {
    val result = parse(Seq(
      "convert",
      "-i", "input.pptx",
      "-o", "output.html",
      "-v",
      "-b", "xlcr"
    ))

    result.isRight shouldBe true
    result.toOption.get match
      case CliCommand.Convert(args) =>
        args.input shouldBe Paths.get("input.pptx")
        args.output shouldBe Paths.get("output.html")
        args.verbose shouldBe true
        args.backend shouldBe Some(Backend.Xlcr)
      case _ => fail("Expected Convert command")
  }

  it should "reject unknown backend values" in {
    val result = parse(Seq("convert", "-i", "in.pdf", "-o", "out.pptx", "-b", "unknown"))

    result.isLeft shouldBe true
    result.left.getOrElse(fail("Expected Help")).toString should include("Unknown backend")
  }

  // ============================================================================
  // Split Command Tests
  // ============================================================================

  it should "parse split command with -i and -d" in {
    val result = parse(Seq("split", "-i", "workbook.xlsx", "-d", "sheets/"))

    result.isRight shouldBe true
    result.toOption.get match
      case CliCommand.Split(args) =>
        args.input shouldBe Paths.get("workbook.xlsx")
        args.outputDir shouldBe Paths.get("sheets/")
        args.verbose shouldBe false
        args.backend shouldBe None
      case _ => fail("Expected Split command")
  }

  it should "parse split command with --output-dir" in {
    val result = parse(Seq("split", "-i", "doc.pdf", "--output-dir", "/tmp/pages"))

    result.isRight shouldBe true
    result.toOption.get match
      case CliCommand.Split(args) =>
        args.outputDir shouldBe Paths.get("/tmp/pages")
      case _ => fail("Expected Split command")
  }

  it should "parse split command with verbose flag" in {
    val result = parse(Seq("split", "-i", "file.pptx", "-d", "slides/", "-v"))

    result.isRight shouldBe true
    result.toOption.get match
      case CliCommand.Split(args) =>
        args.verbose shouldBe true
      case _ => fail("Expected Split command")
  }

  it should "parse split command with backend option" in {
    val result = parse(Seq("split", "-i", "file.pdf", "-d", "out/", "-b", "aspose"))

    result.isRight shouldBe true
    result.toOption.get match
      case CliCommand.Split(args) =>
        args.backend shouldBe Some(Backend.Aspose)
      case _ => fail("Expected Split command")
  }

  // ============================================================================
  // Info Command Tests
  // ============================================================================

  it should "parse info command with -i" in {
    val result = parse(Seq("info", "-i", "document.pdf"))

    result.isRight shouldBe true
    result.toOption.get match
      case CliCommand.Info(args) =>
        args.input shouldBe Paths.get("document.pdf")
      case _ => fail("Expected Info command")
  }

  it should "parse info command with --input" in {
    val result = parse(Seq("info", "--input", "spreadsheet.xlsx"))

    result.isRight shouldBe true
    result.toOption.get match
      case CliCommand.Info(args) =>
        args.input shouldBe Paths.get("spreadsheet.xlsx")
      case _ => fail("Expected Info command")
  }

  // ============================================================================
  // Path Handling Tests
  // ============================================================================

  it should "handle absolute paths" in {
    val result = parse(Seq("convert", "-i", "/home/user/doc.pdf", "-o", "/tmp/output.html"))

    result.isRight shouldBe true
    result.toOption.get match
      case CliCommand.Convert(args) =>
        args.input.isAbsolute shouldBe true
        args.output.isAbsolute shouldBe true
      case _ => fail("Expected Convert command")
  }

  it should "handle relative paths" in {
    val result = parse(Seq("convert", "-i", "../input/doc.pdf", "-o", "./out/result.html"))

    result.isRight shouldBe true
    result.toOption.get match
      case CliCommand.Convert(args) =>
        args.input shouldBe Paths.get("../input/doc.pdf")
        args.output shouldBe Paths.get("./out/result.html")
      case _ => fail("Expected Convert command")
  }

  it should "handle paths with spaces" in {
    val result = parse(Seq("convert", "-i", "my documents/file.pdf", "-o", "output folder/result.html"))

    result.isRight shouldBe true
    result.toOption.get match
      case CliCommand.Convert(args) =>
        args.input shouldBe Paths.get("my documents/file.pdf")
        args.output shouldBe Paths.get("output folder/result.html")
      case _ => fail("Expected Convert command")
  }

  // ============================================================================
  // Error Cases Tests
  // ============================================================================

  it should "return error for missing required input in convert" in {
    val result = parse(Seq("convert", "-o", "output.html"))

    result.isLeft shouldBe true
  }

  it should "return error for missing required output in convert" in {
    val result = parse(Seq("convert", "-i", "input.pdf"))

    result.isLeft shouldBe true
  }

  it should "return error for missing required input in split" in {
    val result = parse(Seq("split", "-d", "output/"))

    result.isLeft shouldBe true
  }

  it should "return error for missing required output dir in split" in {
    val result = parse(Seq("split", "-i", "input.pdf"))

    result.isLeft shouldBe true
  }

  it should "return error for missing required input in info" in {
    val result = parse(Seq("info"))

    result.isLeft shouldBe true
  }

  it should "return error for unknown subcommand" in {
    val result = parse(Seq("unknown", "-i", "file.pdf"))

    result.isLeft shouldBe true
  }

  it should "return error for empty arguments" in {
    val result = parse(Seq.empty)

    result.isLeft shouldBe true
  }

  it should "return error for unknown option" in {
    val result = parse(Seq("convert", "-i", "in.pdf", "-o", "out.html", "--unknown-flag"))

    result.isLeft shouldBe true
  }

  // ============================================================================
  // Help Tests
  // ============================================================================

  it should "return help for --help" in {
    val result = parse(Seq("--help"))

    result.isLeft shouldBe true
    val help = result.left.getOrElse(fail("Expected Help"))
    help.toString should include("xlcr")
    help.toString should include("convert")
    help.toString should include("split")
    help.toString should include("info")
  }

  it should "return help for convert --help" in {
    val result = parse(Seq("convert", "--help"))

    result.isLeft shouldBe true
    val help = result.left.getOrElse(fail("Expected Help"))
    help.toString should include("input")
    help.toString should include("output")
  }

  it should "return help for split --help" in {
    val result = parse(Seq("split", "--help"))

    result.isLeft shouldBe true
    val help = result.left.getOrElse(fail("Expected Help"))
    help.toString should include("input")
    help.toString should include("output-dir")
  }

  it should "return help for info --help" in {
    val result = parse(Seq("info", "--help"))

    result.isLeft shouldBe true
    val help = result.left.getOrElse(fail("Expected Help"))
    help.toString should include("input")
  }

  // ============================================================================
  // CliCommand Enum Tests
  // ============================================================================

  "CliCommand" should "have Convert variant" in {
    val args = ConvertArgs(Paths.get("in"), Paths.get("out"))
    val cmd = CliCommand.Convert(args)

    cmd match
      case CliCommand.Convert(a) => a shouldBe args
      case _ => fail("Expected Convert")
  }

  it should "have Split variant" in {
    val args = SplitArgs(Paths.get("in"), Paths.get("out"))
    val cmd = CliCommand.Split(args)

    cmd match
      case CliCommand.Split(a) => a shouldBe args
      case _ => fail("Expected Split")
  }

  it should "have Info variant" in {
    val args = InfoArgs(Paths.get("in"))
    val cmd = CliCommand.Info(args)

    cmd match
      case CliCommand.Info(a) => a shouldBe args
      case _ => fail("Expected Info")
  }

  // ============================================================================
  // Args Case Class Tests
  // ============================================================================

  "ConvertArgs" should "have correct defaults" in {
    val args = ConvertArgs(Paths.get("in"), Paths.get("out"))

    args.backend shouldBe None
    args.verbose shouldBe false
  }

  "SplitArgs" should "have correct defaults" in {
    val args = SplitArgs(Paths.get("in"), Paths.get("out"))

    args.backend shouldBe None
    args.verbose shouldBe false
  }
