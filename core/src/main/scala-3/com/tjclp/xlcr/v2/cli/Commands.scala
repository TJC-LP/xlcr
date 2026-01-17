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
      backend: Option[String] = None,
      verbose: Boolean = false,
      stripMasters: Boolean = false
  )

  /** Arguments for the split command */
  case class SplitArgs(
      input: Path,
      outputDir: Path,
      backend: Option[String] = None,
      verbose: Boolean = false
  )

  /** Arguments for the info command */
  case class InfoArgs(
      input: Path
  )

  /** Sum type for all CLI commands */
  enum CliCommand:
    case Convert(args: ConvertArgs)
    case Split(args: SplitArgs)
    case Info(args: InfoArgs)

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

  private val stripMastersFlag: Opts[Boolean] =
    Opts.flag(
      long = "strip-masters",
      help = "Strip master slides and layouts (PowerPoint only)"
    ).orFalse

  private val backendOpt: Opts[Option[String]] =
    Opts.option[String](
      long = "backend",
      short = "b",
      help = "Backend to use: aspose, libreoffice (default: auto with fallback)"
    ).orNone

  // ============================================================================
  // Subcommands
  // ============================================================================

  private val convertOpts: Opts[ConvertArgs] =
    (inputOpt, outputOpt, backendOpt, verboseFlag, stripMastersFlag).mapN(ConvertArgs.apply)

  val convertCmd: Opts[CliCommand] =
    Opts.subcommand(
      name = "convert",
      help = "Convert a document to another format"
    )(convertOpts.map(CliCommand.Convert.apply))

  private val splitOpts: Opts[SplitArgs] =
    (inputOpt, outputDirOpt, backendOpt, verboseFlag).mapN(SplitArgs.apply)

  val splitCmd: Opts[CliCommand] =
    Opts.subcommand(
      name = "split",
      help = "Split a document into parts (pages, sheets, slides)"
    )(splitOpts.map(CliCommand.Split.apply))

  private val infoOpts: Opts[InfoArgs] =
    inputOpt.map(InfoArgs.apply)

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
