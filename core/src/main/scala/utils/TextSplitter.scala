package com.tjclp.xlcr
package utils

import models.FileContent
import types.MimeType

/** Very simple splitter for plain-text documents.  It chunks the UTF-8 text
  * into fixed-sized character windows (\`SplitConfig.maxChars\`) with optional
  * overlap (\`SplitConfig.overlap\`).
  *
  * Strategy enum is ignored – only the limits in SplitConfig are honoured.
  */
class TextSplitter extends DocumentSplitter[MimeType.TextPlain.type] {

  override def split(
      content: FileContent[MimeType.TextPlain.type],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {

    val txt = new String(content.data, java.nio.charset.StandardCharsets.UTF_8)

    val chunkSize  = math.max(1, cfg.maxChars)
    val overlap    = math.max(0, cfg.overlap.min(chunkSize - 1))

    val builder = Seq.newBuilder[DocChunk[_ <: MimeType]]

    var start = 0
    var idx   = 0
    while (start < txt.length) {
      val end = math.min(start + chunkSize, txt.length)
      val slice = txt.substring(start, end)
      val bytes = slice.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      val fc    = FileContent(bytes, MimeType.TextPlain)
      builder += DocChunk(fc, label = s"chunk-${idx + 1}", index = idx, total = 0)
      idx += 1
      start = end - overlap
    }

    // fill total
    val chunks = builder.result()
    val total  = chunks.length
    chunks.map(c => c.copy(total = total))
  }
}
