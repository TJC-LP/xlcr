package com.tjclp.xlcr

import java.nio.file.{ Files, Path, Paths }
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ Executors, ThreadFactory, TimeUnit }

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.jdk.CollectionConverters._
import scala.util.{ Failure, Success, Try }

import org.slf4j.LoggerFactory

import processing.{ ErrorMode, ProgressReporter }
import types.MimeType
import utils.FileUtils

/**
 * Parallel directory-to-directory pipeline for batch document processing. Processes files in
 * parallel using a configurable thread pool with progress tracking and flexible error handling.
 */
object ParallelDirectoryPipeline {

  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Result of processing a single file.
   */
  sealed trait ProcessingResult {
    def fileName: String
  }

  case class ProcessingSuccess(fileName: String, inputMime: String, outputMime: String)
      extends ProcessingResult
  case class ProcessingFailure(fileName: String, error: Throwable) extends ProcessingResult
  case class ProcessingSkipped(fileName: String, reason: String)   extends ProcessingResult

  /**
   * Summary of batch processing results.
   */
  case class BatchSummary(
    totalFiles: Int,
    successfulFiles: Int,
    failedFiles: Int,
    skippedFiles: Int,
    durationMs: Long,
    results: Seq[ProcessingResult]
  ) {

    def filesPerSecond: Double =
      if (durationMs > 0) totalFiles.toDouble / (durationMs / 1000.0)
      else 0.0

    def successRate: Double =
      if (totalFiles > 0) successfulFiles.toDouble / totalFiles * 100.0
      else 0.0
  }

  /**
   * Configuration for parallel directory processing.
   *
   * @param threads
   *   Number of parallel threads to use
   * @param errorMode
   *   How to handle errors during processing
   * @param progressReporter
   *   Reporter for tracking progress
   * @param timeout
   *   Maximum time to wait for all files to process
   * @param contextWrapper
   *   Optional function to wrap each task execution (e.g., for ThreadLocal propagation). Receives a
   *   block to execute and returns its result.
   */
  case class Config(
    threads: Int = Runtime.getRuntime.availableProcessors(),
    errorMode: ErrorMode = ErrorMode.default,
    progressReporter: ProgressReporter = ProgressReporter.NoOp,
    timeout: Duration = Duration.Inf,
    contextWrapper: Option[(() => ProcessingResult) => ProcessingResult] = None
  )

