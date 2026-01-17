package com.tjclp.xlcr.v2.cli

import java.nio.file.Path

import cats.syntax.all.*
import com.monovore.decline.*

/**
 * Decline-based CLI commands for XLCR v2.
 *
 * Provides functional, composable argument parsing with clear help text.
 */
object Commands:

  // ============================================================================
  // Argument Types
  // ============================================================================

  /** Arguments for the convert command */
  case class ConvertArgs(
      input: Path,
      output: Path,
      backend: Option[Backend] = None,
      verbose: Boolean = false,
      extensionOnly: Boolean = false
  )

  /** Arguments for the split command */
  case class SplitArgs(
      input: Path,
      outputDir: Path,
      backend: Option[Backend] = None,
      verbose: Boolean = false,
      extensionOnly: Boolean = false
  )

  /** Arguments for the info command */
  case class InfoArgs(
      input: Path,
      extensionOnly: Boolean = false,
      format: OutputFormat = OutputFormat.Text
  )

  /** Output format for the info command */
  enum OutputFormat:
    case Text
    case Json
    case Xml

  /** Sum type for all CLI commands */
  enum CliCommand:
    case Convert(args: ConvertArgs)
    case Split(args: SplitArgs)
    case Info(args: InfoArgs)

  /** Backend selection for CLI commands */
  enum Backend:
    case Aspose
    case LibreOffice
    case Xlcr

  // ============================================================================
  // Common Options
  // ============================================================================

  private val inputOpt: Opts[Path] =
    Opts.option[Path](
      long = "input",
      short = "i",
      help = "Input file path"
    )

  private val outputOpt: Opts[Path] =
    Opts.option[Path](
      long = "output",
      short = "o",
      help = "Output file path"
    )

  private val outputDirOpt: Opts[Path] =
    Opts.option[Path](
      long = "output-dir",
      short = "d",
      help = "Output directory for split files"
    )

  private val verboseFlag: Opts[Boolean] =
    Opts.flag(
      long = "verbose",
      short = "v",
      help = "Enable verbose output"
    ).orFalse

  private val extensionOnlyFlag: Opts[Boolean] =
    Opts.flag(
      long = "extension-only",
      help = "Use extension-based MIME detection only (skip content analysis)"
    ).orFalse

  private val jsonFlag: Opts[Boolean] =
    Opts.flag(
      long = "json",
      help = "Output in JSON format"
    ).orFalse

  private val xmlFlag: Opts[Boolean] =
    Opts.flag(
      long = "xml",
      help = "Output in XML format"
    ).orFalse

  private val outputFormatOpt: Opts[OutputFormat] =
    (jsonFlag, xmlFlag).mapN { (json, xml) =>
      if json then OutputFormat.Json
      else if xml then OutputFormat.Xml
      else OutputFormat.Text
    }

  private val backendOpt: Opts[Option[Backend]] =
    Opts.option[String](
      long = "backend",
      short = "b",
      help = "Backend to use: aspose, libreoffice, xlcr (default: auto with fallback chain)"
    ).mapValidated(parseBackend).orNone

  private def parseBackend(value: String) =
    value.toLowerCase match
      case "aspose" => Backend.Aspose.validNel
      case "libreoffice" => Backend.LibreOffice.validNel
      case "xlcr" => Backend.Xlcr.validNel
      case other =>
        s"Unknown backend: $other (expected: aspose, libreoffice, xlcr)".invalidNel

  // ============================================================================
  // Subcommands
  // ============================================================================

  private val convertOpts: Opts[ConvertArgs] =
    (inputOpt, outputOpt, backendOpt, verboseFlag, extensionOnlyFlag).mapN(ConvertArgs.apply)

  val convertCmd: Opts[CliCommand] =
    Opts.subcommand(
      name = "convert",
      help = "Convert a document to another format"
    )(convertOpts.map(CliCommand.Convert.apply))

  private val splitOpts: Opts[SplitArgs] =
    (inputOpt, outputDirOpt, backendOpt, verboseFlag, extensionOnlyFlag).mapN(SplitArgs.apply)

  val splitCmd: Opts[CliCommand] =
    Opts.subcommand(
      name = "split",
      help = "Split a document into parts (pages, sheets, slides)"
    )(splitOpts.map(CliCommand.Split.apply))

  private val infoOpts: Opts[InfoArgs] =
    (inputOpt, extensionOnlyFlag, outputFormatOpt).mapN(InfoArgs.apply)

  val infoCmd: Opts[CliCommand] =
    Opts.subcommand(
      name = "info",
      help = "Display information about a document"
    )(infoOpts.map(CliCommand.Info.apply))

  // ============================================================================
  // Main Command
  // ============================================================================

  val mainCommand: Command[CliCommand] = Command(
    name = "xlcr",
    header = "XLCR - Cross-format document conversion toolkit"
  )(
    convertCmd orElse splitCmd orElse infoCmd
  )

  /** Parse command line arguments */
  def parse(args: Seq[String]): Either[Help, CliCommand] =
    mainCommand.parse(args)
