package com.tjclp.xlcr
package processing

import java.util.concurrent.atomic.{ AtomicInteger, AtomicLong }

import org.slf4j.LoggerFactory

/**
 * Trait for reporting progress during batch directory processing operations.
 */
trait ProgressReporter {

  /**
   * Start progress reporting for a batch of files.
   *
   * @param totalFiles
   *   Total number of files to process
   */
  def start(totalFiles: Int): Unit

  /**
   * Report that a file has started processing.
   *
   * @param fileName
   *   Name of the file being processed
   */
  def fileStarted(fileName: String): Unit

  /**
   * Report successful completion of a file.
   *
   * @param fileName
   *   Name of the file that completed
   */
  def fileCompleted(fileName: String): Unit

  /**
   * Report a file processing failure.
   *
   * @param fileName
   *   Name of the file that failed
   * @param error
   *   The error that occurred
   */
  def fileFailed(fileName: String, error: Throwable): Unit

  /**
   * Report that a file was skipped.
   *
   * @param fileName
   *   Name of the file that was skipped
   * @param reason
   *   Reason for skipping
   */
  def fileSkipped(fileName: String, reason: String): Unit

  /**
   * Complete progress reporting and show final summary.
   */
  def complete(): Unit

  /**
   * Stop progress reporting (e.g., due to error).
   */
  def stop(): Unit
}

object ProgressReporter {

  /**
   * A no-op progress reporter that does nothing.
   */
  object NoOp extends ProgressReporter {
    override def start(totalFiles: Int): Unit                                = ()
    override def fileStarted(fileName: String): Unit                         = ()
    override def fileCompleted(fileName: String): Unit                       = ()
    override def fileFailed(fileName: String, error: Throwable): Unit        = ()
    override def fileSkipped(fileName: String, reason: String): Unit         = ()
    override def complete(): Unit                                            = ()
    override def stop(): Unit                                                = ()
  }

  /**
   * Create a console-based progress reporter.
   *
   * @param updateIntervalMs
   *   How often to update progress (in milliseconds)
   * @param verbose
   *   If true, log individual file operations
   */
  def console(updateIntervalMs: Long = 2000, verbose: Boolean = false): ProgressReporter =
    new ConsoleProgressReporter(updateIntervalMs, verbose)
}

/**
 * Console-based progress reporter that shows real-time progress and statistics.
 *
 * @param updateIntervalMs
 *   How often to update progress display (in milliseconds)
 * @param verbose
 *   If true, log individual file operations
 */
class ConsoleProgressReporter(updateIntervalMs: Long, verbose: Boolean) extends ProgressReporter {

  private val logger = LoggerFactory.getLogger(getClass)

  private val totalFiles      = new AtomicInteger(0)
  private val processedFiles  = new AtomicInteger(0)
  private val successfulFiles = new AtomicInteger(0)
  private val failedFiles     = new AtomicInteger(0)
  private val skippedFiles    = new AtomicInteger(0)
  private val startTime       = new AtomicLong(0)
  private val lastUpdateTime  = new AtomicLong(0)

  @volatile private var updateThread: Option[Thread] = None
  @volatile private var running: Boolean             = false

  override def start(totalFiles: Int): Unit = {
    this.totalFiles.set(totalFiles)
    this.startTime.set(System.currentTimeMillis())
    this.lastUpdateTime.set(System.currentTimeMillis())
    this.running = true

    logger.info(s"Starting batch processing of $totalFiles files")

    // Start background update thread
    val thread = new Thread(() => {
      while (running) {
        try {
          Thread.sleep(updateIntervalMs)
          if (running) printProgress()
        } catch {
          case _: InterruptedException => // Thread interrupted, exit
        }
      }
    })
    thread.setDaemon(true)
    thread.setName("progress-reporter")
    thread.start()
    updateThread = Some(thread)
  }

  override def fileStarted(fileName: String): Unit =
    if (verbose) {
      logger.info(s"Processing: $fileName")
    }

  override def fileCompleted(fileName: String): Unit = {
    processedFiles.incrementAndGet()
    successfulFiles.incrementAndGet()
    if (verbose) {
      logger.info(s"Completed: $fileName")
    }
  }

  override def fileFailed(fileName: String, error: Throwable): Unit = {
    processedFiles.incrementAndGet()
    failedFiles.incrementAndGet()
    logger.error(s"Failed: $fileName - ${error.getMessage}")
  }

  override def fileSkipped(fileName: String, reason: String): Unit = {
    processedFiles.incrementAndGet()
    skippedFiles.incrementAndGet()
    if (verbose) {
      logger.info(s"Skipped: $fileName - $reason")
    }
  }

  override def complete(): Unit = {
    running = false
    updateThread.foreach(_.interrupt())
    updateThread = None

    // Print final summary
    printProgress()
    val elapsed = System.currentTimeMillis() - startTime.get()
    logger.info("=" * 80)
    logger.info("Batch processing completed")
    logger.info(
      s"Total files: ${totalFiles.get()}, " +
        s"Successful: ${successfulFiles.get()}, " +
        s"Failed: ${failedFiles.get()}, " +
        s"Skipped: ${skippedFiles.get()}"
    )
    logger.info(s"Total time: ${formatDuration(elapsed)}")
    logger.info("=" * 80)
  }

  override def stop(): Unit = {
    running = false
    updateThread.foreach(_.interrupt())
    updateThread = None
    logger.info("Progress reporting stopped")
  }

  private def printProgress(): Unit = {
    val now        = System.currentTimeMillis()
    val elapsed    = now - startTime.get()
    val processed  = processedFiles.get()
    val total      = totalFiles.get()
    val successful = successfulFiles.get()
    val failed     = failedFiles.get()
    val skipped    = skippedFiles.get()

    if (processed > 0 && elapsed > 0) {
      val rate        = processed.toDouble / (elapsed / 1000.0)
      val remaining   = total - processed
      val etaSeconds  = if (rate > 0) (remaining / rate).toLong else 0
      val percentDone = (processed.toDouble / total * 100).toInt

      logger.info(
        f"Progress: $processed/$total ($percentDone%d%%) | " +
          f"Success: $successful | Failed: $failed | Skipped: $skipped | " +
          f"Rate: $rate%.1f files/sec | ETA: ${formatDuration(etaSeconds * 1000)}"
      )
    }

    lastUpdateTime.set(now)
  }

  private def formatDuration(millis: Long): String = {
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours   = minutes / 60

    if (hours > 0) {
      f"$hours%dh ${minutes % 60}%dm ${seconds % 60}%ds"
    } else if (minutes > 0) {
      f"${minutes}%dm ${seconds % 60}%ds"
    } else {
      f"${seconds}%ds"
    }
  }
}
