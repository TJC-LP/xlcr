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

    val rowsPerChunk = if (cfg.maxChars > 0) cfg.maxChars else 1000

    val grouped = rows.grouped(rowsPerChunk).toVector

    val total = grouped.size
    grouped.zipWithIndex.map { case (chunkRows, idx) =>
      val chunkStr = (header +: chunkRows).mkString("\n")
      val bytes = chunkStr.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      val fc = FileContent(bytes, MimeType.TextCsv)
      DocChunk(fc, label = s"rows ${idx * rowsPerChunk}-${idx * rowsPerChunk + chunkRows.size - 1}", index = idx, total = total)
    }
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
    
    // Skip empty rows
    val nonEmptyRows = rows.filter(_.trim.nonEmpty)
    
    val total = nonEmptyRows.size
    nonEmptyRows.zipWithIndex.map { case (row, idx) =>
      // Get original row number (add 2 because we're 0-indexed and need to skip header)
      val originalRowNum = rows.indexOf(row) + 2
      val chunkStr = s"$header\n$row"
      val bytes = chunkStr.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      val fc = FileContent(bytes, MimeType.TextCsv)
      DocChunk(fc, label = s"row $originalRowNum", index = idx, total = total)
    }
  }
}
