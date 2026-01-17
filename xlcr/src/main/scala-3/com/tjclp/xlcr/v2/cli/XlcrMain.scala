package com.tjclp.xlcr.v2.cli

import java.nio.file.Files

import zio.*

import com.tjclp.xlcr.v2.aspose.AsposeTransforms
import com.tjclp.xlcr.v2.cli.Commands.*
import com.tjclp.xlcr.v2.core.XlcrTransforms
import com.tjclp.xlcr.v2.libreoffice.LibreOfficeTransforms
import com.tjclp.xlcr.v2.types.{Content, Mime}

/**
 * XLCR CLI entry point with compile-time transform discovery.
 *
 * This CLI uses stateless, compile-time dispatch to backend transform objects.
 * No runtime registry initialization is required - zero startup overhead.
 *
 * Backend selection:
 * - `--backend aspose` - Use Aspose exclusively
 * - `--backend libreoffice` - Use LibreOffice exclusively
 * - No flag (default) - Use Aspose with LibreOffice fallback
 *
 * Usage:
 * {{{
 * xlcr convert -i input.docx -o output.pdf
 * xlcr convert -i input.docx -o output.pdf --backend libreoffice
 * xlcr split -i input.xlsx -d ./output/
 * xlcr info -i document.pdf
 * xlcr --backend-info
 * }}}
 */
