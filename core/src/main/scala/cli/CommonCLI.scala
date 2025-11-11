package com.tjclp.xlcr
package cli

import scopt.OParser

import types.{ FileType, MimeType }

/**
 * Common CLI utilities shared between core and module-specific Main entry points. This allows code
 * reuse while avoiding circular dependencies.
 */
object CommonCLI {

  /**
   * Common configuration used by all CLI variants
   */
  case class BaseConfig(
    input: String = "",
    output: String = "",
    diffMode: Boolean = false,
    splitMode: Boolean = false,
    splitStrategy: Option[String] = None,
    outputType: Option[String] = None,
    outputFormat: Option[String] = None,
    maxImageWidth: Int = 2000,
    maxImageHeight: Int = 2000,
    maxImageSizeBytes: Long = 1024 * 1024 * 5, // 5MB default
    imageDpi: Int = 300,
    jpegQuality: Float = 0.85f,
    recursiveExtraction: Boolean = false,
    maxRecursionDepth: Int = 5,
    mappings: Seq[String] =
      Seq.empty, // strings like "xlsx=json" or "application/pdf=application/xml"
    failureMode: Option[String] = None,
    failureContext: Map[String, String] = Map.empty,
    chunkRange: Option[String] = None,
    // Parallel processing options
    threads: Int = 1,                 // Default to serial processing (1 thread)
    errorMode: Option[String] = None, // fail-fast, continue, skip
    enableProgress: Boolean = true,   // Enable progress reporting by default for batch operations
    progressIntervalMs: Long = 2000,  // Progress update interval in milliseconds
    verbose: Boolean = false,         // Verbose logging for individual file operations
    // Backend selection
    backend: Option[String] = None // Explicit backend selection (aspose, libreoffice, core, tika)
  )

  /**
   * Attempt to interpret a string as either a known MIME type or a known file extension.
   */
  def parseMimeOrExtension(str: String): Option[MimeType] = {
    if (str == null || str.isEmpty) {
      return None
    }

    // Check if it looks like a MIME type (contains a slash)
    if (str.contains("/")) {
      // Try direct MIME type parse
      MimeType.fromString(str)
    } else {
      // Attempt to interpret it as an extension
      // remove any leading dot, e.g. ".xlsx" -> "xlsx"
      val ext =
        if (str.startsWith(".")) str.substring(1).toLowerCase
        else str.toLowerCase

      // See if there's a matching FileType
      val maybeFt = FileType.fromExtension(ext)
      maybeFt.map(_.getMimeType)
    }
  }

