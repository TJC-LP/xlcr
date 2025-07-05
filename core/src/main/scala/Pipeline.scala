package com.tjclp.xlcr

import java.nio.file.{ Files, Paths, StandardCopyOption }

import scala.util.{ Failure, Success, Try }

import org.slf4j.LoggerFactory

import bridges.BridgeRegistry
import models.FileContent
import splitters._
import types.MimeType
import utils.FileUtils

object Pipeline {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Process the input file -> output file conversion pipeline.
   *
   * @param inputPath
   *   Input file path
   * @param outputPath
   *   Output file path
   * @param diffMode
   *   Whether to perform diff operations
   * @param config
   *   Optional bridge-specific configuration
   */
  def run(
    inputPath: String,
    outputPath: String,
    diffMode: Boolean = false,
    config: Option[bridges.BridgeConfig] = None
  ): Unit = {
    logger.info(
      s"Starting extraction process. Input: $inputPath, Output: $outputPath, DiffMode: $diffMode"
    )

    val input  = Paths.get(inputPath)
    val output = Paths.get(outputPath)

    if (!Files.exists(input)) {
      logger.error(s"Input file '$inputPath' does not exist.")
      throw InputFileNotFoundException(inputPath)
    }

    // Detect input and output MIME types
    val inputMimeType = FileUtils.detectMimeType(input)
    val outputMimeType = Try(
      FileUtils.detectMimeTypeFromExtension(output, strict = true)
    ) match {
      case Failure(ex: UnknownExtensionException) =>
        logger.error(ex.getMessage)
        throw ex
      case Failure(other) =>
        logger.error(
          s"Error determining output MIME: ${other.getMessage}",
          other
        )
        throw other
      case Success(m) => m
    }

    // Create a local copy of input
    val localCopy =
      Files.createTempFile("xlcr_localcopy", input.getFileName.toString)
    try {
      Files.copy(input, localCopy, StandardCopyOption.REPLACE_EXISTING)

      val inputBytes  = Files.readAllBytes(localCopy)
      val fileContent = FileContent(inputBytes, inputMimeType)

      // Perform the conversion
      val resultTry =
        if (diffMode && canMergeInPlace(inputMimeType, outputMimeType)) {
          doDiffConversion(fileContent, output, outputMimeType, config)
        } else {
          doRegularConversion(fileContent, outputMimeType, config)
        }

      resultTry match {
        case Failure(exception) =>
          logger.error(
            s"Error extracting content: ${exception.getMessage}",
            exception
          )
          throw ContentExtractionException(
            s"Error extracting content: ${exception.getMessage}",
            exception
          )

        case Success(fileContentOut) =>
          FileUtils.writeBytes(output, fileContentOut.data) match {
            case Success(_) =>
              logger.info(
                s"Content successfully extracted and saved to '$outputPath'."
              )
              logger.info(s"Content type: ${fileContentOut.mimeType.mimeType}")
            case Failure(ex) =>
              throw OutputWriteException(
                s"Failed to write output to '$outputPath'",
                ex
              )
          }
      }
    } finally
      Files.deleteIfExists(localCopy)
  }

  /**
   * Check if we can perform an in-place merge between these mime types
   */
  private def canMergeInPlace(
    inputMime: MimeType,
    outputMime: MimeType
  ): Boolean =
    BridgeRegistry.supportsMerging(inputMime, outputMime)

  /* =====================================================================
   * File‑to‑Directory split entry‑point
   * =================================================================== */

  /**
   * Split a single input document into multiple output files inside the provided directory.
   *
   * @param inputPath
   *   Path to the file that should be split.
   * @param outputDir
   *   Target directory where individual chunks will be saved.
   * @param strategy
   *   Optional user‑supplied split strategy (page, sheet, …).
   * @param outputType
   *   Optional MIME type override for the produced chunks.
   * @param recursive
   *   Whether to recursively extract nested archives.
   * @param maxRecursionDepth
   *   Maximum recursion depth for nested archives.
   * @param maxImageWidth
   *   Maximum width in pixels for image output
   * @param maxImageHeight
   *   Maximum height in pixels for image output
   * @param maxImageSizeBytes
   *   Maximum size in bytes for image output
   * @param imageDpi
   *   DPI for image rendering
   * @param jpegQuality
   *   JPEG quality (0.0-1.0, default: 0.85)
   */
  def split(
    inputPath: String,
    outputDir: String,
    strategy: Option[SplitStrategy] = None,
    outputType: Option[MimeType] = None,
    recursive: Boolean = false,
    maxRecursionDepth: Int = 5,
    maxImageWidth: Int = 2000,
    maxImageHeight: Int = 2000,
    maxImageSizeBytes: Long = 1024 * 1024 * 5,
    imageDpi: Int = 300,
    jpegQuality: Float = 0.85f,
    failureMode: Option[SplitFailureMode] = None,
    failureContext: Map[String, String] = Map.empty,
    chunkRange: Option[Range] = None
  ): Unit = {

    logger.info(
      s"Starting split process. Input file: $inputPath, Output dir: $outputDir, " +
        s"Strategy: ${strategy.map(_.toString).getOrElse("default")}, OverrideType: ${outputType
            .map(_.mimeType).getOrElse("auto")}, " +
        s"Recursive: $recursive, MaxDepth: $maxRecursionDepth}, " +
        s"MaxImageSize: $maxImageSizeBytes bytes, DPI: $imageDpi"
    )