object XlcrMain extends ZIOAppDefault:

  override def run: ZIO[ZIOAppArgs, Any, ExitCode] =
    for
      // No TransformInit.initialize() - zero startup overhead!
      args <- ZIOAppArgs.getArgs
      result <- handleArgs(args.toList)
    yield result

  private def handleArgs(args: List[String]): ZIO[Any, Any, ExitCode] =
    // Handle special flags before Decline parsing
    args match
      case "--backend-info" :: _ =>
        showBackendInfo().as(ExitCode.success)
      case "--version" :: _ =>
        Console.printLine("xlcr version 2.0.0").as(ExitCode.success)
      case _ =>
        parseAndExecute(args)

  private def parseAndExecute(args: List[String]): ZIO[Any, Any, ExitCode] =
    Commands.parse(args) match
      case Left(help) =>
        if help.errors.isEmpty then
          Console.printLine(help).as(ExitCode.success)
        else
          Console.printLineError(help.toString).as(ExitCode.failure)
      case Right(cmd) =>
        executeCommand(cmd).catchAll { err =>
          Console.printLineError(s"Error: $err").as(ExitCode.failure)
        }

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

      // Show which backend will be used
      _ <- ZIO.when(args.verbose) {
        val backendName = args.backend match
          case Some(Backend.Aspose) => "Aspose"
          case Some(Backend.LibreOffice) => "LibreOffice"
          case Some(Backend.Xlcr) => "XLCR Core (POI/Tika)"
          case _ => "Unified (Aspose → LibreOffice → XLCR Core fallback)"
        Console.printLine(s"Using backend: $backendName")
      }

      // Perform conversion using selected backend
      result <- selectBackend(args.backend).convert(content, outputMime).mapError { err =>
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

      // Show which backend will be used
      _ <- ZIO.when(args.verbose) {
        val backendName = args.backend match
          case Some(Backend.Aspose) => "Aspose"
          case Some(Backend.LibreOffice) => "LibreOffice"
          case Some(Backend.Xlcr) => "XLCR Core (POI/Tika)"
          case _ => "Unified (Aspose → LibreOffice → XLCR Core fallback)"
        Console.printLine(s"Using backend: $backendName")
      }

      // Perform split using selected backend
      fragments <- selectBackend(args.backend).split(content).mapError { err =>
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
      canSplitFile = UnifiedTransforms.canSplit(inputMime)
      _ <- Console.printLine("")
      _ <- Console.printLine(s"Splittable: $canSplitFile")
    yield ExitCode.success

  // ============================================================================
  // Backend Info
  // ============================================================================

  private def showBackendInfo(): ZIO[Any, Throwable, Unit] =
    for
      _ <- Console.printLine("XLCR Backend Status:")
      _ <- Console.printLine("")
      _ <- Console.printLine("  Compile-time transform discovery (no runtime registry)")
      _ <- Console.printLine("")
      _ <- Console.printLine("  Aspose Backend:")
      _ <- checkAsposeStatus()
      _ <- Console.printLine("")
      _ <- Console.printLine("  LibreOffice Backend:")
      _ <- checkLibreOfficeStatus()
      _ <- Console.printLine("")
      _ <- Console.printLine("  XLCR Core Backend:")
      _ <- checkXlcrCoreStatus()
    yield ()

  private def checkAsposeStatus(): ZIO[Any, Throwable, Unit] =
    ZIO.attempt {
      // Check if license env var is set
      val envVarSet = Option(java.lang.System.getenv("ASPOSE_TOTAL_LICENSE_B64")).exists(_.nonEmpty)
      val licFileExists = java.nio.file.Files.exists(
        java.nio.file.Paths.get("Aspose.Java.Total.lic")
      )
      if envVarSet then
        println("    License: Found (env ASPOSE_TOTAL_LICENSE_B64)")
      else if licFileExists then
        println("    License: Found (local file)")
      else
        println("    License: Evaluation mode (no license found)")

      // List supported conversions
      println("    Supported conversions: DOCX/DOC/XLSX/XLS/PPTX/PPT -> PDF, PDF <-> HTML, etc.")
      println("    Supported splits: XLSX/XLS sheets, PPTX/PPT slides, PDF pages, DOCX sections")
    }.unit

  private def checkLibreOfficeStatus(): ZIO[Any, Throwable, Unit] =
    ZIO.attempt {
      val status = com.tjclp.xlcr.config.LibreOfficeConfig.availabilityStatus()
      println(s"    Status: $status")
      println("    Supported conversions: DOCX/DOC/XLSX/XLS/PPTX/PPT/ODT/ODP/RTF -> PDF")
      println("    Supported splits: None (use Aspose for splitting)")
    }.unit

  private def checkXlcrCoreStatus(): ZIO[Any, Throwable, Unit] =
    ZIO.attempt {
      println("    Status: Always available (built-in)")
      println("    Libraries: Apache Tika, Apache POI, Apache PDFBox, ODFDOM")
      println("    Supported conversions:")
      println("      - ANY -> text/plain (Tika text extraction)")
      println("      - ANY -> application/xml (Tika XML extraction)")
      println("      - XLSX -> ODS (POI + ODFDOM)")
      println("    Supported splits:")
      println("      - XLSX/XLS/ODS sheets (POI/ODFDOM)")
      println("      - PPTX slides (POI)")
      println("      - DOCX sections (POI)")
      println("      - PDF pages (PDFBox)")
      println("      - Text paragraphs, CSV rows")
      println("      - EML/MSG attachments (Jakarta Mail / POI HSMF)")
      println("      - ZIP entries (java.util.zip)")
    }.unit

  // ============================================================================
  // Backend Selection
  // ============================================================================

  /** Select the appropriate backend based on user choice */
  private def selectBackend(backend: Option[Backend]): BackendDispatch =
    backend match
      case Some(Backend.Aspose) => AsposeBackend
      case Some(Backend.LibreOffice) => LibreOfficeBackend
      case Some(Backend.Xlcr) => XlcrBackend
      case _ => UnifiedBackend

  /** Trait for backend dispatch */
  private trait BackendDispatch:
    def convert(input: Content[Mime], to: Mime): ZIO[Any, com.tjclp.xlcr.v2.transform.TransformError, Content[Mime]]
    def split(input: Content[Mime]): ZIO[Any, com.tjclp.xlcr.v2.transform.TransformError, Chunk[com.tjclp.xlcr.v2.types.DynamicFragment]]

  private object AsposeBackend extends BackendDispatch:
    def convert(input: Content[Mime], to: Mime) = AsposeTransforms.convert(input, to)
    def split(input: Content[Mime]) = AsposeTransforms.split(input)

  private object LibreOfficeBackend extends BackendDispatch:
    def convert(input: Content[Mime], to: Mime) = LibreOfficeTransforms.convert(input, to)
    def split(input: Content[Mime]) = LibreOfficeTransforms.split(input)

  private object XlcrBackend extends BackendDispatch:
    def convert(input: Content[Mime], to: Mime) = XlcrTransforms.convert(input, to)
    def split(input: Content[Mime]) = XlcrTransforms.split(input)

  private object UnifiedBackend extends BackendDispatch:
    def convert(input: Content[Mime], to: Mime) = UnifiedTransforms.convert(input, to)
    def split(input: Content[Mime]) = UnifiedTransforms.split(input)

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
      Mime.odt, Mime.ods, Mime.odp,
      Mime.png, Mime.jpeg
    )
    targets.filter(target => target != from && UnifiedTransforms.canConvert(from, target))
