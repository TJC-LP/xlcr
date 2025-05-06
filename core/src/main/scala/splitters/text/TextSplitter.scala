package com.tjclp.xlcr
package splitters
package text

import models.FileContent
import types.MimeType.TextPlain

/**
 * Splitter for plain-text documents that supports two modes:
 *
 *   1. Paragraph-aware chunking (default): Intelligently splits text into chunks respecting
 *      paragraph boundaries when possible, with descriptive labels showing paragraph ranges. This
 *      is the default for text files.
 *
 * 2. Character-based chunking with overlap: Splits text into fixed-size character windows
 * (\`SplitConfig.maxChars\`) with optional overlap (\`SplitConfig.overlap\`). Only used when
 * explicitly requested.
 */
object TextSplitter extends DocumentSplitter[TextPlain.type] {

  override def split(
    content: FileContent[TextPlain.type],
    cfg: SplitConfig
  ): Seq[DocChunk[TextPlain.type]] =
    // Note: Chunk is the default strategy for text files (set in DocumentSplitter),
    // but we check if another strategy was explicitly requested
    if (
      cfg.strategy
        .exists(s => s != SplitStrategy.Chunk && s != SplitStrategy.Auto)
    ) {
      // Use character-based splitting with overlap for other strategies
      splitByCharactersWithOverlap(content, cfg)
    } else {
      // Default behavior (Chunk strategy) - use paragraph-aware splitting
      splitByParagraphs(content, cfg)
    }

  /**
   * Splits text into fixed-size character windows with optional overlap. This approach is simpler
   * but doesn't respect paragraph boundaries.
   */
  private def splitByCharactersWithOverlap(
    content: FileContent[TextPlain.type],
    cfg: SplitConfig
  ): Seq[DocChunk[TextPlain.type]] = {
    val txt = new String(content.data, java.nio.charset.StandardCharsets.UTF_8)

    val chunkSize = math.max(1, cfg.maxChars)
    val overlap   = math.max(0, cfg.overlap.min(chunkSize - 1))

    val builder = Seq.newBuilder[DocChunk[TextPlain.type]]

    var start = 0
    var idx   = 0
    while (start < txt.length) {
      val end   = math.min(start + chunkSize, txt.length)
      val slice = txt.substring(start, end)
      val bytes = slice.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      val fc    = FileContent(bytes, TextPlain)
      builder += DocChunk(
        fc,
        label = s"chars ${start}-${end - 1}",
        index = idx,
        total = 0
      )
      idx += 1
      start = end - overlap
    }

    // fill total
    val chunks = builder.result()
    val total  = chunks.length
    chunks.map(c => c.copy(total = total))
  }

  /**
   * Splits text intelligently respecting paragraph boundaries when possible. This is the default
   * approach for text files as it produces more coherent chunks.
   */
  private def splitByParagraphs(
    content: FileContent[TextPlain.type],
    cfg: SplitConfig
  ): Seq[DocChunk[TextPlain.type]] = {
    val txt = new String(content.data, java.nio.charset.StandardCharsets.UTF_8)

    // Split the text into paragraphs
    val paragraphs = txt.split("\n\\s*\n").filter(_.trim.nonEmpty)
    if (paragraphs.isEmpty) return Seq.empty

    val chunkSize = math.max(1, cfg.maxChars)
    val builder   = Seq.newBuilder[DocChunk[TextPlain.type]]

    var currentChunk = new StringBuilder()
    var idx          = 0
    var startPara    = 0 // Track which paragraph we started the current chunk with
    var currentPara  = 0 // Current paragraph index

    for (paragraph <- paragraphs) {
      // If adding this paragraph would exceed the chunk size and we already have content,
      // finalize the current chunk and start a new one
      if (currentChunk.nonEmpty && currentChunk.length + paragraph.length > chunkSize) {
        val chunkText = currentChunk.toString()
        val bytes     = chunkText.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        val fc        = FileContent(bytes, TextPlain)
        val endPara   = currentPara - 1 // Last paragraph in current chunk

        // Calculate character bounds for this chunk
        val charStart   = txt.indexOf(paragraphs(startPara))
        val lastParaEnd = paragraphs(endPara)
        val charEnd     = txt.indexOf(lastParaEnd) + lastParaEnd.length - 1

        builder += DocChunk(
          fc,
          label =
            s"paragraphs ${startPara + 1}-${endPara + 1} (chars $charStart-$charEnd)",
          index = idx,
          total = 0
        )
        idx += 1
        currentChunk = new StringBuilder()
        startPara = currentPara // Start tracking from current paragraph
      }

      // If the paragraph itself is larger than chunk size, split it
      if (paragraph.length > chunkSize) {
        var start = 0
        while (start < paragraph.length) {
          val end   = math.min(start + chunkSize, paragraph.length)
          val slice = paragraph.substring(start, end)
          val bytes = slice.getBytes(java.nio.charset.StandardCharsets.UTF_8)
          val fc    = FileContent(bytes, TextPlain)

          // Calculate the absolute character position in the original text
          val paraStart     = txt.indexOf(paragraph)
          val absoluteStart = paraStart + start
          val absoluteEnd   = paraStart + end - 1

          builder += DocChunk(
            fc,
            label =
              s"paragraph ${currentPara + 1} section ${start}-${end - 1} (chars $absoluteStart-$absoluteEnd)",
            index = idx,
            total = 0
          )
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
      val bytes     = chunkText.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      val fc        = FileContent(bytes, TextPlain)
      val endPara   = currentPara - 1 // Last paragraph processed

      // Calculate character bounds for this chunk
      val charStart   = txt.indexOf(paragraphs(startPara))
      val lastParaEnd = paragraphs(endPara)
      val charEnd     = txt.indexOf(lastParaEnd) + lastParaEnd.length - 1

      builder += DocChunk(
        fc,
        label =
          s"paragraphs ${startPara + 1}-${endPara + 1} (chars $charStart-$charEnd)",
        index = idx,
        total = 0
      )
    }

    // fill total
    val chunks = builder.result()
    val total  = chunks.length
    chunks.map(c => c.copy(total = total))
  }
}
