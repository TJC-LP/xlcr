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

    val csv = new String(content.data, java.nio.charset.StandardCharsets.UTF_8)
    val lines = csv.split("\r?\n", -1).toVector
    if (lines.isEmpty) return Seq.empty

    val header = lines.head
    val rows   = lines.tail

    val rowsPerChunk = if (cfg.maxChars > 0) cfg.maxChars else 1000

    val grouped = rows.grouped(rowsPerChunk).toVector

    val total = grouped.size
    grouped.zipWithIndex.map { case (chunkRows, idx) =>
      val chunkStr = (header +: chunkRows).mkString("\n")
      val bytes    = chunkStr.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      val fc       = FileContent(bytes, MimeType.TextCsv)
      DocChunk(fc, label = s"rows ${idx * rowsPerChunk}-${idx * rowsPerChunk + chunkRows.size - 1}", index = idx, total = total)
    }
  }
}
