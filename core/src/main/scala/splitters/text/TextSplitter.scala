package com.tjclp.xlcr
package splitters
package text

import models.FileContent
import types.MimeType.TextPlain

import com.tjclp.xlcr.splitters.SplitStrategy.Paragraph

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
        .exists(s => s == SplitStrategy.Chunk || s == SplitStrategy.Auto)
    ) {
      // Use character-based splitting with overlap for other strategies
      splitByCharactersWithOverlap(content, cfg)
    } else if (cfg.hasStrategy(Paragraph)) {
      // Use paragraph-aware splitting
      splitByParagraphs(content, cfg)
    } else {
      Seq(DocChunk(content, "text", 0, 1))
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
    if (txt.isEmpty) return Seq.empty

    val chunkSize = math.max(1, cfg.maxChars)
    // Limit overlap to prevent performance issues - at most 50% of chunk size
    val maxOverlap = chunkSize / 2
    val overlap = math.max(0, cfg.overlap.min(maxOverlap))
    
    // Calculate stride (how much we advance each iteration)
    // This ensures we always move forward
    val stride = math.max(1, chunkSize - overlap)
    
    // Pre-calculate chunk boundaries
    val starts = (0 until txt.length by stride).toVector
    val numChunks = starts.length
    
    // Build chunks using the pre-calculated boundaries
    val chunks = starts.zipWithIndex.map { case (start, idx) =>
      val end = math.min(start + chunkSize, txt.length)
      val slice = txt.substring(start, end)
      val bytes = slice.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      val fc = FileContent(bytes, TextPlain)
      
      DocChunk(
        fc,
        label = s"chars $start-${end - 1}",
        index = idx,
        total = numChunks
      )
    }
    
    chunks
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
    if (txt.isEmpty) return Seq.empty

    // Find paragraph boundaries while preserving position information
    val paragraphPattern = "\n\\s*\n".r
    val paragraphBoundaries = paragraphPattern.findAllMatchIn(txt).map(_.start).toVector
    
    // Extract paragraphs with their positions
    val paragraphsWithPos = {
      val starts = 0 +: paragraphBoundaries.map(b => {
        // Find the end of the boundary (after whitespace)
        var end = b + 1
        while (end < txt.length && txt(end).isWhitespace) end += 1
        end
      })
      val ends = paragraphBoundaries :+ txt.length
      
      starts.zip(ends).map { case (start, end) =>
        val text = txt.substring(start, end).trim
        (text, start, end)
      }.filter(_._1.nonEmpty)
    }
    
    if (paragraphsWithPos.isEmpty) return Seq.empty

    val chunkSize = math.max(1, cfg.maxChars)
    val builder   = Seq.newBuilder[DocChunk[TextPlain.type]]

    var currentChunk = new StringBuilder()
    var currentChunkStartPos = -1
    var idx = 0
    var startParaIdx = 0
    var parasInCurrentChunk = Vector.empty[(String, Int, Int)]

    for (((paragraph, paraStart, paraEnd), paraIdx) <- paragraphsWithPos.zipWithIndex) {
      // If adding this paragraph would exceed the chunk size and we already have content,
      // finalize the current chunk and start a new one
      if (currentChunk.nonEmpty && currentChunk.length + paragraph.length > chunkSize) {
        val chunkText = currentChunk.toString()
        val bytes = chunkText.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        val fc = FileContent(bytes, TextPlain)

        // Use actual positions from tracking
        val charStart = currentChunkStartPos
        val charEnd = parasInCurrentChunk.last._3 - 1

        builder += DocChunk(
          fc,
          label = s"paragraphs ${startParaIdx + 1}-$paraIdx (chars $charStart-$charEnd)",
          index = idx,
          total = 0
        )
        idx += 1
        currentChunk = new StringBuilder()
        currentChunkStartPos = -1
        startParaIdx = paraIdx
        parasInCurrentChunk = Vector.empty
      }

      // If the paragraph itself is larger than chunk size, split it
      if (paragraph.length > chunkSize) {
        // Flush any pending chunk first
        if (currentChunk.nonEmpty) {
          val chunkText = currentChunk.toString()
          val bytes = chunkText.getBytes(java.nio.charset.StandardCharsets.UTF_8)
          val fc = FileContent(bytes, TextPlain)
          
          val charStart = currentChunkStartPos
          val charEnd = parasInCurrentChunk.last._3 - 1

          builder += DocChunk(
            fc,
            label = s"paragraphs ${startParaIdx + 1}-$paraIdx (chars $charStart-$charEnd)",
            index = idx,
            total = 0
          )
          idx += 1
          currentChunk = new StringBuilder()
          currentChunkStartPos = -1
          parasInCurrentChunk = Vector.empty
        }
        
        // Split the large paragraph
        var start = 0
        while (start < paragraph.length) {
          val end = math.min(start + chunkSize, paragraph.length)
          val slice = paragraph.substring(start, end)
          val bytes = slice.getBytes(java.nio.charset.StandardCharsets.UTF_8)
          val fc = FileContent(bytes, TextPlain)

          val absoluteStart = paraStart + start
          val absoluteEnd = paraStart + end - 1

          builder += DocChunk(
            fc,
            label = s"paragraph ${paraIdx + 1} section $start-${end - 1} (chars $absoluteStart-$absoluteEnd)",
            index = idx,
            total = 0
          )
          idx += 1
          start += chunkSize
        }
        startParaIdx = paraIdx + 1
      } else {
        // Add the paragraph to the current chunk
        if (currentChunk.nonEmpty) {
          currentChunk.append("\n\n")
        } else {
          currentChunkStartPos = paraStart
        }
        currentChunk.append(paragraph)
        parasInCurrentChunk = parasInCurrentChunk :+ (paragraph, paraStart, paraEnd)
      }
    }

    // Don't forget the last chunk if it's not empty
    if (currentChunk.nonEmpty) {
      val chunkText = currentChunk.toString()
      val bytes = chunkText.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      val fc = FileContent(bytes, TextPlain)

      val charStart = currentChunkStartPos
      val charEnd = parasInCurrentChunk.last._3 - 1

      builder += DocChunk(
        fc,
        label = s"paragraphs ${startParaIdx + 1}-${paragraphsWithPos.length} (chars $charStart-$charEnd)",
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
