package com.tjclp.xlcr

import types.{FileType, MimeType}
import bridges.BridgeRegistry

import scopt.OParser

import java.nio.file.{Files, Paths}

object Main {
  def main(args: Array[String]): Unit = {
    case class ExtendedConfig(
                               input: String = "",
                               output: String = "",
                               diffMode: Boolean = false,
                               splitMode: Boolean = false,
                               splitStrategy: Option[String] = None,
                               outputType: Option[String] = None,
                               mappings: Seq[String] = Seq.empty // strings like "xlsx=json" or "application/pdf=application/xml"
                             )

    val registry = BridgeRegistry
    registry.init()
    val builder = OParser.builder[ExtendedConfig]
    val parser = {
      import builder._
      OParser.sequence(
        programName("xlcr"),
        head("xlcr", "1.0"),
        opt[String]('i', "input")
          .required()
          .valueName("<fileOrDir>")
          .action((x, c) => c.copy(input = x))
          .text("Path to input file or directory"),
        opt[String]('o', "output")
          .required()
          .valueName("<fileOrDir>")
          .action((x, c) => c.copy(output = x))
          .text("Path to output file or directory"),
        opt[Boolean]('d', "diff")
          .action((x, c) => c.copy(diffMode = x))
          .text("enable diff mode to merge with existing output file"),

        opt[Unit]("split")
          .action((_, c) => c.copy(splitMode = true))
          .text("Enable split mode (file-to-directory split)"),

        opt[String]("strategy")
          .action((x, c) => c.copy(splitStrategy = Some(x)))
          .text("Split strategy: sheet, page, slide, attachment, embedded, etc. (optional, split mode only)"),

        opt[String]("type")
          .action((x, c) => c.copy(outputType = Some(x)))
          .text("Override output MIME type/extension for split chunks (optional, split mode only)"),
        opt[Seq[String]]("mapping")
          .valueName("mimeOrExt1=mimeOrExt2,...")
          .action((xs, c) => c.copy(mappings = xs))
          .text("Either MIME or extension to MIME/extension mapping, e.g. 'pdf=xml' or 'application/vnd.ms-excel=application/json'")
      )
    }

    OParser.parse(parser, args, ExtendedConfig()) match {
      case Some(config) =>
        // Parse the mapping strings into a Map[MimeType, MimeType]
        val mimeMap: Map[MimeType, MimeType] = config.mappings.flatMap { pair =>
          val parts = pair.split("=", 2)
          if (parts.length == 2) {
            val inStr = parts(0).trim
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

        val inputPath = Paths.get(config.input)
        val outputPath = Paths.get(config.output)

        // Branch logic based on split vs convert modes
        if (config.splitMode) {
          // Split mode: expect input to be a single file and output to be a directory
          if (Files.isDirectory(inputPath)) {
            System.err.println("Split mode expects --input to be a file, not a directory.")
            sys.exit(1)
          }

          // Ensure output directory exists or can be created
          if (!Files.exists(outputPath)) {
            try Files.createDirectories(outputPath)
            catch {
              case ex: Exception =>
                System.err.println(s"Failed to create output directory: ${ex.getMessage}")
                sys.exit(1)
            }
          } else if (!Files.isDirectory(outputPath)) {
            System.err.println("--output must be a directory when using --split mode.")
            sys.exit(1)
          }

          // Parse optional strategy and output type
          val splitStrategyOpt = config.splitStrategy.flatMap(utils.SplitStrategy.fromString)
          val outputMimeOpt = config.outputType.flatMap(parseMimeOrExtension)

          Pipeline.split(
            inputPath = config.input,
            outputDir = config.output,
            strategy = splitStrategyOpt,
            outputType = outputMimeOpt
          )

        } else {
          // Conversion mode (existing behavior)
          // Check if input path is a directory
          if (Files.isDirectory(inputPath)) {
            // Directory-based approach
            if (mimeMap.nonEmpty) {
              DirectoryPipeline.runDirectoryToDirectory(
                inputDir = config.input,
                outputDir = config.output,
                mimeMappings = mimeMap,
                diffMode = config.diffMode
              )
            } else {
              System.err.println("No mime mappings provided for directory-based operation.")
              sys.exit(1)
            }
          } else {
            // Single file conversion
            Pipeline.run(config.input, config.output, config.diffMode)
          }
        }

      case _ =>
        // arguments are bad, error message will have been displayed
        sys.exit(1)
    }
  }

  /**
   * Attempt to interpret a string as either a known MIME type or a known file extension.
   */
  private def parseMimeOrExtension(str: String): Option[MimeType] = {
    // First try direct MIME type parse
    MimeType.fromString(str) match {
      case someMime@Some(_) => someMime
      case None =>
        // Attempt to interpret it as an extension
        // remove any leading dot, e.g. ".xlsx" -> "xlsx"
        val ext = if (str.startsWith(".")) str.substring(1).toLowerCase else str.toLowerCase

        // See if there's a matching FileType
        val maybeFt = FileType.fromExtension(ext)
        maybeFt.map(_.getMimeType)
    }
  }
}
