package com.tjclp.xlcr.v2.cli

import java.nio.file.{Files, Path}

import zio.*

import com.tjclp.xlcr.v2.cli.Commands.*
import com.tjclp.xlcr.v2.pipeline.Pipeline
import com.tjclp.xlcr.v2.types.{Content, Mime}

/**
 * ZIO-based CLI entry point for XLCR v2.
 *
 * Uses Decline for argument parsing and the v2 Pipeline API for transforms.
 */
object Main extends ZIOAppDefault:

  override def run: ZIO[ZIOAppArgs, Any, ExitCode] =
    for
      args <- ZIOAppArgs.getArgs
      result <- Commands.parse(args.toList) match
        case Left(help) =>
          // Distinguish between --help request and parse errors
          if help.errors.isEmpty then
            // User requested help (--help flag)
            Console.printLine(help).as(ExitCode.success)
          else
            // Parse error - show help with errors
            Console.printLineError(help.toString).as(ExitCode.failure)
        case Right(cmd) =>
          executeCommand(cmd).catchAll { err =>
            Console.printLineError(s"Error: $err").as(ExitCode.failure)
          }
    yield result

  private def executeCommand(cmd: CliCommand): ZIO[Any, Throwable, ExitCode] =
    cmd match
      case CliCommand.Convert(args) => runConvert(args)
      case CliCommand.Split(args) => runSplit(args)
      case CliCommand.Info(args) => runInfo(args)

  // ============================================================================
  // Convert Command
  // ============================================================================

  private def runConvert(args: ConvertArgs): ZIO[Any, Throwable, ExitCode] =
    for
      _ <- ZIO.when(args.verbose)(
        Console.printLine(s"Converting ${args.input} to ${args.output}")
      )

      // Read input file
      inputBytes <- ZIO.attemptBlocking(Files.readAllBytes(args.input))
      inputMime = Mime.fromFilename(args.input.getFileName.toString)
      outputMime = Mime.fromFilename(args.output.getFileName.toString)
      content = Content.fromChunk(Chunk.fromArray(inputBytes), inputMime)

      _ <- ZIO.when(args.verbose)(
        Console.printLine(s"Input MIME: ${inputMime.value}, Output MIME: ${outputMime.value}")
      )

      // Perform conversion
      result <- Pipeline.convert(content, outputMime).mapError { err =>
        new RuntimeException(s"Conversion failed: ${err.message}")
      }

      // Write output file
      _ <- ZIO.attemptBlocking(Files.write(args.output, result.data.toArray))

      _ <- ZIO.when(args.verbose)(
        Console.printLine(s"Wrote ${result.size} bytes to ${args.output}")
      )

      _ <- Console.printLine(s"Successfully converted ${args.input.getFileName} to ${args.output.getFileName}")
    yield ExitCode.success

  // ============================================================================
  // Split Command
  // ============================================================================

  private def runSplit(args: SplitArgs): ZIO[Any, Throwable, ExitCode] =
    for
      _ <- ZIO.when(args.verbose)(
        Console.printLine(s"Splitting ${args.input} into ${args.outputDir}")
      )

      // Ensure output directory exists
      _ <- ZIO.attemptBlocking {
        if !Files.exists(args.outputDir) then
          Files.createDirectories(args.outputDir)
      }

      // Read input file
      inputBytes <- ZIO.attemptBlocking(Files.readAllBytes(args.input))
      inputMime = Mime.fromFilename(args.input.getFileName.toString)
      content = Content.fromChunk(Chunk.fromArray(inputBytes), inputMime)

      _ <- ZIO.when(args.verbose)(
        Console.printLine(s"Input MIME: ${inputMime.value}")
      )

      // Perform split
      fragments <- Pipeline.split(content).mapError { err =>
        new RuntimeException(s"Split failed: ${err.message}")
      }

      // Write fragments to output directory
      _ <- ZIO.foreachDiscard(fragments) { fragment =>
        val baseName = args.input.getFileName.toString.replaceFirst("\\.[^.]+$", "")
        val extension = getExtension(fragment.content.mime)
        val fragmentName = fragment.name.getOrElse(s"part-${fragment.index}")
        val sanitizedName = fragmentName.replaceAll("[^a-zA-Z0-9._-]", "_")
        val outputPath = args.outputDir.resolve(s"${baseName}_${sanitizedName}.$extension")

        for
          _ <- ZIO.attemptBlocking(Files.write(outputPath, fragment.content.data.toArray))
          _ <- ZIO.when(args.verbose)(
            Console.printLine(s"  Wrote fragment: $outputPath")
          )
        yield ()
      }

      _ <- Console.printLine(s"Successfully split ${args.input.getFileName} into ${fragments.size} parts")
    yield ExitCode.success

  // ============================================================================
  // Info Command
  // ============================================================================

  private def runInfo(args: InfoArgs): ZIO[Any, Throwable, ExitCode] =
    for
      // Check file exists
      exists <- ZIO.attemptBlocking(Files.exists(args.input))
      _ <- ZIO.unless(exists)(
        ZIO.fail(new RuntimeException(s"File not found: ${args.input}"))
      )

      // Get file info
      size <- ZIO.attemptBlocking(Files.size(args.input))
      inputMime = Mime.fromFilename(args.input.getFileName.toString)

      // Display info
      _ <- Console.printLine(s"File: ${args.input}")
      _ <- Console.printLine(s"Size: ${formatSize(size)}")
      _ <- Console.printLine(s"MIME Type: ${inputMime.value}")
      _ <- Console.printLine(s"Base Type: ${inputMime.baseType}")
      _ <- Console.printLine(s"Sub Type: ${inputMime.subType}")

      // Check available conversions
      _ <- Console.printLine("")
      _ <- Console.printLine("Available conversions:")
      availableTargets = findAvailableConversions(inputMime)
      _ <- ZIO.foreachDiscard(availableTargets) { target =>
        Console.printLine(s"  -> ${target.value}")
      }

      // Check if splittable
      canSplit = Pipeline.canConvert(inputMime, inputMime) // Simplified check
      _ <- Console.printLine("")
      _ <- Console.printLine(s"Splittable: $canSplit")
    yield ExitCode.success

  // ============================================================================
  // Utility Functions
  // ============================================================================

  private def getExtension(mime: Mime): String =
    mime.mimeType match
      case "application/pdf" => "pdf"
      case "text/html" => "html"
      case "text/plain" => "txt"
      case "text/csv" => "csv"
      case "text/tab-separated-values" => "tsv"
      case "application/json" => "json"
      case "application/xml" | "text/xml" => "xml"
      case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" => "docx"
      case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" => "xlsx"
      case "application/vnd.openxmlformats-officedocument.presentationml.presentation" => "pptx"
      case "application/msword" => "doc"
      case "application/vnd.ms-excel" => "xls"
      case "application/vnd.ms-powerpoint" => "ppt"
      case "application/vnd.oasis.opendocument.text" => "odt"
      case "application/vnd.oasis.opendocument.spreadsheet" => "ods"
      case "application/vnd.oasis.opendocument.presentation" => "odp"
      case "image/png" => "png"
      case "image/jpeg" => "jpg"
      case "image/gif" => "gif"
      case "image/svg+xml" => "svg"
      case _ => "bin"

  private def formatSize(bytes: Long): String =
    if bytes < 1024 then s"$bytes B"
    else if bytes < 1024 * 1024 then f"${bytes / 1024.0}%.1f KB"
    else if bytes < 1024 * 1024 * 1024 then f"${bytes / (1024.0 * 1024)}%.1f MB"
    else f"${bytes / (1024.0 * 1024 * 1024)}%.1f GB"

  private def findAvailableConversions(from: Mime): List[Mime] =
    // Check common target formats
    val targets = List(
      Mime.pdf, Mime.html, Mime.plain, Mime.csv, Mime.json,
      Mime.docx, Mime.xlsx, Mime.pptx,
      Mime.odt, Mime.ods, Mime.odp
    )
    targets.filter(target => target != from && Pipeline.canConvert(from, target))
