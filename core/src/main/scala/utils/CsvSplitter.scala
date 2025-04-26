package com.tjclp.xlcr
package utils

import models.FileContent
import types.MimeType

/** Splits CSV files on row boundaries while always keeping the header in each
  * chunk.  Chunk size is controlled via \`SplitConfig.maxChars\`, interpreted
  * here as **maximum data rows** (not counting header) per chunk.  If
  * \`maxChars\` is <= 0 we default to 1 000 rows.
  */
class CsvSplitter extends DocumentSplitter[MimeType.TextCsv.type] {

  override def split(
      content: FileContent[MimeType.TextCsv.type],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    // Check if the strategy is explicitly set to Row
    if (cfg.hasStrategy(SplitStrategy.Row)) {
      // Split by individual rows
      splitByRows(content, cfg)
    } else {
      // Default behavior - group rows into chunks
      splitIntoChunks(content, cfg)
    }
  }

  /** Default implementation: groups rows into chunks of specified size, keeping header in each chunk */
  private def splitIntoChunks(
      content: FileContent[MimeType.TextCsv.type],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    val csv = new String(content.data, java.nio.charset.StandardCharsets.UTF_8)
    val lines = csv.split("\r?\n", -1).toVector
    if (lines.isEmpty) return Seq.empty

    val header = lines.head
    val rows = lines.tail
    
    // Use a reasonable default for rows per chunk
    val rowsPerChunk = if (cfg.maxChars > 0) cfg.maxChars else 1000
    
    // Precompute the total number
    val total = (rows.size + rowsPerChunk - 1) / rowsPerChunk
    
    // Process in chunks to avoid collecting all intermediate groups in memory
    val builder = Vector.newBuilder[DocChunk[_ <: MimeType]]
    
    for (i <- 0 until total) {
      val startIdx = i * rowsPerChunk
      val endIdx = math.min(startIdx + rowsPerChunk, rows.size)
      val chunkRows = rows.slice(startIdx, endIdx)
      
      // Generate chunk with header
      val chunkStr = (header +: chunkRows).mkString("\n")
      val bytes = chunkStr.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      val fc = FileContent(bytes, MimeType.TextCsv)
      
      // Create descriptive label
      val label = s"rows ${startIdx+1}-${endIdx}"
      
      builder += DocChunk(fc, label = label, index = i, total = total)
    }
    
    builder.result()
  }

  /** Splits CSV by individual rows, creating a separate chunk for each row (with header) */
  private def splitByRows(
      content: FileContent[MimeType.TextCsv.type],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    val csv = new String(content.data, java.nio.charset.StandardCharsets.UTF_8)
    val lines = csv.split("\r?\n", -1).toVector
    if (lines.isEmpty) return Seq.empty

    val header = lines.head
    val rows = lines.tail
    
    // Track original row numbers while filtering empty rows in a single pass
    val nonEmptyRowsWithInfo = rows.zipWithIndex
      .filter { case (row, _) => row.trim.nonEmpty }
      
    // Pre-allocate the header bytes since they're reused for every chunk
    val headerBytes = header.getBytes(java.nio.charset.StandardCharsets.UTF_8)
    val newline = '\n'.toByte
    
    val total = nonEmptyRowsWithInfo.size
    nonEmptyRowsWithInfo.zipWithIndex.map { 
      case ((row, originalIdx), idx) =>
        // Compute row number (add 2 for header and 0-index)
        val originalRowNum = originalIdx + 2
        
        // Efficient byte array creation without string concatenation
        val rowBytes = row.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        val bytes = new Array[Byte](headerBytes.length + 1 + rowBytes.length)
        
        // Copy header + newline + row bytes directly
        System.arraycopy(headerBytes, 0, bytes, 0, headerBytes.length)
        bytes(headerBytes.length) = newline
        System.arraycopy(rowBytes, 0, bytes, headerBytes.length + 1, rowBytes.length)
        
        val fc = FileContent(bytes, MimeType.TextCsv)
        DocChunk(fc, label = s"row $originalRowNum", index = idx, total = total)
    }
  }
}
