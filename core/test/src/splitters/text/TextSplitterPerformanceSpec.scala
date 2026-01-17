package com.tjclp.xlcr
package splitters
package text

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import models.FileContent
import types.MimeType

/**
 * Performance and edge case tests for TextSplitter. These tests specifically target issues that
 * were fixed:
 *   1. High overlap causing O(n²) performance 2. Incorrect paragraph position tracking with
 *      duplicates 3. Potential infinite loops 4. Empty text handling
 */
class TextSplitterPerformanceSpec extends AnyFlatSpec with Matchers {

  "TextSplitter performance" should "handle high overlap without degradation" in {
    // This test would have taken forever with the old implementation
    val largeText = "a" * 10000 // 10K characters
    val content = FileContent(largeText.getBytes(), MimeType.TextPlain)
      .asInstanceOf[FileContent[MimeType.TextPlain.type]]

    val cfg = SplitConfig(
      strategy = Some(SplitStrategy.Chunk),
      maxChars = 1000,
      overlap = 999 // Would have caused O(n²) behavior before fix
    )

    val startTime = System.currentTimeMillis()
    val chunks    = TextSplitter.split(content, cfg)
    val endTime   = System.currentTimeMillis()
    val duration  = endTime - startTime

    // Should complete quickly even with high overlap
    duration should be < 1000L // Less than 1 second

    // With 50% overlap limit, we should have ~20 chunks, not 10000
    chunks.length should be < 50

    // Verify chunks progress correctly
    val firstChunk  = chunks.head
    val secondChunk = chunks(1)
    firstChunk.label shouldBe "chars 0-999"
    secondChunk.label shouldBe "chars 500-1499" // 50% overlap
  }

  it should "never create infinite loops with extreme overlap" in {
    val content = FileContent("test content for overlap".getBytes(), MimeType.TextPlain)
      .asInstanceOf[FileContent[MimeType.TextPlain.type]]

    val cfg = SplitConfig(
      strategy = Some(SplitStrategy.Chunk),
      maxChars = 5,
      overlap = 10 // Overlap greater than maxChars
    )

    // Should complete without hanging
    val chunks = TextSplitter.split(content, cfg)
    chunks should not be empty

    // Verify overlap was capped at 50%
    // With maxChars=5 and 50% overlap, stride=3
    val expectedChunkCount = math.ceil(24.0 / 3).toInt // text length / stride
    chunks.length shouldBe expectedChunkCount
  }

  it should "calculate stride correctly to ensure forward progress" in {
    val content = FileContent(("a" * 100).getBytes(), MimeType.TextPlain)
      .asInstanceOf[FileContent[MimeType.TextPlain.type]]

    val cfg = SplitConfig(
      strategy = Some(SplitStrategy.Chunk),
      maxChars = 10,
      overlap = 8
    )

    val chunks = TextSplitter.split(content, cfg)

    // With maxChars=10 and overlap=8 (capped to 5), stride should be 5
    // So we should have 20 chunks (100 chars / 5 stride)
    chunks.length shouldBe 20

    // Verify progression
    chunks.sliding(2).foreach { case Seq(chunk1, chunk2) =>
      val start1 = chunk1.label.split("-")(0).split(" ")(1).toInt
      val start2 = chunk2.label.split("-")(0).split(" ")(1).toInt
      (start2 - start1) shouldBe 5 // Stride of 5
    }
  }

  "TextSplitter edge cases" should "handle empty text gracefully" in {
    val content = FileContent("".getBytes(), MimeType.TextPlain)
      .asInstanceOf[FileContent[MimeType.TextPlain.type]]

    // Test both strategies
    val chunkCfg = SplitConfig(strategy = Some(SplitStrategy.Chunk))
    val paraCfg  = SplitConfig(strategy = Some(SplitStrategy.Paragraph))

    val chunkResult = TextSplitter.split(content, chunkCfg)
    val paraResult  = TextSplitter.split(content, paraCfg)

    chunkResult shouldBe empty
    paraResult shouldBe empty
  }

