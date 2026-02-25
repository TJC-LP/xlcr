package com.tjclp.xlcr.cli

import java.nio.file.Path

import com.tjclp.xlcr.types.*

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
    extensionOnly: Boolean = false,
    options: ConvertOptions = ConvertOptions()
  )

  /** Arguments for the split command */
  case class SplitArgs(
    input: Path,
    output: Path,
    backend: Option[Backend] = None,
    verbose: Boolean = false,
    extensionOnly: Boolean = false,
    extract: Boolean = false,
    options: ConvertOptions = ConvertOptions()
  )

  /** Arguments for the info command */
  case class InfoArgs(
    input: Path,
    extensionOnly: Boolean = false,
    format: OutputFormat = OutputFormat.Text
  )

  /** Arguments for the server command (pure data — no HTTP imports) */
  case class ServerArgs(
    host: Option[String] = None,
    port: Option[Int] = None,
    maxRequestSize: Option[Long] = None
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
    case Server(args: ServerArgs)

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
      help = "Output path (ZIP file by default, or directory with --extract)"
    )

  private val extractFlag: Opts[Boolean] =
    Opts.flag(
      long = "extract",
      short = "x",
      help = "Extract fragments to directory instead of creating a ZIP file"
    ).orFalse

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
      case "aspose"      => Backend.Aspose.validNel
      case "libreoffice" => Backend.LibreOffice.validNel
      case "xlcr"        => Backend.Xlcr.validNel
      case other         =>
        s"Unknown backend: $other (expected: aspose, libreoffice, xlcr)".invalidNel

  // ============================================================================
  // Conversion Options
  // ============================================================================

  private val passwordOpt: Opts[Option[String]] =
    Opts.option[String]("password", help = "Password for encrypted documents").orNone

  private val noEvalFormulasFlag: Opts[Boolean] =
    Opts.flag("no-evaluate-formulas", help = "Skip formula evaluation before conversion").orFalse

  private val oneSheetPerPageFlag: Opts[Boolean] =
    Opts.flag("one-sheet-per-page", help = "Output one sheet per page in PDF").orFalse

  private val landscapeFlag: Opts[Option[Boolean]] =
    Opts
      .flag("landscape", help = "Force landscape orientation")
      .map(_ => Some(true))
      .orElse(
        Opts.flag("portrait", help = "Force portrait orientation").map(_ => Some(false))
      )
      .orElse(Opts(None))

  private val paperSizeOpt: Opts[Option[PaperSize]] =
    Opts
      .option[String](
        "paper-size",
        help = "Paper size: a4, letter, legal, a3, a5, tabloid"
      )
      .mapValidated { s =>
        PaperSize.fromString(s) match
          case Some(ps) => ps.validNel
          case None     => s"Unknown paper size: $s".invalidNel
      }
      .orNone

  private val sheetNamesOpt: Opts[List[String]] =
    Opts.options[String]("sheet", help = "Sheet name to include (repeatable)").orEmpty.map(_.toList)

  private val excludeHiddenFlag: Opts[Boolean] =
    Opts.flag("exclude-hidden", help = "Exclude hidden sheets").orFalse

  private val stripMastersFlag: Opts[Boolean] =
    Opts.flag("strip-masters", help = "Strip master/layout slides from PowerPoint").orFalse

  private val fixedLayoutFlag: Opts[Boolean] =
    Opts.flag("fixed-layout", help = "Use fixed layout for PDF to HTML (default: flowing)").orFalse

  private val noEmbedResourcesFlag: Opts[Boolean] =
    Opts
      .flag("no-embed-resources", help = "Don't embed resources into HTML output")
      .orFalse

  private val convertOptionsOpts: Opts[ConvertOptions] =
    (
      passwordOpt,
      noEvalFormulasFlag,
      oneSheetPerPageFlag,
      landscapeFlag,
      paperSizeOpt,
      sheetNamesOpt,
      excludeHiddenFlag,
      stripMastersFlag,
      fixedLayoutFlag,
      noEmbedResourcesFlag
    ).mapN {
      (
        password,
        noEvalFormulas,
        oneSheet,
        landscape,
        paper,
        sheets,
        exclHidden,
        stripM,
        fixedLayout,
        noEmbed
      ) =>
        ConvertOptions(
          password = password,
          evaluateFormulas = !noEvalFormulas,
          oneSheetPerPage = oneSheet,
          landscape = landscape,
          paperSize = paper,
          sheetNames = sheets,
          excludeHidden = exclHidden,
          stripMasters = stripM,
          flowingLayout = !fixedLayout,
          embedResources = !noEmbed
        )
    }

  // ============================================================================
  // Subcommands
  // ============================================================================

  private val convertOpts: Opts[ConvertArgs] =
    (inputOpt, outputOpt, backendOpt, verboseFlag, extensionOnlyFlag, convertOptionsOpts).mapN(
      ConvertArgs.apply
    )

  val convertCmd: Opts[CliCommand] =
    Opts.subcommand(
      name = "convert",
      help = "Convert a document to another format"
    )(convertOpts.map(CliCommand.Convert.apply))

  private val splitOpts: Opts[SplitArgs] =
    (
      inputOpt,
      outputDirOpt,
      backendOpt,
      verboseFlag,
      extensionOnlyFlag,
      extractFlag,
      convertOptionsOpts
    ).mapN(SplitArgs.apply)

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
  // Server Subcommand
  // ============================================================================

  private val serverHostOpt: Opts[Option[String]] =
    Opts.option[String](
      long = "host",
      help = "Host to bind to (default: 0.0.0.0, or XLCR_HOST env)"
    ).orNone

  private val serverPortOpt: Opts[Option[Int]] =
    Opts.option[Int](
      long = "port",
      short = "p",
      help = "Port to listen on (default: 8080, or XLCR_PORT env)"
    ).orNone

  private val serverMaxRequestSizeOpt: Opts[Option[Long]] =
    Opts.option[Long](
      long = "max-request-size",
      help = "Maximum request body size in bytes (default: 104857600)"
    ).orNone

  private val serverStartOpts: Opts[ServerArgs] =
    (serverHostOpt, serverPortOpt, serverMaxRequestSizeOpt).mapN(ServerArgs.apply)

  private val serverStartCmd: Opts[CliCommand] =
    Opts.subcommand(
      name = "start",
      help = "Start the HTTP server"
    )(serverStartOpts.map(CliCommand.Server.apply))

  val serverCmd: Opts[CliCommand] =
    Opts.subcommand(
      name = "server",
      help = "HTTP server for document conversion API"
    )(serverStartCmd)

  // ============================================================================
  // Main Command
  // ============================================================================

  val mainCommand: Command[CliCommand] = Command(
    name = "xlcr",
    header = "XLCR - Cross-format document conversion toolkit"
  )(
    convertCmd.orElse(splitCmd).orElse(infoCmd).orElse(serverCmd)
  )

  /** Parse command line arguments */
  def parse(args: Seq[String]): Either[Help, CliCommand] =
    mainCommand.parse(args)
end Commands
