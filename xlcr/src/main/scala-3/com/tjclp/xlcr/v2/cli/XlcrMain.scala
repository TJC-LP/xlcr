package com.tjclp.xlcr.v2.cli

import java.io.FileOutputStream
import java.nio.file.{ Files, Path }

import org.apache.tika.io.TikaInputStream
import org.apache.tika.metadata.{ HttpHeaders, Metadata }
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.sax.BodyContentHandler

import scala.util.Using

import zio.*

import com.tjclp.xlcr.v2.aspose.AsposeTransforms
import com.tjclp.xlcr.v2.cli.Commands.*
import com.tjclp.xlcr.v2.core.XlcrTransforms
import com.tjclp.xlcr.v2.libreoffice.LibreOfficeTransforms
import com.tjclp.xlcr.v2.output.{ FragmentNaming, MimeExtensions, ZipBuilder }
import com.tjclp.xlcr.v2.types.{ Content, Mime }

/**
 * XLCR CLI entry point with compile-time transform discovery.
 *
 * This CLI uses stateless, compile-time dispatch to backend transform objects. No runtime registry
 * initialization is required - zero startup overhead.
 *
 * Backend selection:
 *   - `--backend aspose` - Use Aspose exclusively
 *   - `--backend libreoffice` - Use LibreOffice exclusively
 *   - No flag (default) - Use Aspose with LibreOffice fallback
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

  // Ensure java.home and java.library.path are set before Aspose initializes
  // (critical for native images). In GraalVM native images, build-time -Djava.home
  // is not propagated to runtime. Aspose libraries need java.home for font resolution.
  // The native binary also needs java.library.path pointing to $JAVA_HOME/lib for AWT
  // (libawt.so). With both properties set, the native binary works with just JAVA_HOME.
  locally {
    if java.lang.System.getProperty("java.home") == null then
      val envJavaHome = java.lang.System.getenv("JAVA_HOME")
      if envJavaHome != null then java.lang.System.setProperty("java.home", envJavaHome)

    val javaHome = java.lang.System.getProperty("java.home")
    if javaHome != null then
      val libDir   = javaHome + "/lib"
      val existing = Option(java.lang.System.getProperty("java.library.path")).getOrElse("")
      if !existing.contains(libDir) then
        java.lang.System.setProperty("java.library.path", libDir + ":" + existing)
  }

  override def run: ZIO[ZIOAppArgs, Any, ExitCode] =
    for
      // No TransformInit.initialize() - zero startup overhead!
      args   <- ZIOAppArgs.getArgs
      result <- handleArgs(args.toList)
    yield result

  private def handleArgs(args: List[String]): ZIO[Any, Any, ExitCode] =
    // Handle special flags before Decline parsing
    args match
      case "--backend-info" :: _ =>
        showBackendInfo().as(ExitCode.success)
      case "--version" :: _ =>
        Console.printLine("xlcr version 0.1.3").as(ExitCode.success)
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
      case CliCommand.Split(args)   => runSplit(args)
      case CliCommand.Info(args)    => runInfo(args)

  // ============================================================================
  // Convert Command
  // ============================================================================

  private def runConvert(args: ConvertArgs): ZIO[Any, Throwable, ExitCode] =
    for
      _ <- ZIO.when(args.verbose)(
        Console.printLine(s"Converting ${args.input} to ${args.output}")
      )

      // Read input file and detect MIME type
      inputBytes <- ZIO.attemptBlocking(Files.readAllBytes(args.input))
      inputChunk = Chunk.fromArray(inputBytes)
      inputMime = if args.extensionOnly then
        Mime.fromFilename(args.input.getFileName.toString)
      else
        Mime.detect(inputChunk, args.input.getFileName.toString)
      outputMime = Mime.fromFilename(args.output.getFileName.toString)
      content    = Content.fromChunk(inputChunk, inputMime)

      _ <- ZIO.when(args.verbose)(
        Console.printLine(s"Input MIME: ${inputMime.value}, Output MIME: ${outputMime.value}")
      )

      // Show which backend will be used
      _ <- ZIO.when(args.verbose) {
        val backendName = args.backend match
          case Some(Backend.Aspose)      => "Aspose"
          case Some(Backend.LibreOffice) => "LibreOffice"
          case Some(Backend.Xlcr)        => "XLCR Core (POI/Tika)"
          case _                         => "Unified (Aspose → LibreOffice → XLCR Core fallback)"
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

      _ <- Console.printLine(
        s"Successfully converted ${args.input.getFileName} to ${args.output.getFileName}"
      )
    yield ExitCode.success

  // ============================================================================
  // Split Command
  // ============================================================================

  private def runSplit(args: SplitArgs): ZIO[Any, Throwable, ExitCode] =
    for
      _ <- ZIO.when(args.verbose)(
        Console.printLine(s"Splitting ${args.input} into ${args.output}")
      )

      // Read input file and detect MIME type
      inputBytes <- ZIO.attemptBlocking(Files.readAllBytes(args.input))
      inputChunk = Chunk.fromArray(inputBytes)
      inputMime = if args.extensionOnly then
        Mime.fromFilename(args.input.getFileName.toString)
      else
        Mime.detect(inputChunk, args.input.getFileName.toString)
      content = Content.fromChunk(inputChunk, inputMime)

      _ <- ZIO.when(args.verbose)(
        Console.printLine(s"Input MIME: ${inputMime.value}")
      )

      // Show which backend will be used
      _ <- ZIO.when(args.verbose) {
        val backendName = args.backend match
          case Some(Backend.Aspose)      => "Aspose"
          case Some(Backend.LibreOffice) => "LibreOffice"
          case Some(Backend.Xlcr)        => "XLCR Core (POI/Tika)"
          case _                         => "Unified (Aspose → LibreOffice → XLCR Core fallback)"
        Console.printLine(s"Using backend: $backendName")
      }

      // Perform split using selected backend
      fragments <- selectBackend(args.backend).split(content).mapError { err =>
        new RuntimeException(s"Split failed: ${err.message}")
      }

      // Output based on --extract flag
      _ <- if args.extract then
        // Extract fragments to directory
        writeFragmentsToDirectory(args.output, fragments, args.verbose)
      else
        // Create ZIP file (default)
        writeFragmentsToZip(args.output, fragments, args.verbose)

      _ <- Console.printLine(
        s"Successfully split ${args.input.getFileName} into ${fragments.size} parts"
      )
    yield ExitCode.success

  /**
   * Write fragments to a directory with standardized naming.
   */
  private def writeFragmentsToDirectory(
    outputDir: Path,
    fragments: Chunk[com.tjclp.xlcr.v2.types.DynamicFragment],
    verbose: Boolean
  ): ZIO[Any, Throwable, Unit] =
    for
      // Ensure output directory exists
      _ <- ZIO.attemptBlocking {
        if !Files.exists(outputDir) then
          Files.createDirectories(outputDir)
      }

      total = fragments.size

      // Write each fragment with standardized naming
      _ <- ZIO.foreachDiscard(fragments.zipWithIndex) { case (fragment, idx) =>
        val index      = idx + 1
        val name       = fragment.name.getOrElse(s"fragment_$index")
        val ext        = MimeExtensions.getExtensionOrDefault(fragment.mime)
        val filename   = FragmentNaming.buildFilename(index, total, name, ext)
        val outputPath = outputDir.resolve(filename)

        for
          _ <- ZIO.attemptBlocking(Files.write(outputPath, fragment.content.data.toArray))
          _ <- ZIO.when(verbose)(
            Console.printLine(s"  Wrote fragment: $outputPath")
          )
        yield ()
      }
    yield ()

  /**
   * Write fragments to a ZIP file with standardized naming.
   */
  private def writeFragmentsToZip(
    outputPath: Path,
    fragments: Chunk[com.tjclp.xlcr.v2.types.DynamicFragment],
    verbose: Boolean
  ): ZIO[Any, Throwable, Unit] =
    for
      // Ensure parent directory exists
      _ <- ZIO.attemptBlocking {
        val parent = outputPath.getParent
        if parent != null && !Files.exists(parent) then
          Files.createDirectories(parent)
      }

      // Build ZIP using shared utilities
      _ <- ZIO.attemptBlocking {
        Using.resource(new FileOutputStream(outputPath.toFile)) { fos =>
          ZipBuilder.writeZip(fragments, fos, "bin")
        }
      }

      _ <- ZIO.when(verbose)(
        Console.printLine(s"  Wrote ZIP: $outputPath")
      )
    yield ()

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

      // Read file and detect MIME type with metadata
      inputBytes <- ZIO.attemptBlocking(Files.readAllBytes(args.input))
      inputChunk = Chunk.fromArray(inputBytes)
      size <- ZIO.attemptBlocking(Files.size(args.input))

      inputMime = if args.extensionOnly then
        Mime.fromFilename(args.input.getFileName.toString)
      else
        Mime.detect(inputChunk, args.input.getFileName.toString)

      // Extract Tika metadata (skip if extension-only mode for speed)
      metadata <- if args.extensionOnly then
        ZIO.succeed(Map.empty[String, String])
      else
        ZIO.attemptBlocking(extractMetadata(inputChunk, args.input.getFileName.toString))

      // Check available conversions and splittability
      availableTargets = findAvailableConversions(inputMime)
      canSplitFile     = UnifiedTransforms.canSplit(inputMime)

      // Output based on format
      _ <- args.format match
        case OutputFormat.Json =>
          printInfoJson(args.input, size, inputMime, metadata, availableTargets, canSplitFile)
        case OutputFormat.Xml =>
          printInfoXml(args.input, size, inputMime, metadata, availableTargets, canSplitFile)
        case OutputFormat.Text =>
          printInfoText(args.input, size, inputMime, metadata, availableTargets, canSplitFile)
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
      case Some(Backend.Aspose)      => AsposeBackend
      case Some(Backend.LibreOffice) => LibreOfficeBackend
      case Some(Backend.Xlcr)        => XlcrBackend
      case _                         => UnifiedBackend

  /** Trait for backend dispatch */
  private trait BackendDispatch:
    def convert(
      input: Content[Mime],
      to: Mime
    ): ZIO[Any, com.tjclp.xlcr.v2.transform.TransformError, Content[Mime]]
    def split(input: Content[Mime]): ZIO[
      Any,
      com.tjclp.xlcr.v2.transform.TransformError,
      Chunk[com.tjclp.xlcr.v2.types.DynamicFragment]
    ]

  private object AsposeBackend extends BackendDispatch:
    def convert(input: Content[Mime], to: Mime) = AsposeTransforms.convert(input, to)
    def split(input: Content[Mime])             = AsposeTransforms.split(input)

  private object LibreOfficeBackend extends BackendDispatch:
    def convert(input: Content[Mime], to: Mime) = LibreOfficeTransforms.convert(input, to)
    def split(input: Content[Mime])             = LibreOfficeTransforms.split(input)

  private object XlcrBackend extends BackendDispatch:
    def convert(input: Content[Mime], to: Mime) = XlcrTransforms.convert(input, to)
    def split(input: Content[Mime])             = XlcrTransforms.split(input)

  private object UnifiedBackend extends BackendDispatch:
    def convert(input: Content[Mime], to: Mime) = UnifiedTransforms.convert(input, to)
    def split(input: Content[Mime])             = UnifiedTransforms.split(input)

  // ============================================================================
  // Utility Functions
  // ============================================================================

  private def getExtension(mime: Mime): String =
    mime.mimeType match
      case "application/pdf"                                                           => "pdf"
      case "text/html"                                                                 => "html"
      case "text/plain"                                                                => "txt"
      case "text/csv"                                                                  => "csv"
      case "text/tab-separated-values"                                                 => "tsv"
      case "application/json"                                                          => "json"
      case "application/xml" | "text/xml"                                              => "xml"
      case "application/vnd.openxmlformats-officedocument.wordprocessingml.document"   => "docx"
      case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"         => "xlsx"
      case "application/vnd.openxmlformats-officedocument.presentationml.presentation" => "pptx"
      case "application/msword"                                                        => "doc"
      case "application/vnd.ms-excel"                                                  => "xls"
      case "application/vnd.ms-powerpoint"                                             => "ppt"
      case "application/vnd.oasis.opendocument.text"                                   => "odt"
      case "application/vnd.oasis.opendocument.spreadsheet"                            => "ods"
      case "application/vnd.oasis.opendocument.presentation"                           => "odp"
      case "image/png"                                                                 => "png"
      case "image/jpeg"                                                                => "jpg"
      case "image/gif"                                                                 => "gif"
      case "image/svg+xml"                                                             => "svg"
      case _                                                                           => "bin"

  private def formatSize(bytes: Long): String =
    if bytes < 1024 then s"$bytes B"
    else if bytes < 1024 * 1024 then f"${bytes / 1024.0}%.1f KB"
    else if bytes < 1024 * 1024 * 1024 then f"${bytes / (1024.0 * 1024)}%.1f MB"
    else f"${bytes / (1024.0 * 1024 * 1024)}%.1f GB"

  private def findAvailableConversions(from: Mime): List[Mime] =
    // Check common target formats
    val targets = List(
      Mime.pdf,
      Mime.html,
      Mime.plain,
      Mime.csv,
      Mime.json,
      Mime.docx,
      Mime.xlsx,
      Mime.pptx,
      Mime.odt,
      Mime.ods,
      Mime.odp,
      Mime.png,
      Mime.jpeg
    )
    targets.filter(target => target != from && UnifiedTransforms.canConvert(from, target))

  // ============================================================================
  // Metadata Extraction
  // ============================================================================

  private def extractMetadata(data: Chunk[Byte], filename: String): Map[String, String] =
    if data.isEmpty then Map.empty
    else
      try
        val metadata = new Metadata()
        metadata.set(HttpHeaders.CONTENT_LOCATION, filename)
        val parser  = new AutoDetectParser()
        val handler = new BodyContentHandler(-1)
        Using.resource(TikaInputStream.get(new java.io.ByteArrayInputStream(data.toArray))) {
          stream =>
            parser.parse(stream, handler, metadata)
        }
        metadata.names().toList.map(name => name -> metadata.get(name)).toMap
      catch
        case _: Exception => Map.empty

  // ============================================================================
  // Info Output Formatters
  // ============================================================================

  private def printInfoText(
    path: Path,
    size: Long,
    mime: Mime,
    metadata: Map[String, String],
    conversions: List[Mime],
    canSplit: Boolean
  ): ZIO[Any, Throwable, Unit] =
    for
      _ <- Console.printLine(s"File: $path")
      _ <- Console.printLine(s"Size: ${formatSize(size)}")
      _ <- Console.printLine(s"MIME Type: ${mime.value}")
      _ <- Console.printLine(s"Base Type: ${mime.baseType}")
      _ <- Console.printLine(s"Sub Type: ${mime.subType}")
      _ <- Console.printLine("")
      _ <- Console.printLine("Available conversions:")
      _ <- ZIO.foreachDiscard(conversions)(t => Console.printLine(s"  -> ${t.value}"))
      _ <- Console.printLine("")
      _ <- Console.printLine(s"Splittable: $canSplit")
      _ <- ZIO.when(metadata.nonEmpty) {
        for
          _ <- Console.printLine("")
          _ <- Console.printLine("Metadata:")
          _ <- ZIO.foreachDiscard(metadata.toList.sortBy(_._1)) { case (k, v) =>
            Console.printLine(s"  $k: $v")
          }
        yield ()
      }
    yield ()

  private def printInfoJson(
    path: Path,
    size: Long,
    mime: Mime,
    metadata: Map[String, String],
    conversions: List[Mime],
    canSplit: Boolean
  ): ZIO[Any, Throwable, Unit] =
    val metadataJson = metadata.map { case (k, v) =>
      s""""${escapeJson(k)}": "${escapeJson(v)}""""
    }.mkString(",\n    ")

    val conversionsJson = conversions.map(m => s""""${m.value}"""").mkString(", ")

    val json = s"""{
  "file": "${escapeJson(path.toString)}",
  "size": $size,
  "sizeFormatted": "${formatSize(size)}",
  "mimeType": "${mime.value}",
  "baseType": "${mime.baseType}",
  "subType": "${mime.subType}",
  "availableConversions": [$conversionsJson],
  "splittable": $canSplit,
  "metadata": {
    $metadataJson
  }
}"""
    Console.printLine(json)

  private def printInfoXml(
    path: Path,
    size: Long,
    mime: Mime,
    metadata: Map[String, String],
    conversions: List[Mime],
    canSplit: Boolean
  ): ZIO[Any, Throwable, Unit] =
    val metadataXml = metadata.map { case (k, v) =>
      s"""    <entry key="${escapeXml(k)}">${escapeXml(v)}</entry>"""
    }.mkString("\n")

    val conversionsXml =
      conversions.map(m => s"""    <conversion>${escapeXml(m.value)}</conversion>""").mkString("\n")

    val xml = s"""<?xml version="1.0" encoding="UTF-8"?>
<fileInfo>
  <file>${escapeXml(path.toString)}</file>
  <size>$size</size>
  <sizeFormatted>${formatSize(size)}</sizeFormatted>
  <mimeType>${escapeXml(mime.value)}</mimeType>
  <baseType>${escapeXml(mime.baseType)}</baseType>
  <subType>${escapeXml(mime.subType)}</subType>
  <availableConversions>
$conversionsXml
  </availableConversions>
  <splittable>$canSplit</splittable>
  <metadata>
$metadataXml
  </metadata>
</fileInfo>"""
    Console.printLine(xml)

  private def escapeJson(s: String): String =
    s.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")

  private def escapeXml(s: String): String =
    s.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")