  /**
   * Process files in parallel from inputDir to outputDir using the provided MIME type mappings.
   *
   * @param inputDir
   *   Directory containing input files
   * @param outputDir
   *   Directory for output files
   * @param mimeMappings
   *   Map from input MIME type to output MIME type
   * @param config
   *   Processing configuration (threads, error mode, progress reporting)
   * @param diffMode
   *   Whether to merge changes into existing files
   * @return
   *   Summary of processing results
   */
  def runParallel(
    inputDir: String,
    outputDir: String,
    mimeMappings: Map[MimeType, MimeType],
    config: Config = Config(),
    diffMode: Boolean = false,
    backendPreference: Option[String] = None
  ): BatchSummary = {

    val inPath  = Paths.get(inputDir)
    val outPath = Paths.get(outputDir)

    // Validate input directory
    if (!Files.isDirectory(inPath)) {
      throw new IllegalArgumentException(
        s"Input directory does not exist or is not a directory: $inputDir"
      )
    }

    // Create output directory if needed
    if (!Files.exists(outPath)) {
      Files.createDirectories(outPath)
    } else if (!Files.isDirectory(outPath)) {
      throw new IllegalArgumentException(s"Output path is not a directory: $outputDir")
    }

    // Collect files to process
    val files = Files
      .list(inPath)
      .iterator()
      .asScala
      .filter(Files.isRegularFile(_))
      .toList

    if (files.isEmpty) {
      logger.warn(s"No files found in input directory: $inputDir")
      return BatchSummary(
        totalFiles = 0,
        successfulFiles = 0,
        failedFiles = 0,
        skippedFiles = 0,
        durationMs = 0,
        results = Seq.empty
      )
    }

    logger.info(
      s"Starting parallel processing of ${files.size} files with ${config.threads} threads"
    )

    // Create thread pool with named threads for better debugging
    val threadFactory = new ThreadFactory {
      private val counter = new AtomicInteger(0)
      override def newThread(r: Runnable): Thread = {
        val thread = new Thread(r, s"parallel-pipeline-${counter.incrementAndGet()}")
        thread.setDaemon(false)
        thread
      }
    }

    val executor                      = Executors.newFixedThreadPool(config.threads, threadFactory)
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(executor)

    val startTime = System.currentTimeMillis()

    // Start progress reporting
    config.progressReporter.start(files.size)

    try {
      // Create futures for each file
      val futures = files.map { file =>
        Future {
          // Wrap execution with context wrapper if provided (e.g., for ThreadLocal propagation)
          config.contextWrapper match {
            case Some(wrapper) =>
              wrapper(() =>
                processFile(file, outPath, mimeMappings, diffMode, config.progressReporter, backendPreference)
              )
            case None =>
              processFile(file, outPath, mimeMappings, diffMode, config.progressReporter, backendPreference)
          }
        }
      }

      // Handle error mode
      val allResults = config.errorMode match {
        case ErrorMode.FailFast =>
          // In fail-fast mode, we want to stop on first error
          processWithFailFast(futures, config.timeout)

        case ErrorMode.ContinueOnError | ErrorMode.SkipOnError =>
          // In continue/skip mode, wait for all to complete
          val results = Await.result(Future.sequence(futures), config.timeout)
          results
      }

      val endTime  = System.currentTimeMillis()
      val duration = endTime - startTime

      // Categorize results
      val successful = allResults.count(_.isInstanceOf[ProcessingSuccess])
      val failed     = allResults.count(_.isInstanceOf[ProcessingFailure])
      val skipped    = allResults.count(_.isInstanceOf[ProcessingSkipped])

      val summary = BatchSummary(
        totalFiles = files.size,
        successfulFiles = successful,
        failedFiles = failed,
        skippedFiles = skipped,
        durationMs = duration,
        results = allResults
      )

      // Complete progress reporting
      config.progressReporter.complete()

      summary

    } catch {
      case ex: Exception =>
        logger.error(s"Error during parallel processing: ${ex.getMessage}", ex)
        config.progressReporter.stop()
        throw ex
    } finally {
      // Shutdown executor
      executor.shutdown()
      try
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
          logger.warn("Executor did not terminate in time, forcing shutdown")
          executor.shutdownNow()
        }
      catch {
        case _: InterruptedException =>
          executor.shutdownNow()
          Thread.currentThread().interrupt()
      }
    }
  }

  /**
   * Process with fail-fast behavior - stop on first error.
   */
  private def processWithFailFast(
    futures: List[Future[ProcessingResult]],
    timeout: Duration
  )(implicit ec: ExecutionContext): Seq[ProcessingResult] = {
    import scala.concurrent.Promise

    val promise = Promise[Seq[ProcessingResult]]()

    // Create a future that completes when any future fails
    Future
      .sequence(futures)
      .onComplete {
        case Success(results) =>
          // All succeeded
          promise.trySuccess(results)
        case Failure(ex) =>
          // At least one failed
          promise.tryFailure(ex)
      }

    Await.result(promise.future, timeout)
  }

  /**
   * Process a single file.
   */
  private def processFile(
    file: Path,
    outputDir: Path,
    mimeMappings: Map[MimeType, MimeType],
    diffMode: Boolean,
    progressReporter: ProgressReporter,
    backendPreference: Option[String] = None
  ): ProcessingResult = {

    val fileName = file.getFileName.toString

    try {
      progressReporter.fileStarted(fileName)

      // Detect input MIME type
      val inputMime = FileUtils.detectMimeType(file)

      // Look up output MIME type
      mimeMappings.get(inputMime) match {
        case None =>
          val reason = s"No mapping found for MIME type: ${inputMime.mimeType}"
          progressReporter.fileSkipped(fileName, reason)
          ProcessingSkipped(fileName, reason)

        case Some(outputMime) =>
          // Determine output file name
          val outputFile = determineOutputFile(file, outputDir, outputMime)

          // Run the conversion using Pipeline
          Try {
            Pipeline.run(
              inputPath = file.toString,
              outputPath = outputFile.toString,
              diffMode = diffMode,
              backendPreference = backendPreference
            )
          } match {
            case Success(_) =>
              progressReporter.fileCompleted(fileName)
              ProcessingSuccess(fileName, inputMime.mimeType, outputMime.mimeType)

            case Failure(ex) =>
              progressReporter.fileFailed(fileName, ex)
              ProcessingFailure(fileName, ex)
          }
      }
    } catch {
      case ex: Throwable =>
        progressReporter.fileFailed(fileName, ex)
        ProcessingFailure(fileName, ex)
    }
  }

  /**
   * Determine the output file path based on input file and output MIME type.
   */
  private def determineOutputFile(
    inputFile: Path,
    outputDir: Path,
    outputMime: MimeType
  ): Path = {
    val originalName = inputFile.getFileName.toString

    // Remove existing extension
    val baseName = {
      val idx = originalName.lastIndexOf(".")
      if (idx >= 0) originalName.substring(0, idx)
      else originalName
    }

    // Find extension for output MIME type
    val extension      = findExtensionForMime(outputMime).getOrElse("dat")
    val outputFileName = s"$baseName.$extension"

    outputDir.resolve(outputFileName)
  }

  /**
   * Find file extension for a MIME type.
   */
  private def findExtensionForMime(mime: MimeType): Option[String] =
    types.FileType.values
      .find(_.getMimeType == mime)
      .map(_.getExtension.extension)
}
