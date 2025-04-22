package com.tjclp.xlcr
package utils.aspose

import models.FileContent
import types.{FileType, MimeType}
import utils.{DocChunk, DocumentSplitter, SplitConfig, SplitStrategy}

import com.aspose.zip.{Archive, ArchiveEntry}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

/**
 * ZIP archive splitter using Aspose.ZIP library.
 * Extracts all entries from a ZIP archive file.
 * Supports recursive extraction of nested archives with zipbomb protection.
 *
 * This is the Scala 2.12 version.
 */
object ZipArchiveAsposeSplitter extends DocumentSplitter[MimeType.ApplicationZip.type] {
  
  private val logger = org.slf4j.LoggerFactory.getLogger(getClass)
  
  /**
   * Cleans a path for display by:
   * 1. Removing macOS-specific hidden file prefix "._"
   * 2. Taking only the last path component (filename)
   * 3. Handling special characters
   */
  private def cleanPathForDisplay(path: String): String = {
    val lastComponent = path.split("/").last
    if (lastComponent.startsWith("._")) lastComponent.substring(2) else lastComponent
  }
  
  // Track total extracted size for zipbomb protection
  private val extractedSizeMap = new java.util.concurrent.ConcurrentHashMap[java.util.UUID, java.util.concurrent.atomic.AtomicLong]()
  
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
   * @return true if extraction can continue, false if limit exceeded
   */
  private def trackExtractedSize(sessionId: java.util.UUID, byteCount: Long, maxSize: Long): Boolean = {
    val counter = extractedSizeMap.get(sessionId)
    val newTotal = counter.addAndGet(byteCount)
    
    if (newTotal > maxSize) {
      logger.warn(s"ZIP extraction aborted: size limit exceeded (${newTotal} > ${maxSize} bytes)")
      false
    } else {
      true
    }
  }
  
  /**
   * Clean up tracking session
   */
  private def cleanupExtractSession(sessionId: java.util.UUID): Unit = {
    extractedSizeMap.remove(sessionId)
  }

  override def split(
    content: FileContent[MimeType.ApplicationZip.type],
    cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    
    // If not requesting embedded split, return the original
    if (cfg.strategy != SplitStrategy.Embedded)
      return Seq(DocChunk(content, "zip archive", 0, 1))
      
    // Initialize zipbomb protection
    val sessionId = initExtractSession()
    
    try {
      // Track the initial content
      trackExtractedSize(sessionId, content.data.length, cfg.maxTotalSize)
    
      // Extract the entries
      val chunks = extractEntries(content, cfg, sessionId)
      
      // Finalize chunks with correct total count
      if (chunks.isEmpty)
        return Seq(DocChunk(content, "zip archive", 0, 1))
        
      val total = chunks.size
      
      // Reindex chunks to have sequential indices without gaps from skipped metadata files
      chunks.zipWithIndex.map { case (chunk, newIndex) => 
        chunk.copy(index = newIndex, total = total)
      }.toSeq
    } finally {
      cleanupExtractSession(sessionId)
    }
  }
  
  /**
   * Extract ZIP archive entries
   */
  private def extractEntries(
    content: FileContent[MimeType.ApplicationZip.type],
    cfg: SplitConfig,
    sessionId: java.util.UUID
  ): Seq[DocChunk[_ <: MimeType]] = {
    
    val chunks = ListBuffer.empty[DocChunk[_ <: MimeType]]
    val input = new ByteArrayInputStream(content.data)
    
    try {
      // Use Aspose.ZIP to extract ZIP archive entries
      val zipArchive = new Archive(input)
      val entries = zipArchive.getEntries.asScala
      
      // Check for potential zipbomb by calculating compression ratio
      val totalCompressed = entries.map(_.getCompressedSize).sum
      val totalUncompressed = entries.map(_.getUncompressedSize).sum
      
      // If compression ratio > 100, log a warning (potential zipbomb)
      if (totalCompressed > 0 && totalUncompressed / totalCompressed > 100) {
        logger.warn(s"Potential ZIP bomb detected: compression ratio ${totalUncompressed / totalCompressed}")
        logger.warn(s"Archive contains ${entries.size} entries, compressed: ${totalCompressed}, uncompressed: ${totalUncompressed}")
      }
      
      // Process each entry in the ZIP file
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
          val compressedSize = entry.getCompressedSize
          val uncompressedSize = entry.getUncompressedSize
          
          if (compressedSize > 0 && uncompressedSize / compressedSize > 1000) {
            logger.warn(s"Suspicious compression ratio for $entryName: ${uncompressedSize / compressedSize}")
          }
          
          // Determine MIME type based on file extension
          val ext = entryName.split("\\.").lastOption.getOrElse("").toLowerCase
          val mime = FileType.fromExtension(ext).map(_.getMimeType).getOrElse(MimeType.ApplicationOctet)
          
          // Get clean entry name for display
          val cleanEntryName = cleanPathForDisplay(entryName)
          
          // Create a document chunk for this entry
          chunks += DocChunk(
            FileContent(entryData, mime), 
            cleanEntryName, 
            index, 
            0, // Will update total later
            Map(
              "compressed_size" -> compressedSize.toString,
              "uncompressed_size" -> uncompressedSize.toString,
              "archive_type" -> "zip",
              "path" -> entryName // Include full path for nested extraction
            )
          )
        }
      }
      
      zipArchive.close()
    } catch {
      case e: Exception =>
        logger.error(s"Error extracting ZIP archive: ${e.getMessage}", e)
    } finally {
      Try(input.close())
    }
    
    chunks.toSeq
  }
}