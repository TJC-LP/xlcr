package com.tjclp.xlcr

import com.tjclp.xlcr.utils.FileUtils
import com.tjclp.xlcr.types.{FileType, MimeType}
import org.slf4j.LoggerFactory

import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

/**
 * DirectoryPipeline provides simple directory-based conversions
 * using the existing Pipeline for single-file transformations.
 */
object DirectoryPipeline {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Convert each file in inputDir using a dictionary that maps from
   * an input MimeType -> desired output MimeType.
   *
   * Steps:
   * 1) For each file in inputDir, detect the input MIME.
   * 2) Look up the corresponding output MIME in mimeMappings (if not found, skip or log).
   * 3) Figure out the proper file extension for that output MIME type (if we recognize it).
   * 4) Construct the new output filename with that extension in outputDir.
   * 5) Call Pipeline.run(...) for single-file transformation.
   *
   * @param inputDir    path to directory containing input files
   * @param outputDir   path to directory for the converted output
   * @param mimeMappings dictionary from input MIME -> output MIME
   * @param diffMode    whether to merge changes if applicable
   */
  def runDirectoryToDirectory(
                               inputDir: String,
                               outputDir: String,
                               mimeMappings: Map[MimeType, MimeType],
                               diffMode: Boolean = false
                             ): Unit = {
    val inPath = Paths.get(inputDir)
    val outPath = Paths.get(outputDir)

    if (!Files.isDirectory(inPath)) {
      throw new IllegalArgumentException(s"Input directory does not exist or is not a directory: $inputDir")
    }
    if (!Files.exists(outPath)) {
      Files.createDirectories(outPath)
    } else if (!Files.isDirectory(outPath)) {
      throw new IllegalArgumentException(s"Output path is not a directory: $outputDir")
    }

    // Collect the files
    val files = Files.list(inPath)
      .iterator()
      .asScala
      .filter(Files.isRegularFile(_))
      .toList

    // For each file, detect mime, figure out the mapping, and run the pipeline
    files.foreach { file =>
      val inputMime = FileUtils.detectMimeType(file)
      mimeMappings.get(inputMime) match {
        case None =>
          logger.warn(s"No mapping found for MIME type: ${inputMime.mimeType}, skipping file: $file")
        case Some(outputMime) =>
          // We'll figure out the extension from outputMime if possible
          val maybeExt = findExtensionForMime(outputMime)
          val originalName = file.getFileName.toString

          // We'll remove any existing extension from originalName, then add the new extension
          val baseName = {
            val idx = originalName.lastIndexOf(".")
            if (idx >= 0) originalName.substring(0, idx)
            else originalName
          }
          val outExt = maybeExt.getOrElse("dat") // fallback to .dat if unknown
          val outFileName = s"$baseName.$outExt"
          val outFile = outPath.resolve(outFileName)

          logger.info(s"Converting $file (mime: ${inputMime.mimeType}) -> $outFile (mime: ${outputMime.mimeType})")
          Try {
            Pipeline.run(file.toString, outFile.toString, diffMode)
          } match {
            case Failure(ex) =>
              logger.error(s"Failed to convert $file: ${ex.getMessage}")
            case Success(_) =>
              logger.info(s"Successfully converted $file -> $outFile")
          }
      }
    }
  }

  /**
   * A helper to find a known extension from a given MIME type,
   * leveraging our FileType enum if we can.
   */
  private def findExtensionForMime(mime: MimeType): Option[String] = {
    // Attempt to look up in FileType enum
    val maybeFt = FileType.values.find(_.getMimeType == mime)
    maybeFt.map(_.getExtension.extension)
  }

  // Other expansions possible:
  // def runDirectoryToSingleFile(...)
  // def runSingleFileToMultiple(...)
}