    val inPath = Paths.get(inputPath)
    val outDir = Paths.get(outputDir)

    if (!Files.exists(inPath) || Files.isDirectory(inPath)) {
      val msg = s"Input path must be an existing file: $inputPath"
      logger.error(msg)
      throw InputFileNotFoundException(msg)
    }

    if (!Files.exists(outDir)) Files.createDirectories(outDir)
    else if (!Files.isDirectory(outDir)) {
      val msg =
        s"Output path must be a directory for split operation: $outputDir"
      logger.error(msg)
      throw new IllegalArgumentException(msg)
    }

    // Read file content and detect mime
    val fileContent = models.FileContent.fromPath[MimeType](inPath)

    // Decide on strategy (user override or default)
    val effStrategy: SplitStrategy =
      strategy.getOrElse(SplitConfig.defaultStrategyForMime(fileContent.mimeType))

    // Create split config with recursive flag and image parameters
    val splitCfg = SplitConfig(
      strategy = Some(effStrategy),
      recursive = recursive,
      maxRecursionDepth = maxRecursionDepth,
      maxImageWidth = maxImageWidth,
      maxImageHeight = maxImageHeight,
      maxImageSizeBytes = maxImageSizeBytes,
      imageDpi = imageDpi,
      jpegQuality = jpegQuality,
      failureMode = failureMode.getOrElse(SplitFailureMode.PreserveAsChunk),
      failureContext = failureContext,
      chunkRange = chunkRange
    )