  /**
   * Build the base CLI parser with common options. Module-specific Mains can extend this with
   * additional options.
   */
  def baseParser[C <: BaseConfig](
    progName: String,
    progVersion: String
  ): OParser[Unit, C] = {
    val builder = OParser.builder[C]
    import builder._

    OParser.sequence(
      programName(progName),
      head(progName, progVersion),
      opt[String]('i', "input")
        .required()
        .valueName("<fileOrDir>")
        .action((x, c) => c.asInstanceOf[BaseConfig].copy(input = x).asInstanceOf[C])
        .text("Path to input file or directory"),
      opt[String]('o', "output")
        .required()
        .valueName("<fileOrDir>")
        .action((x, c) => c.asInstanceOf[BaseConfig].copy(output = x).asInstanceOf[C])
        .text("Path to output file or directory"),
      opt[Boolean]('d', "diff")
        .action((x, c) => c.asInstanceOf[BaseConfig].copy(diffMode = x).asInstanceOf[C])
        .text("enable diff mode to merge with existing output file"),
      opt[Unit]("split")
        .action((_, c) => c.asInstanceOf[BaseConfig].copy(splitMode = true).asInstanceOf[C])
        .text("Enable split mode (file-to-directory split)"),
      opt[String]("strategy")
        .action((x, c) => c.asInstanceOf[BaseConfig].copy(splitStrategy = Some(x)).asInstanceOf[C])
        .text(
          "Split strategy (used with --split): page (PDF), sheet (Excel), slide (PowerPoint), " +
            "attachment (emails), embedded (archives), heading (Word), paragraph, row, column, sentence"
        ),
      opt[String]("type")
        .action((x, c) => c.asInstanceOf[BaseConfig].copy(outputType = Some(x)).asInstanceOf[C])
        .text(
          "Override output MIME type/extension for split chunks - can be MIME type (application/pdf) " +
            "or extension (pdf). Used with --split only."
        ),
      // PDF to image conversion options
      opt[String]("format")
        .action((x, c) => c.asInstanceOf[BaseConfig].copy(outputFormat = Some(x)).asInstanceOf[C])
        .text(
          "Output format for PDF page splitting: pdf (default), png, or jpg"
        ),
      opt[Int]("max-width")
        .action((x, c) => c.asInstanceOf[BaseConfig].copy(maxImageWidth = x).asInstanceOf[C])
        .text("Maximum width in pixels for image output (default: 2000)"),
      opt[Int]("max-height")
        .action((x, c) => c.asInstanceOf[BaseConfig].copy(maxImageHeight = x).asInstanceOf[C])
        .text("Maximum height in pixels for image output (default: 2000)"),
      opt[Long]("max-size")
        .action((x, c) => c.asInstanceOf[BaseConfig].copy(maxImageSizeBytes = x).asInstanceOf[C])
        .text("Maximum size in bytes for image output (default: 5MB)"),
      opt[Int]("dpi")
        .action((x, c) => c.asInstanceOf[BaseConfig].copy(imageDpi = x).asInstanceOf[C])
        .text("DPI for PDF rendering (default: 300)"),
      opt[Double]("quality")
        .action((x, c) => c.asInstanceOf[BaseConfig].copy(jpegQuality = x.toFloat).asInstanceOf[C])
        .text("JPEG quality (0.0-1.0, default: 0.85)"),
      // Recursive extraction options
      opt[Unit]("recursive")
        .action((_, c) =>
          c.asInstanceOf[BaseConfig].copy(recursiveExtraction = true).asInstanceOf[C]
        )
        .text("Enable recursive extraction of archives (ZIP within ZIP)"),
      opt[Int]("max-recursion-depth")
        .action((x, c) => c.asInstanceOf[BaseConfig].copy(maxRecursionDepth = x).asInstanceOf[C])
        .text("Maximum recursion depth for nested archives (default: 5)"),
      opt[Seq[String]]("mapping")
        .valueName("mimeOrExt1=mimeOrExt2,...")
        .action((xs, c) => c.asInstanceOf[BaseConfig].copy(mappings = xs).asInstanceOf[C])
        .text(
          "Either MIME or extension to MIME/extension mapping, e.g. 'pdf=xml' or 'application/vnd.ms-excel=application/json'"
        ),
      opt[String]("failure-mode")
        .action((x, c) => c.asInstanceOf[BaseConfig].copy(failureMode = Some(x)).asInstanceOf[C])
        .text("How to handle splitting failures: throw, preserve (default), drop, or tag"),
      opt[Map[String, String]]("failure-context")
        .valueName("k1=v1,k2=v2...")
        .action((x, c) => c.asInstanceOf[BaseConfig].copy(failureContext = x).asInstanceOf[C])
        .text("Additional context for failure handling (e.g., env=prod,version=1.0)"),
      opt[String]("chunk-range")
        .valueName("<range>")
        .action((x, c) => c.asInstanceOf[BaseConfig].copy(chunkRange = Some(x)).asInstanceOf[C])
        .text("Range of chunks to extract (e.g., 0-9, 5, 10-14,20-24). Zero-based indexing."),
      // Parallel processing options
      opt[Int]("threads")
        .valueName("<N>")
        .action((x, c) => c.asInstanceOf[BaseConfig].copy(threads = x).asInstanceOf[C])
        .text(
          s"Number of parallel threads for directory processing (default: 1 for serial, max: ${Runtime.getRuntime.availableProcessors()})"
        ),
      opt[String]("error-mode")
        .valueName("<mode>")
        .action((x, c) => c.asInstanceOf[BaseConfig].copy(errorMode = Some(x)).asInstanceOf[C])
        .text(
          "Error handling mode for batch processing: fail-fast (stop on first error), continue (default, log and continue), skip (skip failed files)"
        ),
      opt[Unit]("no-progress")
        .action((_, c) => c.asInstanceOf[BaseConfig].copy(enableProgress = false).asInstanceOf[C])
        .text("Disable progress reporting for batch operations"),
      opt[Long]("progress-interval")
        .valueName("<milliseconds>")
        .action((x, c) => c.asInstanceOf[BaseConfig].copy(progressIntervalMs = x).asInstanceOf[C])
        .text("Progress update interval in milliseconds (default: 2000)"),
      opt[Unit]("verbose")
        .action((_, c) => c.asInstanceOf[BaseConfig].copy(verbose = true).asInstanceOf[C])
        .text("Enable verbose logging for individual file operations"),
      // Backend selection
      opt[String]("backend")
        .valueName("<name>")
        .action((x, c) => c.asInstanceOf[BaseConfig].copy(backend = Some(x)).asInstanceOf[C])
        .validate(x =>
          if (Seq("aspose", "libreoffice", "core", "tika").contains(x.toLowerCase))
            success
          else
            failure(s"Invalid backend '$x'. Must be one of: aspose, libreoffice, core, tika")
        )
        .text(
          "Explicitly select backend: aspose (HIGH priority), libreoffice (DEFAULT), core (DEFAULT), tika (LOW)"
        )
    )
  }

