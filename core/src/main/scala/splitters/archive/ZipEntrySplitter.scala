package com.tjclp.xlcr
package splitters
package archive

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import java.util.zip.{ ZipEntry, ZipInputStream }

import scala.collection.mutable.ListBuffer

import org.slf4j.LoggerFactory

import models.FileContent
import types.{ FileType, MimeType }
import utils.PathFilter

/**
 * Splits a ZIP archive into its constituent files.
 *
 * Features:
 *   - Extracts files from ZIP archives
 *   - Filters out macOS metadata files and directories
 *   - Determines appropriate MIME types for extracted files
 *   - Preserves original file paths in metadata
 */
object ZipEntrySplitter extends DocumentSplitter[MimeType.ApplicationZip.type]
    with SplitFailureHandler {
  override protected val logger = LoggerFactory.getLogger(getClass)

  override def split(
    content: FileContent[MimeType.ApplicationZip.type],
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
      val chunks = ListBuffer.empty[DocChunk[_ <: MimeType]]
      val zipInputStream = new ZipInputStream(
        new ByteArrayInputStream(content.data)
      )

      try {
        // Process each entry in the ZIP file
        var entry: ZipEntry = zipInputStream.getNextEntry()
        while (entry != null) {
          val entryName = entry.getName

          // Skip directories and macOS metadata files
          if (!entry.isDirectory && !PathFilter.isMacOsMetadata(entryName)) {
            logger.debug(s"Processing ZIP entry: $entryName")

            // Read the ZIP entry content
            val baos   = new ByteArrayOutputStream()
            val buffer = new Array[Byte](8192)
            var len    = 0
            while ({ len = zipInputStream.read(buffer); len > 0 })
              baos.write(buffer, 0, len)

            // Determine the MIME type based on the file extension
            val ext = entryName.split("\\.").lastOption.getOrElse("").toLowerCase
            val mime = FileType
              .fromExtension(ext)
              .map(_.getMimeType)
              .getOrElse(MimeType.ApplicationOctet)

            // Get clean entry name for display
            val displayName = PathFilter.cleanPathForDisplay(entryName)

            // Create a chunk for this entry
            val fileContent = FileContent(baos.toByteArray, mime)
            chunks += DocChunk(
              fileContent,
              displayName,
              chunks.length,
              0,
              Map(
                "path" -> entryName, // Store original path for nested structure preservation
                "size"            -> baos.size().toString, // Store size information
                "compressed_size" -> entry.getCompressedSize.toString
              )
            )
          } else if (PathFilter.isMacOsMetadata(entryName)) {
            logger.debug(s"Skipping macOS metadata: $entryName")
          }

          // Close the current entry and move to the next
          zipInputStream.closeEntry()
          entry = zipInputStream.getNextEntry()
        }
      } finally
        zipInputStream.close()

      // If no valid entries were found, throw an exception
      if (chunks.isEmpty) {
        throw new EmptyDocumentException(
          content.mimeType.mimeType,
          "ZIP archive contains no valid entries"
        )
      }

      // Update total count and return
      val total     = chunks.size
      val allChunks = chunks.map(c => c.copy(total = total)).toSeq.sortBy(_.index)

      // Apply chunk range filtering if specified
      cfg.chunkRange match {
        case Some(range) =>
          range.filter(i => i >= 0 && i < total).map(allChunks(_)).toSeq
        case None =>
          allChunks
      }
    }
  }
}
