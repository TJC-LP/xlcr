package com.tjclp.xlcr
package utils

import models.FileContent
import types.{FileType, MimeType}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.zip.{ZipEntry, ZipInputStream}

import scala.collection.mutable.ListBuffer

/**
 * Splits a ZIP archive into its constituent files.
 */
class ZipEntrySplitter extends DocumentSplitter[MimeType.ApplicationZip.type] {

  override def split(
      content: FileContent[MimeType.ApplicationZip.type],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {

    // If not requesting entry-level split, return the whole archive
    if (!cfg.hasStrategy(SplitStrategy.Embedded))
      return Seq(DocChunk(content, "archive", 0, 1))

    val chunks = ListBuffer.empty[DocChunk[_ <: MimeType]]
    val zipInputStream = new ZipInputStream(new ByteArrayInputStream(content.data))
    
    try {
      // Process each entry in the ZIP file
      var entry: ZipEntry = zipInputStream.getNextEntry()
      while (entry != null) {
        // Skip directories
        if (!entry.isDirectory) {
          // Read the ZIP entry content
          val baos = new ByteArrayOutputStream()
          val buffer = new Array[Byte](8192)
          var len = 0
          while ({ len = zipInputStream.read(buffer); len > 0 }) {
            baos.write(buffer, 0, len)
          }
          
          // Determine the MIME type based on the file extension
          val entryName = entry.getName
          val ext = entryName.split("\\.").lastOption.getOrElse("").toLowerCase
          val mime = FileType.fromExtension(ext).map(_.getMimeType).getOrElse(MimeType.ApplicationOctet)
          
          // Create a chunk for this entry
          val fileContent = FileContent(baos.toByteArray, mime)
          chunks += DocChunk(
            fileContent, 
            entryName, 
            chunks.length, 
            0,
            Map(
              "path" -> entryName // Store original path for nested structure preservation
            )
          )
        }
        
        // Close the current entry and move to the next
        zipInputStream.closeEntry()
        entry = zipInputStream.getNextEntry()
      }
    } finally {
      zipInputStream.close()
    }

    // If no valid entries were found, return the original archive
    if (chunks.isEmpty)
      return Seq(DocChunk(content, "archive", 0, 1))

    // Update total count and return
    val total = chunks.size
    chunks.map(c => c.copy(total = total)).toSeq.sortBy(_.index)
  }
}