  /**
   * Parse mime mappings from string format to Map[MimeType, MimeType]
   */
  def parseMimeMappings(mappings: Seq[String]): Map[MimeType, MimeType] =
    mappings.flatMap { pair =>
      val parts = pair.split("=", 2)
      if (parts.length == 2) {
        val inStr  = parts(0).trim
        val outStr = parts(1).trim

        (parseMimeOrExtension(inStr), parseMimeOrExtension(outStr)) match {
          case (Some(inMime), Some(outMime)) =>
            Some(inMime -> outMime)
          case _ =>
            System.err.println(s"Warning: could not parse mapping '$pair'")
            None
        }
      } else {
        System.err.println(s"Warning: invalid mapping format '$pair'")
        None
      }
    }.toMap

  /**
   * Parse output MIME type from --type and --format options
   */
  def parseOutputMimeType(
    outputType: Option[String],
    outputFormat: Option[String]
  ): Option[MimeType] =
    if (outputType.isDefined) {
      // If explicit type is set, use it
      outputType.flatMap(parseMimeOrExtension)
    } else if (outputFormat.isDefined) {
      // Otherwise, if format is set, use it for backward compatibility
      outputFormat.flatMap { fmt =>
        fmt.toLowerCase match {
          case "png"          => Some(MimeType.ImagePng)
          case "jpg" | "jpeg" => Some(MimeType.ImageJpeg)
          case "pdf"          => Some(MimeType.ApplicationPdf)
          case _              => None
        }
      }
    } else {
      None
    }

  /**
   * Parse chunk range string into a Scala Range. Supports formats:
   *   - "5" - single chunk at index 5
   *   - "0-9" - range from 0 to 9 (inclusive)
   *   - "5-" - from index 5 to end (represented as 5 to Int.MaxValue)
   *
   * For multiple ranges like "0-4,10-14", only the first range is used for now. Future enhancement
   * could return Seq[Range] to support multiple ranges.
   */
  def parseChunkRange(rangeStr: String): Option[Range] =
    rangeStr.trim match {
      case ""                   => None
      case s if s.contains(",") =>
        // For now, just parse the first range
        // TODO: Support multiple ranges in the future
        val firstRange = s.split(",").head.trim
        parseChunkRange(firstRange)
      case s if s.contains("-") =>
        val parts = s.split("-", -1).map(_.trim)
        if (parts.length == 2) {
          try {
            // Handle empty start (e.g., "-5")
            if (parts(0).isEmpty) {
              System.err.println(s"Invalid chunk range format: $s")
              return None
            }

            val start = parts(0).toInt
            val end =
              if (parts(1).isEmpty) Int.MaxValue else parts(1).toInt + 1 // +1 for inclusive end

            // Validate that start < end (allow equal for single-element ranges like "5-5")
            if (end != Int.MaxValue && start > end - 1) {
              System.err.println(s"Invalid chunk range format: $s")
              None
            } else {
              Some(start until end)
            }
          } catch {
            case _: NumberFormatException =>
              System.err.println(s"Invalid chunk range format: $s")
              None
          }
        } else {
          System.err.println(s"Invalid chunk range format: $s")
          None
        }
      case s =>
        // Single number
        try {
          val idx = s.toInt
          Some(idx to idx) // Single element range
        } catch {
          case _: NumberFormatException =>
            System.err.println(s"Invalid chunk range format: $s")
            None
        }
    }
}