  it should "handle single character text" in {
    val content = FileContent("a".getBytes(), MimeType.TextPlain)
      .asInstanceOf[FileContent[MimeType.TextPlain.type]]

    val cfg = SplitConfig(
      strategy = Some(SplitStrategy.Chunk),
      maxChars = 10
    )

    val chunks = TextSplitter.split(content, cfg)
    chunks.length shouldBe 1
    chunks.head.label shouldBe "chars 0-0"
  }

  "TextSplitter paragraph tracking" should "correctly track positions with duplicate paragraphs" in {
    // This would have failed with indexOf approach
    val text = """First paragraph

First paragraph

Third paragraph

First paragraph"""

    val content = FileContent(text.getBytes(), MimeType.TextPlain)
      .asInstanceOf[FileContent[MimeType.TextPlain.type]]

    val cfg = SplitConfig(
      strategy = Some(SplitStrategy.Paragraph),
      maxChars = 50
    )

    val chunks = TextSplitter.split(content, cfg)

    // Chunks might be combined if they fit within maxChars
    chunks.length should be >= 2

    // Each duplicate should have correct position
    chunks.foreach { chunk =>
      chunk.label should include("chars")
      // Extract char positions from label
      val charPattern = """chars (\d+)-(\d+)""".r
      val matches     = charPattern.findFirstMatchIn(chunk.label)
      matches shouldBe defined

      val start = matches.get.group(1).toInt
      val end   = matches.get.group(2).toInt

      // Verify the positions point to actual paragraph locations
      val extractedText = text.substring(start, math.min(end + 1, text.length))
      // The extracted text should contain the paragraph content
      val chunkContent = new String(chunk.content.data)
      extractedText should include(chunkContent.trim)
    }

    // The key test is that position tracking is correct, not chunk count
    // Let's verify that the character positions in labels match actual content
    var allPositionsCorrect = true

    chunks.foreach { chunk =>
      val charPattern = """chars (\d+)-(\d+)""".r
      val matches     = charPattern.findFirstMatchIn(chunk.label)
      if (matches.isDefined) {
        val start = matches.get.group(1).toInt
        val end   = matches.get.group(2).toInt

        // Extract what's at those positions in original text
        val expectedContent = text.substring(start, math.min(end + 1, text.length))
        val actualContent   = new String(chunk.content.data)

        // The chunk content should match what's at those positions (after trimming)
        val contentMatches = expectedContent.trim.contains(actualContent.trim) ||
          actualContent.trim.contains(expectedContent.trim)

        if (!contentMatches) {
          println(
            s"Position mismatch: expected at $start-$end: '$expectedContent', got: '$actualContent'"
          )
          allPositionsCorrect = false
        }
      }
    }

    allPositionsCorrect shouldBe true
  }

  it should "handle paragraphs with varying whitespace correctly" in {
    val text = """First paragraph

Second paragraph


Third paragraph with extra spacing



Fourth paragraph with lots of space"""

    val content = FileContent(text.getBytes(), MimeType.TextPlain)
      .asInstanceOf[FileContent[MimeType.TextPlain.type]]

    val cfg = SplitConfig(
      strategy = Some(SplitStrategy.Paragraph),
      maxChars = 100
    )

    val chunks = TextSplitter.split(content, cfg)

    // Should identify all 4 paragraphs despite varying spacing
    val allContent = chunks.map(c => new String(c.content.data)).mkString(" ")

    // Paragraphs might be combined if they fit within maxChars
    allContent should include("First paragraph")
    allContent should include("Second paragraph")
    allContent should include("Third paragraph with extra spacing")
    allContent should include("Fourth paragraph with lots of space")

    // Verify positions are tracked correctly
    chunks.foreach { chunk =>
      val charPattern = """chars (\d+)-(\d+)""".r
      val matches     = charPattern.findFirstMatchIn(chunk.label)
      matches shouldBe defined
    }
  }

