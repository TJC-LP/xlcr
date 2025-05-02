package com.tjclp.xlcr
package splitters
package archive

import models.FileContent
import types.Priority.LOW
import types.{FileType, MimeType, Priority}
import utils.PathFilter

import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.collection.mutable.ListBuffer

/** Generic archive splitter that handles multiple archive formats:
  * - ZIP files (.zip)
  * - GZip files (.gz)
  * - 7-Zip files (.7z)
  * - TAR archives (.tar)
  * - BZip2 files (.bz2)
  * - XZ files (.xz)
  *
  * Supports recursive extraction of archives within archives.
  * Includes zipbomb protection to prevent excessive resource usage.
  */
class ArchiveEntrySplitter extends DocumentSplitter[MimeType] {
  override def priority: Priority = LOW // TODO: Make recursion work properly

  private val logger = LoggerFactory.getLogger(getClass)

  /** Cleans a path for display by delegating to PathFilter utility
    */
  private def cleanPathForDisplay(path: String): String = {
    PathFilter.cleanPathForDisplay(path)
  }

  // The list of MIME types this splitter can handle
  private val supportedMimeTypes = Set[MimeType](
    MimeType.ApplicationZip,
    MimeType.ApplicationGzip,
    MimeType.ApplicationSevenz,
    MimeType.ApplicationBzip2,
    MimeType.ApplicationXz
  )

  // Track total extracted size for zipbomb protection
  private val extractedSizeMap = new java.util.concurrent.ConcurrentHashMap[
    java.util.UUID,
    java.util.concurrent.atomic.AtomicLong
  ]()

  /** Initialize a tracking session for zipbomb protection
    */
  private def initExtractSession(): java.util.UUID = {
    val sessionId = java.util.UUID.randomUUID()
    extractedSizeMap.put(
      sessionId,
      new java.util.concurrent.atomic.AtomicLong(0)
    )
    sessionId
  }

  /** Track the extracted size and check against limits
    * @return true if extraction can continue, false if limit exceeded
    */
  private def trackExtractedSize(
      sessionId: java.util.UUID,
      byteCount: Long,
      maxSize: Long
  ): Boolean = {
    val counter = extractedSizeMap.get(sessionId)
    val newTotal = counter.addAndGet(byteCount)

    if (newTotal > maxSize) {
      logger.warn(
        s"Archive extraction aborted: size limit exceeded (${newTotal} > ${maxSize} bytes)"
      )
      false
    } else {
      true
    }
  }

  /** Clean up tracking session
    */
  private def cleanupExtractSession(sessionId: java.util.UUID): Unit = {
    extractedSizeMap.remove(sessionId)
  }

