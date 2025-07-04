package com.tjclp.xlcr
package splitters.archive

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.Try

import com.aspose.zip.SevenZipArchive

import models.FileContent
import splitters.{ DocChunk, HighPrioritySplitter, SplitConfig, SplitStrategy, SplitFailureHandler, EmptyDocumentException, CorruptedDocumentException }
import types.{ FileType, MimeType }

/**
 * 7-Zip archive splitter using Aspose.ZIP library. Extracts all entries from a 7z archive file.
 * Supports recursive extraction of nested archives with zipbomb protection.
 */
object SevenZipArchiveAsposeSplitter extends HighPrioritySplitter[MimeType.ApplicationSevenz.type] 
    with SplitFailureHandler {

  override protected val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  /**
   * Cleans a path for display by:
   *   1. Removing macOS-specific hidden file prefix "._" 2. Taking only the last path component
   *      (filename) 3. Handling special characters
   */
  private def cleanPathForDisplay(path: String): String = {
    val lastComponent = path.split("/").last
    if (lastComponent.startsWith("._")) lastComponent.substring(2) else lastComponent
  }

  // Track total extracted size for zipbomb protection
  private val extractedSizeMap = new java.util.concurrent.ConcurrentHashMap[
    java.util.UUID,
    java.util.concurrent.atomic.AtomicLong
  ]()

  /**
   * Initialize a tracking session for zipbomb protection
   */
  private def initExtractSession(): java.util.UUID = {
    val sessionId = java.util.UUID.randomUUID()
    extractedSizeMap.put(sessionId, new java.util.concurrent.atomic.AtomicLong(0))
    sessionId
  }

  /**
   * Track the extracted size and check against limits
   * @return
   *   true if extraction can continue, false if limit exceeded
   */
  private def trackExtractedSize(
    sessionId: java.util.UUID,
    byteCount: Long,
    maxSize: Long
  ): Boolean = {
    val counter  = extractedSizeMap.get(sessionId)
    val newTotal = counter.addAndGet(byteCount)

    if (newTotal > maxSize) {
      logger.warn(s"7z extraction aborted: size limit exceeded (${newTotal} > ${maxSize} bytes)")
      false
    } else {
      true
    }
  }

  /**
   * Clean up tracking session
   */
  private def cleanupExtractSession(sessionId: java.util.UUID): Unit =
    extractedSizeMap.remove(sessionId)

  override def split(
    content: FileContent[MimeType.ApplicationSevenz.type],
    cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {

    // Check for valid strategy
    if (!cfg.hasStrategy(SplitStrategy.Embedded)) {
      return handleInvalidStrategy(
        content,
        cfg,
        cfg.strategy.map(_.displayName).getOrElse("none"),
        Seq("embedded")
      )
    }

    // Wrap main logic with failure handling
    withFailureHandling(content, cfg) {
      // Initialize zipbomb protection
      val sessionId = initExtractSession()

      try {
        // Track the initial content
        trackExtractedSize(sessionId, content.data.length, cfg.maxTotalSize)

        // Extract the entries
        val chunks = extractEntries(content, cfg, sessionId)

        // Check if archive is empty
        if (chunks.isEmpty) {
          throw new EmptyDocumentException(
            content.mimeType.toString,
            "7z archive contains no extractable entries"
          )
        }

        val total = chunks.size

        // Reindex chunks to have sequential indices without gaps from skipped metadata files
        chunks.zipWithIndex.map { case (chunk, newIndex) =>
          chunk.copy(index = newIndex, total = total)
        }.toSeq
      } finally
        cleanupExtractSession(sessionId)
    }
  }

  /**
   * Extract 7z archive entries
   */
  private def extractEntries(
    content: FileContent[MimeType.ApplicationSevenz.type],
    cfg: SplitConfig,
    sessionId: java.util.UUID
  ): Seq[DocChunk[_ <: MimeType]] = {

    val chunks = ListBuffer.empty[DocChunk[_ <: MimeType]]
    val input  = new ByteArrayInputStream(content.data)

    try {
      // Use Aspose.ZIP to extract 7z archive entries
      val sevenZipArchive = new SevenZipArchive(input)
      val entries         = sevenZipArchive.getEntries.asScala

      // Calculate total sizes for zipbomb detection
      val totalCompressed   = entries.map(_.getCompressedSize).sum
      val totalUncompressed = entries.map(_.getUncompressedSize).sum

      // If compression ratio > 100, log a warning (potential zipbomb)
      if (totalCompressed > 0 && totalUncompressed / totalCompressed > 100) {
        logger.warn(
          s"Potential 7z bomb detected: compression ratio ${totalUncompressed / totalCompressed}"
        )
        logger.warn(
          s"Archive contains ${entries.size} entries, compressed: ${totalCompressed}, uncompressed: ${totalUncompressed}"
        )
      }

      // Process each entry in the 7z file
      entries.zipWithIndex.foreach { case (entry, index) =>
        val entryName = entry.getName

        // Skip __MACOSX directories and files
        if (entryName.startsWith("__MACOSX") || entryName.contains("/__MACOSX/")) {
          logger.debug(s"Skipping macOS metadata: $entryName")
        }
        // Handle directories - just log them
        else if (entry.isDirectory) {
          logger.debug(s"Directory entry: $entryName")
        } else {
          // Check zipbomb protection limit before extraction
          if (!trackExtractedSize(sessionId, entry.getUncompressedSize, cfg.maxTotalSize)) {
            logger.warn(s"Skipping extraction of '$entryName': size limit exceeded")
            // Early return with partial results
            return chunks.toSeq
          }

          // Extract the entry data
          val outputStream = new ByteArrayOutputStream()
          entry.extract(outputStream)
          val entryData = outputStream.toByteArray
          outputStream.close()

          // Calculate compression ratio for zipbomb detection
          val compressedSize   = entry.getCompressedSize
          val uncompressedSize = entry.getUncompressedSize

          if (compressedSize > 0 && uncompressedSize / compressedSize > 1000) {
            logger.warn(
              s"Suspicious compression ratio for $entryName: ${uncompressedSize / compressedSize}"
            )
          }

          // Determine MIME type based on file extension
          val ext = entryName.split("\\.")
            .lastOption.getOrElse("").toLowerCase

          val mime = FileType.fromExtension(ext)
            .map(_.getMimeType).getOrElse(MimeType.ApplicationOctet)

          // Get clean entry name for display
          val cleanEntryName = cleanPathForDisplay(entryName)

          // Create a document chunk for this entry
          chunks += DocChunk(
            FileContent(entryData, mime),
            cleanEntryName,
            index,
            0, // Will update total later
            Map(
              "compressed_size"   -> compressedSize.toString,
              "uncompressed_size" -> uncompressedSize.toString,
              "archive_type"      -> "7z",
              "path"              -> entryName // Include full path for nested extraction
            )
          )
        }
      }

      sevenZipArchive.close()
    } catch {
      case e: Exception =>
        logger.error(s"Error extracting 7z archive: ${e.getMessage}", e)
        throw new CorruptedDocumentException(
          content.mimeType.toString,
          s"Failed to extract 7z archive: ${e.getMessage}"
        )
    } finally
      Try(input.close())

    chunks.toSeq
  }
}
