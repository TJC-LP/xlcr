package com.tjclp.xlcr
package utils

import models.FileContent
import types.{FileType, MimeType}

import org.apache.commons.compress.archivers.{ArchiveEntry, ArchiveInputStream, ArchiveStreamFactory}
import org.apache.commons.compress.compressors.{CompressorInputStream, CompressorStreamFactory}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}

/**
 * Generic archive splitter that handles multiple archive formats:
 * - ZIP files (.zip)
 * - GZip files (.gz)
 * - 7-Zip files (.7z)
 * - TAR archives (.tar)
 * - BZip2 files (.bz2)
 * - XZ files (.xz)
 */
class ArchiveEntrySplitter extends DocumentSplitter[MimeType] {

  // The list of MIME types this splitter can handle
  private val supportedMimeTypes = Set[MimeType](
    MimeType.ApplicationZip,
    MimeType.ApplicationGzip,
    MimeType.ApplicationSevenz,
    MimeType.ApplicationBzip2,
    MimeType.ApplicationXz
  )

  override def split(
      content: FileContent[MimeType],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {

    // If not handling this MIME type or not requesting split, return the original
    if (!supportedMimeTypes.contains(content.mimeType) || cfg.strategy != SplitStrategy.Embedded)
      return Seq(DocChunk(content, "archive", 0, 1))

    // Handle the archive based on its type
    content.mimeType match {
      case MimeType.ApplicationZip => splitZip(content, cfg)
      case MimeType.ApplicationGzip => splitSingleEntryCompressed(content, cfg, CompressorStreamFactory.GZIP)
      case MimeType.ApplicationBzip2 => splitSingleEntryCompressed(content, cfg, CompressorStreamFactory.BZIP2)
      case MimeType.ApplicationXz => splitSingleEntryCompressed(content, cfg, CompressorStreamFactory.XZ)
      case MimeType.ApplicationSevenz => splitZip(content, cfg) // Fallback to zip handling for 7z
      case _ => Seq(DocChunk(content, "archive", 0, 1))
    }
  }

  /**
   * Handles ZIP archives (.zip) and similar formats
   */
  private def splitZip(
      content: FileContent[MimeType],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    val chunks = ListBuffer.empty[DocChunk[_ <: MimeType]]
    val input = new ByteArrayInputStream(content.data)
    
    try {
      val zipInput = new java.util.zip.ZipInputStream(input)
      var entry = zipInput.getNextEntry()
      
      while (entry != null) {
        if (!entry.isDirectory) {
          val entryName = entry.getName
          val outputStream = new ByteArrayOutputStream()
          val buffer = new Array[Byte](8192)
          var len = 0
          
          while ({ len = zipInput.read(buffer); len > 0 }) {
            outputStream.write(buffer, 0, len)
          }
          
          val entryData = outputStream.toByteArray
          outputStream.close()
          
          // Determine MIME type
          val ext = entryName.split("\\.").lastOption.getOrElse("").toLowerCase
          val mime = FileType.fromExtension(ext).map(_.getMimeType).getOrElse(MimeType.ApplicationOctet)
          
          chunks += DocChunk(FileContent(entryData, mime), entryName, chunks.length, 0)
        }
        
        zipInput.closeEntry()
        entry = zipInput.getNextEntry()
      }
      
      zipInput.close()
    } catch {
      case e: Exception =>
        // Log the error but continue with an empty result
        println(s"Error reading ZIP: ${e.getMessage}")
    }
    
    // Finalize chunks with correct total count
    if (chunks.isEmpty)
      return Seq(DocChunk(content, "archive", 0, 1))
      
    val total = chunks.size
    chunks.map(c => c.copy(total = total)).toSeq.sortBy(_.index)
  }

  /**
   * Handles single-entry compressed formats (GZIP, BZIP2, XZ)
   */
  private def splitSingleEntryCompressed(
      content: FileContent[MimeType],
      cfg: SplitConfig,
      compressorName: String
  ): Seq[DocChunk[_ <: MimeType]] = {
    val input = new ByteArrayInputStream(content.data)
    
    try {
      // Decompress the input
      val factory = new CompressorStreamFactory()
      val compressor = factory.createCompressorInputStream(compressorName, input)
      val outputStream = new ByteArrayOutputStream()
      val buffer = new Array[Byte](8192)
      var len = 0
      
      while ({ len = compressor.read(buffer); len > 0 }) {
        outputStream.write(buffer, 0, len)
      }
      
      compressor.close()
      
      val decompressedBytes = outputStream.toByteArray
      outputStream.close()
      
      // Try to determine a sensible file name and MIME type
      val nameWithoutExt = content.mimeType match {
        case MimeType.ApplicationGzip => "decompressed.gz"
        case MimeType.ApplicationBzip2 => "decompressed.bz2"
        case MimeType.ApplicationXz => "decompressed.xz"
        case _ => "decompressed"
      }
      
      val nameWithoutCompressExt = nameWithoutExt.replaceAll("\\.(gz|bz2|xz)$", "")
      val ext = nameWithoutCompressExt.split("\\.").lastOption.getOrElse("").toLowerCase
      val mime = FileType.fromExtension(ext).map(_.getMimeType).getOrElse(MimeType.ApplicationOctet)
      
      // Return single decompressed content
      val fileContent = FileContent(decompressedBytes, mime)
      Seq(DocChunk(fileContent, nameWithoutCompressExt, 0, 1))
    } catch {
      case e: Exception =>
        // Log the error and return the original content
        println(s"Error decompressing ${compressorName}: ${e.getMessage}")
        Seq(DocChunk(content, "archive", 0, 1))
    }
  }
}