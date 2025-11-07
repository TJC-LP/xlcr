package com.tjclp.xlcr

import java.nio.file.{ Files, Paths }

import scala.jdk.CollectionConverters._
import scala.util.{ Failure, Success, Try }

import com.tjclp.xlcr.processing.{ ErrorMode, ProgressReporter }
import com.tjclp.xlcr.types.{ FileType, MimeType }
import com.tjclp.xlcr.utils.FileUtils

import org.slf4j.LoggerFactory

/**
 * DirectoryPipeline provides simple directory-based conversions using the existing Pipeline for
 * single-file transformations.
 */
object DirectoryPipeline {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Convert each file in inputDir using a dictionary that maps from an input MimeType -> desired
   * output MimeType.
   *
   * Steps: 1) For each file in inputDir, detect the input MIME. 2) Look up the corresponding output
   * MIME in mimeMappings (if not found, skip or log). 3) Figure out the proper file extension for
   * that output MIME type (if we recognize it). 4) Construct the new output filename with that
   * extension in outputDir. 5) Call Pipeline.run(...) for single-file transformation.
   *
   * When threads > 1, uses parallel processing with the specified configuration.
   *
   * @param inputDir
   *   path to directory containing input files
   * @param outputDir
   *   path to directory for the converted output
   * @param mimeMappings
   *   dictionary from input MIME -> output MIME
   * @param diffMode
   *   whether to merge changes if applicable
   * @param threads
   *   number of parallel threads (1 = serial processing)
   * @param errorMode
   *   error handling strategy (FailFast, ContinueOnError, SkipOnError)
   * @param enableProgress
   *   whether to show progress reporting
   * @param progressIntervalMs
   *   progress update interval in milliseconds
   * @param verbose
   *   enable verbose logging for individual file operations
   * @param contextWrapper
   *   optional function to wrap each task execution (e.g., for ThreadLocal propagation)
   */
  def runDirectoryToDirectory(
    inputDir: String,
    outputDir: String,
    mimeMappings: Map[MimeType, MimeType],
    diffMode: Boolean = false,
    threads: Int = 1,
    errorMode: Option[ErrorMode] = None,
    enableProgress: Boolean = true,
    progressIntervalMs: Long = 2000,
    verbose: Boolean = false,
    contextWrapper: Option[(
      () => ParallelDirectoryPipeline.ProcessingResult
    ) => ParallelDirectoryPipeline.ProcessingResult] = None
  ): Unit = {
    // Route to parallel or serial implementation based on threads setting
    if (threads > 1) {
      // Use parallel processing
      logger.info(s"Using parallel processing with $threads threads")

      // Create progress reporter
      val progressReporter =
        if (enableProgress) {
          ProgressReporter.console(progressIntervalMs, verbose)
        } else {
          ProgressReporter.NoOp
        }

      // Parse error mode
      val effectiveErrorMode = errorMode.getOrElse(ErrorMode.default)

      // Create parallel config
      val parallelConfig = ParallelDirectoryPipeline.Config(
        threads = threads,
        errorMode = effectiveErrorMode,
        progressReporter = progressReporter,
        contextWrapper = contextWrapper
      )

      // Run parallel processing
      val summary = ParallelDirectoryPipeline.runParallel(
        inputDir = inputDir,
        outputDir = outputDir,
        mimeMappings = mimeMappings,
        config = parallelConfig,
        diffMode = diffMode
      )

      // Log summary
      logger.info(
        s"Batch processing completed: ${summary.successfulFiles}/${summary.totalFiles} successful, " +
          s"${summary.failedFiles} failed, ${summary.skippedFiles} skipped in ${summary.durationMs}ms " +
          s"(${summary.filesPerSecond} files/sec, ${summary.successRate}% success rate)"
      )

      // Log failed files if any
      if (summary.failedFiles > 0) {
        logger.warn("Failed files:")
        summary.results.collect {
          case ParallelDirectoryPipeline.ProcessingFailure(fileName, error) =>
            logger.warn(s"  - $fileName: ${error.getMessage}")
        }
      }

    } else {
      // Use serial processing (original implementation)
      logger.info("Using serial processing")

      val inPath  = Paths.get(inputDir)
      val outPath = Paths.get(outputDir)

      if (!Files.isDirectory(inPath)) {
        throw new IllegalArgumentException(
          s"Input directory does not exist or is not a directory: $inputDir"
        )
      }
      if (!Files.exists(outPath)) {
        Files.createDirectories(outPath)
      } else if (!Files.isDirectory(outPath)) {
        throw new IllegalArgumentException(s"Output path is not a directory: $outputDir")
      }

      // Collect the files
      val files = Files
        .list(inPath)
        .iterator()
        .asScala
        .filter(Files.isRegularFile(_))
        .toList

      // For each file, detect mime, figure out the mapping, and run the pipeline
      files.foreach { file =>
        val inputMime = FileUtils.detectMimeType(file)
        mimeMappings.get(inputMime) match {
          case None =>
            logger.warn(
              s"No mapping found for MIME type: ${inputMime.mimeType}, skipping file: $file"
            )
          case Some(outputMime) =>
            // We'll figure out the extension from outputMime if possible
            val maybeExt     = findExtensionForMime(outputMime)
            val originalName = file.getFileName.toString

            // We'll remove any existing extension from originalName, then add the new extension
            val baseName = {
              val idx = originalName.lastIndexOf(".")
              if (idx >= 0) originalName.substring(0, idx)
              else originalName
            }
            val outExt      = maybeExt.getOrElse("dat") // fallback to .dat if unknown
            val outFileName = s"$baseName.$outExt"
            val outFile     = outPath.resolve(outFileName)

            logger.info(
              s"Converting $file (mime: ${inputMime.mimeType}) -> $outFile (mime: ${outputMime.mimeType})"
            )
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
  }

  /**
   * A helper to find a known extension from a given MIME type, leveraging our FileType enum if we
   * can.
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
