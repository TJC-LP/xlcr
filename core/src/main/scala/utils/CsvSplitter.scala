package com.tjclp.xlcr
package utils

import models.FileContent
import types.MimeType

/** Splits CSV files on row boundaries while always keeping the header in each
  * chunk. Two main splitting strategies are available:
  * 
  * 1. Character-based chunking (default): Groups rows into chunks based on total
  *    character count, similar to text file chunking. Controlled via \`SplitConfig.maxChars\`,
  *    with a default of 4000 characters if not specified.
  * 
  * 2. Row-based chunking: Creates a separate chunk for each individual row (with header),
  *    activated by setting \`SplitStrategy.Row\`.
  *
  * Each chunk preserves the header and maintains complete rows for context integrity.
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

  /** 
   * Default implementation: groups rows into chunks based on character count, keeping header in each chunk.
   * This implementation:
   * 1. Uses maxChars to determine when to create a new chunk (default: 4000 chars)
   * 2. Always keeps complete rows together (never splits a row across chunks)
   * 3. Includes the header in each chunk for proper context
   * 4. Uses memory-efficient byte array operations for performance
   * 5. Creates descriptive labels showing the row number ranges in each chunk
   */
  private def splitIntoChunks(
      content: FileContent[MimeType.TextCsv.type],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    val charset = java.nio.charset.StandardCharsets.UTF_8
    val csv = new String(content.data, charset)
    val lines = csv.split("\r?\n", -1).toVector
    if (lines.isEmpty) return Seq.empty

    val header = lines.head
    val rows = lines.tail.filter(_.trim.nonEmpty)
    if (rows.isEmpty) return Seq.empty
    
    // Use a reasonable default for max chars per chunk
    val maxCharsPerChunk = if (cfg.maxChars > 0) cfg.maxChars else 4000
    
    // Pre-compute header bytes since they're reused for every chunk
    val headerBytes = header.getBytes(charset)
    val newline = '\n'.toByte
    
    // Process in chunks based on character count
    val builder = Vector.newBuilder[DocChunk[_ <: MimeType]]
    val chunkRowBytes = new java.util.ArrayList[Array[Byte]](32) // Initial capacity of 32 rows
    var currentChunkSize = 0
    var startRow = 0
    var rowIdx = 0
    var chunkIdx = 0
    
    // Add each row while tracking the total byte count
    rows.foreach { row =>
      val rowBytes = row.getBytes(charset)
      val rowSize = rowBytes.length + 1 // +1 for newline
      
      // If this row would put us over the limit, create a chunk and start a new one
      // Always include at least one row per chunk, even if it exceeds maxCharsPerChunk
      if (chunkRowBytes.size() > 0 && currentChunkSize + rowSize > maxCharsPerChunk) {
        // Calculate total chunk size (header + newlines + rows)
        val totalSize = headerBytes.length + chunkRowBytes.size() + 
          chunkRowBytes.stream().mapToInt(_.length).sum()
        
        // Create the combined byte array
        val chunkBytes = new Array[Byte](totalSize)
        var offset = 0
        
        // Copy header
        System.arraycopy(headerBytes, 0, chunkBytes, offset, headerBytes.length)
        offset += headerBytes.length
        
        // Copy each row with a newline separator
        for (i <- 0 until chunkRowBytes.size()) {
          chunkBytes(offset) = newline
          offset += 1
          
          val rowData = chunkRowBytes.get(i)
          System.arraycopy(rowData, 0, chunkBytes, offset, rowData.length)
          offset += rowData.length
        }
        
        val fc = FileContent(chunkBytes, MimeType.TextCsv)
        
        // Create descriptive label (noting the row range)
        val numRows = chunkRowBytes.size()
        val label = s"rows ${startRow+1}-${startRow+numRows}"
        
        builder += DocChunk(fc, label = label, index = chunkIdx, total = -1) // Total will be updated later
        
        // Reset for next chunk
        chunkRowBytes.clear()
        currentChunkSize = 0
        startRow = rowIdx
        chunkIdx += 1
      }
      
      // Add the row to the current chunk
      chunkRowBytes.add(rowBytes)
      currentChunkSize += rowSize
      rowIdx += 1
    }
    
    // Don't forget the last chunk
    if (chunkRowBytes.size() > 0) {
      // Calculate total chunk size (header + newlines + rows)
      val totalSize = headerBytes.length + chunkRowBytes.size() + 
        chunkRowBytes.stream().mapToInt(_.length).sum()
      
      // Create the combined byte array
      val chunkBytes = new Array[Byte](totalSize)
      var offset = 0
      
      // Copy header
      System.arraycopy(headerBytes, 0, chunkBytes, offset, headerBytes.length)
      offset += headerBytes.length
      
      // Copy each row with a newline separator
      for (i <- 0 until chunkRowBytes.size()) {
        chunkBytes(offset) = newline
        offset += 1
        
        val rowData = chunkRowBytes.get(i)
        System.arraycopy(rowData, 0, chunkBytes, offset, rowData.length)
        offset += rowData.length
      }
      
      val fc = FileContent(chunkBytes, MimeType.TextCsv)
      
      // Create descriptive label
      val numRows = chunkRowBytes.size()
      val label = s"rows ${startRow+1}-${startRow+numRows}"
      
      builder += DocChunk(fc, label = label, index = chunkIdx, total = -1)
    }
    
    // Get the result and update the total count
    val result = builder.result()
    val total = result.size
    
    // Update total in each chunk
    result.map(chunk => chunk.copy(total = total))
  }

  /**
   * Splits CSV by individual rows, creating a separate chunk for each row (with header).
   * This implementation:
   * 1. Creates one chunk per non-empty row
   * 2. Prepends the header to each row for proper context
   * 3. Uses memory-efficient byte array operations for performance
   * 4. Labels each chunk with its corresponding row number for easy reference
   * Only activates when SplitStrategy.Row is explicitly specified
   */
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
