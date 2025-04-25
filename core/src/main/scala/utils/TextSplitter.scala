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
    // Check if we should use the chunk strategy
    if (cfg.hasStrategy(SplitStrategy.Chunk)) {
      // Split into fixed-size chunks
      splitIntoFixedChunks(content, cfg)
    } else {
      // Default behavior - use paragraph-style splitting with overlaps
      splitWithOverlap(content, cfg)
    }
  }

  /** Splits text using fixed-size chunks with optional overlap */
  private def splitWithOverlap(
      content: FileContent[MimeType.TextPlain.type],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    val txt = new String(content.data, java.nio.charset.StandardCharsets.UTF_8)

    val chunkSize = math.max(1, cfg.maxChars)
    val overlap = math.max(0, cfg.overlap.min(chunkSize - 1))

    val builder = Seq.newBuilder[DocChunk[_ <: MimeType]]

    var start = 0
    var idx = 0
    while (start < txt.length) {
      val end = math.min(start + chunkSize, txt.length)
      val slice = txt.substring(start, end)
      val bytes = slice.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      val fc = FileContent(bytes, MimeType.TextPlain)
      builder += DocChunk(fc, label = s"chars ${start}-${end-1}", index = idx, total = 0)
      idx += 1
      start = end - overlap
    }

    // fill total
    val chunks = builder.result()
    val total = chunks.length
    chunks.map(c => c.copy(total = total))
  }

  /** Splits text using smart chunking around paragraph boundaries when possible */
  private def splitIntoFixedChunks(
      content: FileContent[MimeType.TextPlain.type],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    val txt = new String(content.data, java.nio.charset.StandardCharsets.UTF_8)

    // Split the text into paragraphs
    val paragraphs = txt.split("\n\\s*\n").filter(_.trim.nonEmpty)
    if (paragraphs.isEmpty) return Seq.empty

    val chunkSize = math.max(1, cfg.maxChars)
    val builder = Seq.newBuilder[DocChunk[_ <: MimeType]]
    
    var currentChunk = new StringBuilder()
    var idx = 0
    var startPara = 0 // Track which paragraph we started the current chunk with
    var currentPara = 0 // Current paragraph index
    
    for (paragraph <- paragraphs) {
      // If adding this paragraph would exceed the chunk size and we already have content,
      // finalize the current chunk and start a new one
      if (currentChunk.nonEmpty && currentChunk.length + paragraph.length > chunkSize) {
        val chunkText = currentChunk.toString()
        val bytes = chunkText.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        val fc = FileContent(bytes, MimeType.TextPlain)
        val endPara = currentPara - 1 // Last paragraph in current chunk
        builder += DocChunk(fc, label = s"paragraphs ${startPara+1}-${endPara+1}", index = idx, total = 0)
        idx += 1
        currentChunk = new StringBuilder()
        startPara = currentPara // Start tracking from current paragraph
      }
      
      // If the paragraph itself is larger than chunk size, split it
      if (paragraph.length > chunkSize) {
        var start = 0
        while (start < paragraph.length) {
          val end = math.min(start + chunkSize, paragraph.length)
          val slice = paragraph.substring(start, end)
          val bytes = slice.getBytes(java.nio.charset.StandardCharsets.UTF_8)
          val fc = FileContent(bytes, MimeType.TextPlain)
          builder += DocChunk(fc, label = s"paragraph ${currentPara+1} chars ${start}-${end-1}", index = idx, total = 0)
          idx += 1
          start += chunkSize
        }
      } else {
        // Add the paragraph to the current chunk
        if (currentChunk.nonEmpty) {
          currentChunk.append("\n\n")
        }
        currentChunk.append(paragraph)
      }
      
      currentPara += 1
    }
    
    // Don't forget the last chunk if it's not empty
    if (currentChunk.nonEmpty) {
      val chunkText = currentChunk.toString()
      val bytes = chunkText.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      val fc = FileContent(bytes, MimeType.TextPlain)
      val endPara = currentPara - 1 // Last paragraph processed
      builder += DocChunk(fc, label = s"paragraphs ${startPara+1}-${endPara+1}", index = idx, total = 0)
    }
    
    // fill total
    val chunks = builder.result()
    val total = chunks.length
    chunks.map(c => c.copy(total = total))
  }
}