    // Start with current depth = 0
    splitRecursive(fileContent, outDir, splitCfg, outputType, depth = 0)
  }

  /**
   * Recursive implementation of the split operation.
   *
   * @param content
   *   The file content to split
   * @param outputDir
   *   The output directory for chunks
   * @param cfg
   *   Split configuration with recursion settings
   * @param outputType
   *   Optional MIME type override
   * @param depth
   *   Current recursion depth
   * @param pathPrefix
   *   Optional prefix for output filenames (used for nested archives)
   * @return
   *   Number of successfully processed chunks
   */
  private def splitRecursive(
    content: FileContent[MimeType],
    outputDir: java.nio.file.Path,
    cfg: SplitConfig,
    outputType: Option[MimeType],
    depth: Int,
    pathPrefix: Option[String] = None
  ): Int = {

    // Get chunks for the current content
    val chunks =
      try
        DocumentSplitter.split(content, cfg)
      catch {
        case ex: Exception =>
          logger.error(
            s"Failed to split file using strategy ${cfg.strategy}: ${ex.getMessage}"
          )
          throw new RuntimeException(
            s"Failed to split file using strategy ${cfg.strategy}",
            ex
          )
      }

    if (chunks.isEmpty) {
      logger.warn("Split operation produced no chunks.")
      return 0
    } else if (chunks.size <= 1 && depth == 0) {
      logger.warn(
        "Split operation produced only one chunk – file may not be splittable using the chosen strategy."
      )
    }

    var successCount = 0

    // Process each chunk
    chunks.foreach { chunk =>
      val chunkMime: MimeType = outputType.getOrElse(chunk.content.mimeType)
      val ext: String         = findExtensionForMime(chunkMime).getOrElse("dat")

      // Get original path from metadata if available
      val origPath = chunk.attrs.get("path")

      // Create more intelligent filename that preserves structure
      val baseLabel   = sanitizeLabel(chunk.label)
      val indexPadded = f"${chunk.index + 1}%03d"

      val fileName = (pathPrefix, origPath) match {
        // If we have both a prefix and an original path, combine them intelligently
        case (Some(prefix), Some(path)) =>
          val sanitizedPath = sanitizeLabel(path.replace('/', '_'))
          s"${prefix}_${indexPadded}_$sanitizedPath.$ext"
        // Just prefix + index + label
        case (Some(prefix), None) =>
          s"${prefix}_${indexPadded}_$baseLabel.$ext"
        // No prefix but with original path
        case (None, Some(path)) =>
          val sanitizedPath = sanitizeLabel(path.replace('/', '_'))
          s"${indexPadded}_$sanitizedPath.$ext"
        // Basic format with just index + label
        case (None, None) =>
          s"${indexPadded}_$baseLabel.$ext"
      }

      val outPath = outputDir.resolve(fileName)

      // Check if we need to convert the content based on outputType
      val wantedMime = outputType.getOrElse(chunk.content.mimeType)

      // If the chunk MIME type doesn't match what we want, try to convert it
      val finalContent = if (wantedMime != chunk.content.mimeType) {
        // Look for a bridge that can convert from the chunk's MIME type to the wanted MIME type
        logger.info(
          s"Converting chunk from ${chunk.content.mimeType.mimeType} to ${wantedMime.mimeType}"
        )

        // Create appropriate bridge config for image rendering if needed
        val bridgeConfig = (chunk.content.mimeType, wantedMime) match {
          case (MimeType.ApplicationPdf, MimeType.ImagePng | MimeType.ImageJpeg) =>
            // For PDF to image conversion, use ImageRenderConfig
            Some(bridges.image.ImageRenderConfig(
              maxBytes = cfg.maxImageSizeBytes,
              maxWidthPx = cfg.maxImageWidth,
              maxHeightPx = cfg.maxImageHeight,
              initialDpi = cfg.imageDpi,
              initialQuality = cfg.jpegQuality,
              autoTune = cfg.autoTuneImages
            ))
          case _ => None
        }

        // Attempt conversion
        // We need to be careful with the types here, so use a safer approach
        try
          // Use doRegularConversion which handles the type casting internally
          doRegularConversion(chunk.content, wantedMime, bridgeConfig) match {
            case Success(convertedContent) =>
              convertedContent
            case Failure(ex) =>
              logger.warn(s"Failed to convert chunk: ${ex.getMessage}. Using original format.")
              chunk.content
          }
        catch {
          case ex: Exception =>
            logger.warn(
              s"Unexpected error during conversion: ${ex.getMessage}. Using original format."
            )
            chunk.content
        }
      } else {
        // No conversion needed
        chunk.content
      }

      // First write the current chunk with possibly converted content
      utils.FileUtils.writeBytes(outPath, finalContent.data) match {
        case Success(_) =>
          logger.info(s"Wrote chunk #${chunk.index} to $outPath")
          successCount += 1

          // If this is an archive and we're doing recursive extraction, continue processing
          if (
            cfg.recursive && depth < cfg.maxRecursionDepth && isArchiveType(
              chunk.content.mimeType
            )
          ) {

            // Create a subdirectory for nested content
            val subDirName = s"${fileName}_contents"
            val subDir     = outputDir.resolve(subDirName)

            if (!Files.exists(subDir)) {
              try
                Files.createDirectory(subDir)
              catch {
                case ex: Exception =>
                  logger.error(
                    s"Failed to create subdirectory for nested archive: ${ex.getMessage}"
                  )
                  // Return early with current success count
                  return successCount
              }
            }

            // Use same recursion settings for nested content but make sure Embedded strategy is set
            val nestedCfg = SplitConfig(
              strategy = Some(
                SplitStrategy.Embedded
              ), // Always use embedded for nested archives
              recursive =
                true, // Ensure recursive is explicitly true for nested archives
              maxRecursionDepth = cfg.maxRecursionDepth,
              maxTotalSize =
                cfg.maxTotalSize // Preserve zipbomb protection limits
            )

            // Create new prefix for nested files that preserves path information
            val newPrefix = pathPrefix match {
              case Some(prefix) => s"${prefix}_$indexPadded"
              case None         => indexPadded
            }

            // Process the nested archive
            logger.info(
              s"Processing nested archive at depth ${depth + 1}: $outPath"
            )

            // Create a new FileContent with correct MIME type to ensure it's processed as an archive
            // This is important because the chunk's MIME type might not be recognized as an archive
            val archiveContent = if (isArchiveType(chunk.content.mimeType)) {
              chunk.content
            } else {
              // Force archive MIME type if needed (e.g., if incorrectly detected as octet-stream)
              logger.info(
                s"Forcing archive MIME type for nested extraction: ${chunk.content.mimeType} -> ${MimeType.ApplicationZip}"
              )
              FileContent(chunk.content.data, MimeType.ApplicationZip)
            }

            val nestedCount = splitRecursive(
              archiveContent,
              subDir,
              nestedCfg,
              outputType,
              depth + 1,
              Some(newPrefix)
            )

            // If no nested content found, remove the empty directory
            if (nestedCount == 0) {
              try {
                Files.delete(subDir)
                logger.info(s"Removed empty nested directory: $subDir")
              } catch {
                case ex: Exception =>
                  logger.warn(
                    s"Failed to remove empty directory $subDir: ${ex.getMessage}"
                  )
              }
            } else {
              logger.info(s"Processed $nestedCount nested files in $subDir")
              successCount += nestedCount
            }
          }

        case Failure(ex) =>
          logger.error(
            s"Failed to write chunk #${chunk.index} to $outPath: ${ex.getMessage}"
          )
      }
    }

    // Log summary for top-level only
    if (depth == 0) {
      logger.info(
        s"Split operation complete: $successCount total files written to $outputDir"
      )
    }

    successCount
  }

  /**
   * Check if a MIME type represents an archive format that can be recursively extracted
   */
  private def isArchiveType(mime: MimeType): Boolean = mime match {
    case MimeType.ApplicationZip | MimeType.ApplicationGzip |
        MimeType.ApplicationSevenz | MimeType.ApplicationTar |
        MimeType.ApplicationBzip2 | MimeType.ApplicationXz =>
      true
    case _ =>
      // Return true for types that might be archives but weren't detected properly
      // This helps with nested ZIP files that might be incorrectly detected as octet-stream
      val mimeStr = mime.mimeType.toLowerCase
      mimeStr.contains("zip") || mimeStr.contains("compress") ||
      mimeStr.contains("archive") || mimeStr.contains("tar") ||
      // Include Java JAR files, which are ZIPs with a different extension
      mimeStr == "application/java-archive" || mimeStr == "application/x-java-archive" ||
      mimeStr == "application/jar"
  }

  /** Map a MIME type to a known file extension, if possible. */
  private def findExtensionForMime(mime: MimeType): Option[String] =
    types.FileType.values
      .find(_.getMimeType == mime)
      .map(_.getExtension.extension)

  /** Sanitize chunk label to obtain a safe filename component. */
  private def sanitizeLabel(label: String): String = {
    // Handle macOS hidden files by removing the "._" prefix
    val macCleaned = if (label.startsWith("._")) label.substring(2) else label

    // Replace Windows-invalid chars
    val replaced = macCleaned.replaceAll("[\\\\/:*?\"<>|]", "_")

    // Handle directory paths by keeping only the filename part
    val fileNameOnly = replaced.split("/").last

    // Collapse whitespace
    fileNameOnly.trim.replaceAll("\\s+", "_")
  }

  /**
   * Perform a normal single-step conversion via convertDynamic.
   *
   * @param input
   *   The input file content to convert
   * @param outMime
   *   The target MIME type
   * @param config
   *   Optional bridge-specific configuration
   * @return
   *   A Try containing the converted file content or an error
   */
  private def doRegularConversion(
    input: FileContent[MimeType],
    outMime: MimeType,
    config: Option[bridges.BridgeConfig] = None
  ): Try[FileContent[MimeType]] =
    Try {
      BridgeRegistry.findBridge(input.mimeType, outMime) match {
        case Some(bridge) =>
          bridge
            .convert(input, config)
            .asInstanceOf[FileContent[MimeType]]
        case None =>
          throw UnsupportedConversionException(
            input.mimeType.mimeType,
            outMime.mimeType
          )
      }
    }

  /**
   * Perform a diff-based conversion by merging source into existing output. We assume the model is
   * Mergeable, such as SheetsData in JSON -> Excel scenario.
   *
   * @param incoming
   *   The incoming file content to merge
   * @param existingPath
   *   Path to the existing file to merge into
   * @param outputMime
   *   The target MIME type
   * @param config
   *   Optional bridge-specific configuration
   * @return
   *   A Try containing the merged file content or an error
   */
  private def doDiffConversion(
    incoming: FileContent[MimeType],
    existingPath: java.nio.file.Path,
    outputMime: MimeType,
    config: Option[bridges.BridgeConfig] = None
  ): Try[FileContent[MimeType]] = Try {
    if (!Files.exists(existingPath)) {
      throw InputFileNotFoundException(existingPath.toString)
    }

    val existingContent = FileContent.fromPath[MimeType](existingPath)

    BridgeRegistry.findMergeableBridge(incoming.mimeType, outputMime) match {
      case Some(bridge) =>
        bridge
          .convertWithDiff(
            incoming,
            existingContent,
            config
          )
          .asInstanceOf[FileContent[MimeType]]
      case None =>
        throw UnsupportedConversionException(
          incoming.mimeType.mimeType,
          outputMime.mimeType
        )
    }
  }
}
