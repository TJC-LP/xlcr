package com.tjclp.xlcr
package utils.aspose

import models.FileContent
import types.{FileType, MimeType}
import utils.{DocChunk, DocumentSplitter, SplitConfig, SplitStrategy}

import com.aspose.zip.{Archive, ArchiveEntry}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

/**
 * ZIP archive splitter using Aspose.ZIP library.
 * Extracts all entries from a ZIP archive file.
 */
object ZipArchiveAsposeSplitter extends DocumentSplitter[MimeType.ApplicationZip.type] {

  override def split(
    content: FileContent[MimeType.ApplicationZip.type],
    cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {

    // If not requesting embedded split, return the original
    if (cfg.strategy != SplitStrategy.Embedded)
      return Seq(DocChunk(content, "zip archive", 0, 1))

    val chunks = ListBuffer.empty[DocChunk[_ <: MimeType]]
    val input = new ByteArrayInputStream(content.data)
    
    try {
      // Use Aspose.ZIP to extract ZIP archive entries
      val zipArchive = new Archive(input)
      val entries = zipArchive.getEntries.asScala
      
      // Process each entry in the ZIP file
      entries.zipWithIndex.foreach { case (entry, index) =>
        if (!entry.isDirectory) {
          // Extract the entry data
          val outputStream = new ByteArrayOutputStream()
          entry.extract(outputStream)
          val entryData = outputStream.toByteArray
          outputStream.close()
          
          // Determine MIME type based on file extension
          val entryName = entry.getName
          val ext = entryName.split("\\.").lastOption.getOrElse("").toLowerCase
          val mime = FileType.fromExtension(ext).map(_.getMimeType).getOrElse(MimeType.ApplicationOctet)
          
          // Create a document chunk for this entry
          chunks += DocChunk(
            FileContent(entryData, mime), 
            entryName, 
            index, 
            0, // Will update total later
            Map(
              "compressed_size" -> entry.getCompressedSize.toString,
              "uncompressed_size" -> entry.getUncompressedSize.toString,
              "archive_type" -> "zip"
            )
          )
        }
      }
      
      zipArchive.close()
    } catch {
      case e: Exception =>
        println(s"Error extracting ZIP archive: ${e.getMessage}")
        e.printStackTrace()
    } finally {
      Try(input.close())
    }
    
    // Finalize chunks with correct total count
    if (chunks.isEmpty)
      return Seq(DocChunk(content, "zip archive", 0, 1))
      
    val total = chunks.size
    chunks.map(c => c.copy(total = total)).toSeq.sortBy(_.index)
  }
}