  override def split(
      content: FileContent[MimeType],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {

    // If not handling this MIME type or not requesting split, return the original
    if (
      !supportedMimeTypes
        .contains(content.mimeType) || !cfg.hasStrategy(SplitStrategy.Embedded)
    )
      return Seq(DocChunk(content, "archive", 0, 1))

    // Initialize zipbomb protection
    val sessionId = initExtractSession()

    try {
      // Track the initial content size
      trackExtractedSize(sessionId, content.data.length, cfg.maxTotalSize)

      // Handle the archive based on its type
      val chunks = content.mimeType match {
        case MimeType.ApplicationZip => splitZip(content, cfg, sessionId)
        case MimeType.ApplicationGzip =>
          splitSingleEntryCompressed(
            content,
            cfg,
            CompressorStreamFactory.GZIP,
            sessionId
          )
        case MimeType.ApplicationBzip2 =>
          splitSingleEntryCompressed(
            content,
            cfg,
            CompressorStreamFactory.BZIP2,
            sessionId
          )
        case MimeType.ApplicationXz =>
          splitSingleEntryCompressed(
            content,
            cfg,
            CompressorStreamFactory.XZ,
            sessionId
          )
        case MimeType.ApplicationSevenz =>
          splitZip(content, cfg, sessionId) // Fallback to zip handling for 7z
        case _ => Seq(DocChunk(content, "archive", 0, 1))
      }

      chunks
    } finally {
      cleanupExtractSession(sessionId)
    }
  }

  /** Handles ZIP archives (.zip) and similar formats
    */
  private def splitZip(
      content: FileContent[MimeType],
      cfg: SplitConfig,
      sessionId: java.util.UUID
  ): Seq[DocChunk[_ <: MimeType]] = {
    // Import break functionality
    import scala.util.control.Breaks._

    val chunks = ListBuffer.empty[DocChunk[_ <: MimeType]]
    val input = new ByteArrayInputStream(content.data)

    try {
      val zipInput = new java.util.zip.ZipInputStream(input)
      var entry = zipInput.getNextEntry()

      // Estimate total uncompressed size for zipbomb protection
      var entriesProcessed = 0
      var totalUncompressedSize = 0L
      var potentialCompressedSize = 0L

      while (entry != null) {
        breakable {
          val entryName = entry.getName

          // Skip macOS metadata files and directories
          if (PathFilter.isMacOsMetadata(entryName)) {
            logger.debug(s"Skipping macOS metadata: $entryName")
            zipInput.closeEntry()
            entry = zipInput.getNextEntry()
            break()
          } else if (!entry.isDirectory) {
            val size = entry.getSize

            if (size > 0) {
              // If size is reported correctly, track it
              val wouldExceedLimit =
                !trackExtractedSize(sessionId, size, cfg.maxTotalSize)
              if (wouldExceedLimit) {
                logger.warn(
                  s"Skipping ZIP entry ${entry.getName}: would exceed size limit"
                )
                zipInput.closeEntry()
                entry = zipInput.getNextEntry()
                // Skip to next entry
                scala.util.control.Breaks.break()
              }
            } else {
              // Size unknown, we'll track during extraction
              potentialCompressedSize += entry.getCompressedSize
            }

            // Create output stream to extract the data
            val outputStream = new ByteArrayOutputStream()
            val buffer = new Array[Byte](8192)
            var len = 0
            var bytesRead = 0L

            val maxEntrySize = 1024 * 1024 * 50 // 50MB per entry max

            while ({ len = zipInput.read(buffer); len > 0 }) {
              bytesRead += len

              // Check for oversized entries
              if (bytesRead > maxEntrySize) {
                logger.warn(
                  s"ZIP entry ${entryName} exceeded max size (${bytesRead} > ${maxEntrySize})"
                )
                scala.util.control.Breaks.break()
              }

              outputStream.write(buffer, 0, len)
            }

            val entryData = outputStream.toByteArray
            outputStream.close()

            // Update tracking for actual size
            if (
              size <= 0 && !trackExtractedSize(
                sessionId,
                entryData.length,
                cfg.maxTotalSize
              )
            ) {
              logger.warn(s"ZIP entry ${entryName} exceeded total size limit")
              zipInput.closeEntry()
              entry = zipInput.getNextEntry()
              // Skip to next iteration
              scala.util.control.Breaks.break()
            }

            totalUncompressedSize += entryData.length
            entriesProcessed += 1

            // Check compression ratio (zipbomb protection)
            val compressedSize = entry.getCompressedSize
            if (
              compressedSize > 0 && entryData.length / compressedSize > 1000
            ) {
              logger.warn(
                s"Suspicious compression ratio for ${entryName}: ${entryData.length / compressedSize}"
              )
            }

            // Determine MIME type
            val ext =
              entryName.split("\\.").lastOption.getOrElse("").toLowerCase
            val mime = FileType
              .fromExtension(ext)
              .map(_.getMimeType)
              .getOrElse(MimeType.ApplicationOctet)

            // Get clean entry name (remove path except last component for display)
            val cleanEntryName = cleanPathForDisplay(entryName)

            // Create document chunk
            chunks += DocChunk(
              FileContent(entryData, mime),
              cleanEntryName,
              chunks.length,
              0,
              Map(
                "compressed_size" -> entry.getCompressedSize.toString,
                "uncompressed_size" -> entryData.length.toString,
                "archive_type" -> "zip",
                "path" -> entryName // Keep original path in metadata
              )
            )
          }
        }

        zipInput.closeEntry()
        entry = zipInput.getNextEntry()
      }

      zipInput.close()

      // Check overall compression ratio
      if (
        entriesProcessed > 0 && potentialCompressedSize > 0 && totalUncompressedSize / potentialCompressedSize > 100
      ) {
        logger.warn(
          s"Potential ZIP bomb detected: compression ratio ${totalUncompressedSize / potentialCompressedSize}"
        )
        logger.warn(
          s"Archive contains ${entriesProcessed} entries, uncompressed: ${totalUncompressedSize}"
        )
      }
    } catch {
      case e: Exception =>
        // Log the error but continue with an empty result
        logger.error(s"Error reading ZIP: ${e.getMessage}", e)
    }

    // Finalize chunks with correct total count
    if (chunks.isEmpty)
      return Seq(DocChunk(content, "archive", 0, 1))

    val total = chunks.size

    // Reindex chunks to have sequential indices without gaps from skipped metadata files
    chunks.zipWithIndex.map { case (chunk, newIndex) =>
      chunk.copy(index = newIndex, total = total)
    }.toSeq
  }

  /** Handles single-entry compressed formats (GZIP, BZIP2, XZ)
    */
  private def splitSingleEntryCompressed(
      content: FileContent[MimeType],
      cfg: SplitConfig,
      compressorName: String,
      sessionId: java.util.UUID
  ): Seq[DocChunk[_ <: MimeType]] = {
    // Import break functionality
    import scala.util.control.Breaks._

    val input = new ByteArrayInputStream(content.data)

    try {
      // Decompress the input
      val factory = new CompressorStreamFactory()
      val compressor =
        factory.createCompressorInputStream(compressorName, input)
      val outputStream = new ByteArrayOutputStream()
      val buffer = new Array[Byte](8192)
      var len = 0
      var totalBytesRead = 0L

      // Compressed formats can expand dramatically - set reasonable limits
      val maxDecompressedSize = 1024 * 1024 * 100 // 100MB

      breakable {
        while ({ len = compressor.read(buffer); len > 0 }) {
          totalBytesRead += len

          // Check for zipbomb
          if (totalBytesRead > maxDecompressedSize) {
            logger.warn(
              s"Compressed file exceeded max size limit (${totalBytesRead} > ${maxDecompressedSize})"
            )
            scala.util.control.Breaks.break()
          }

          if (!trackExtractedSize(sessionId, len, cfg.maxTotalSize)) {
            logger.warn(s"Decompression aborted: size limit exceeded")
            scala.util.control.Breaks.break()
          }

          outputStream.write(buffer, 0, len)
        }
      }

      compressor.close()

      val decompressedBytes = outputStream.toByteArray
      outputStream.close()

      // Check compression ratio (zipbomb protection)
      val compressionRatio =
        decompressedBytes.length.toDouble / content.data.length
      if (compressionRatio > 1000) {
        logger.warn(s"Suspicious compression ratio: ${compressionRatio}")
      }

      // Try to determine a sensible file name and MIME type
      val nameWithoutExt = content.mimeType match {
        case MimeType.ApplicationGzip  => "decompressed.gz"
        case MimeType.ApplicationBzip2 => "decompressed.bz2"
        case MimeType.ApplicationXz    => "decompressed.xz"
        case _                         => "decompressed"
      }

      val nameWithoutCompressExt =
        nameWithoutExt.replaceAll("\\.(gz|bz2|xz)$", "")
      val ext =
        nameWithoutCompressExt.split("\\.").lastOption.getOrElse("").toLowerCase
      val mime = FileType
        .fromExtension(ext)
        .map(_.getMimeType)
        .getOrElse(MimeType.ApplicationOctet)

      // Return single decompressed content
      val fileContent = FileContent(decompressedBytes, mime)
      Seq(
        DocChunk(
          fileContent,
          nameWithoutCompressExt,
          0,
          1,
          Map(
            "compressed_size" -> content.data.length.toString,
            "uncompressed_size" -> decompressedBytes.length.toString,
            "archive_type" -> compressorName.toLowerCase
          )
        )
      )
    } catch {
      case e: Exception =>
        // Log the error and return the original content
        logger.error(
          s"Error decompressing ${compressorName}: ${e.getMessage}",
          e
        )
        Seq(DocChunk(content, "archive", 0, 1))
    }
  }
}