  it should "split large paragraphs with correct position tracking" in {
    val largePara = "x" * 1000
    val text = s"""Small paragraph

$largePara

Another small paragraph"""

    val content = FileContent(text.getBytes(), MimeType.TextPlain)
      .asInstanceOf[FileContent[MimeType.TextPlain.type]]

    val cfg = SplitConfig(
      strategy = Some(SplitStrategy.Paragraph),
      maxChars = 100
    )

    val chunks = TextSplitter.split(content, cfg)

    // Should have chunks for small para, multiple for large para, and last small para
    chunks.length should be >= 12 // 1 + 10 + 1

    // Verify large paragraph is split with correct labels
    val largeParagraphChunks = chunks.filter(_.label.contains("paragraph 2 section"))
    largeParagraphChunks.length shouldBe 10

    // Check section numbering and absolute positions
    largeParagraphChunks.zipWithIndex.foreach { case (chunk, idx) =>
      chunk.label should include(s"section ${idx * 100}-${math.min((idx + 1) * 100 - 1, 999)}")

      // Extract absolute positions
      val charPattern = """chars (\d+)-(\d+)""".r
      val matches     = charPattern.findFirstMatchIn(chunk.label).get
      val absStart    = matches.group(1).toInt

      // The absolute start should account for "Small paragraph\n\n" prefix
      absStart should be >= 16 // Length of "Small paragraph\n\n"
    }
  }

  "TextSplitter strategy handling" should "use character-based splitting for Chunk strategy" in {
    val content =
      FileContent("Simple text content for testing chunks".getBytes(), MimeType.TextPlain)
        .asInstanceOf[FileContent[MimeType.TextPlain.type]]

    val cfg = SplitConfig(
      strategy = Some(SplitStrategy.Chunk),
      maxChars = 10
    )

    val chunks = TextSplitter.split(content, cfg)

    // Should use character-based splitting with "chars X-Y" labels
    chunks.foreach { chunk =>
      chunk.label should startWith("chars ")
      (chunk.label should not).include("paragraph")
    }

    // Verify content is split correctly
    val reconstructed = chunks.map(c => new String(c.content.data)).mkString("")
    reconstructed shouldBe "Simple text content for testing chunks"
  }

  it should "handle invalid strategies by returning single chunk" in {
    val content = FileContent("Test content for invalid strategy".getBytes(), MimeType.TextPlain)
      .asInstanceOf[FileContent[MimeType.TextPlain.type]]

    val invalidStrategies = Seq(
      SplitStrategy.Row,
      SplitStrategy.Sheet,
      SplitStrategy.Page,
      SplitStrategy.Slide
    )

    invalidStrategies.foreach { strategy =>
      val cfg = SplitConfig(
        strategy = Some(strategy),
        maxChars = 5
      )

      val chunks = TextSplitter.split(content, cfg)

      // Should return entire content as single chunk
      chunks.length shouldBe 1
      chunks.head.label shouldBe "document" // Changed from "text" - failure handler returns "document"
      new String(chunks.head.content.data) shouldBe "Test content for invalid strategy"
    }
  }

  "TextSplitter performance with real-world text" should "handle Lorem Ipsum efficiently" in {
    val loremIpsum =
      """Lorem ipsum dolor sit amet, consectetur adipiscing elit.

Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.

Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris.

Nisi ut aliquip ex ea commodo consequat.""" * 100 // Repeat to make it larger

    val content = FileContent(loremIpsum.getBytes(), MimeType.TextPlain)
      .asInstanceOf[FileContent[MimeType.TextPlain.type]]

    val cfg = SplitConfig(
      strategy = Some(SplitStrategy.Paragraph),
      maxChars = 200
    )

    val startTime = System.currentTimeMillis()
    val chunks    = TextSplitter.split(content, cfg)
    val endTime   = System.currentTimeMillis()

    // Should complete quickly
    (endTime - startTime) should be < 500L

    // Should create reasonable number of chunks
    chunks.length should be > 100
    chunks.length should be < 500

    // All chunks should have valid labels with positions
    chunks.foreach { chunk =>
      chunk.label should include("chars")
      chunk.label should (include("paragraph").or(include("paragraphs")))
    }
  }
}
