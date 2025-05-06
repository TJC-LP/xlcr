package com.tjclp.xlcr
package splitters
package text

import java.nio.file.{ Files, Paths }

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import models.FileContent
import types.MimeType

object CsvSplitterSpec extends AnyFlatSpec with Matchers {

  "CsvSplitter" should "split CSV by individual rows with Row strategy" in {
    val filePath  = getClass.getResource("/text_samples/sample.csv").getPath
    val fileBytes = Files.readAllBytes(Paths.get(filePath))
    val content = FileContent(fileBytes, MimeType.TextCsv)
      .asInstanceOf[FileContent[MimeType.TextCsv.type]]

    // Config with Row strategy
    val cfg = SplitConfig(
      strategy = Some(SplitStrategy.Row)
    )

    val splitter = CsvSplitter
    val chunks   = splitter.split(content, cfg)

    // We should have one chunk per row (excluding header)
    val expectedRows = 10
    chunks.length shouldBe expectedRows

    // Verify labels show individual rows
    chunks.foreach { chunk =>
      chunk.label should startWith("row")
      println(s"Chunk label: ${chunk.label}")

      // Each chunk should contain the header + 1 row
      val chunkContent = new String(chunk.content.data)
      val lines        = chunkContent.split("\n")
      lines.length shouldBe 2

      // First line should be header
      lines(0) shouldBe "Name,Age,City,Occupation,Salary"
    }

    // Verify chunk index and totals
    chunks.zipWithIndex.foreach { case (chunk, idx) =>
      chunk.index shouldBe idx
      chunk.total shouldBe chunks.length
    }
  }

  it should "group rows into chunks based on character count with default strategy" in {
    val filePath  = getClass.getResource("/text_samples/sample.csv").getPath
    val fileBytes = Files.readAllBytes(Paths.get(filePath))
    val content = FileContent(fileBytes, MimeType.TextCsv)
      .asInstanceOf[FileContent[MimeType.TextCsv.type]]

    // Config with default strategy but small maxChars to force multiple chunks
    val cfg = SplitConfig(
      strategy = None, // Use default
      maxChars = 100   // Small character limit to force multiple chunks
    )

    val splitter = CsvSplitter
    val chunks   = splitter.split(content, cfg)

    // We should have multiple chunks
    chunks.length should be > 1

    // Verify labels
    chunks.foreach { chunk =>
      chunk.label should startWith("rows")

      // Each chunk should contain the header + some rows
      val chunkContent = new String(chunk.content.data)
      val lines        = chunkContent.split("\n")
      lines.length should be > 1

      // First line should be header
      lines(0) shouldBe "Name,Age,City,Occupation,Salary"

      // Verify chunk sizes are approximately limited by character count
      // (allowing some flexibility since we always include at least one row)
      if (lines.length > 2) { // If chunk has more than header + 1 row
        val totalChars = chunkContent.length
        totalChars should be <= cfg.maxChars * 2 // Allow some flexibility for very long rows
      }
    }

    // Verify chunk index and totals
    chunks.zipWithIndex.foreach { case (chunk, idx) =>
      chunk.index shouldBe idx
      chunk.total shouldBe chunks.length
    }

    // Print chunk statistics for debugging
    chunks.foreach { chunk =>
      val chunkContent = new String(chunk.content.data)
      val lines        = chunkContent.split("\n")
      println(
        s"Chunk '${chunk.label}': ${chunkContent.length} chars, ${lines.length - 1} rows (plus header)"
      )
    }
  }

  it should "handle both very small and large character limits correctly" in {
    val filePath  = getClass.getResource("/text_samples/sample.csv").getPath
    val fileBytes = Files.readAllBytes(Paths.get(filePath))
    val content = FileContent(fileBytes, MimeType.TextCsv)
      .asInstanceOf[FileContent[MimeType.TextCsv.type]]

    // First test with a very small limit (should create many small chunks)
    val smallCfg    = SplitConfig(maxChars = 50)
    val smallChunks = CsvSplitter.split(content, smallCfg)

    smallChunks.length should be > 3 // Should have multiple small chunks

    // Then test with a very large limit (should create one chunk with all rows)
    val largeCfg    = SplitConfig(maxChars = 100000)
    val largeChunks = CsvSplitter.split(content, largeCfg)

    largeChunks.length shouldBe 1 // Should have just one chunk

    // The single large chunk should contain all rows
    val csv        = new String(content.data)
    val totalLines = csv.split("\n").length

    val largeChunkContent = new String(largeChunks.head.content.data)
    val largeChunkLines   = largeChunkContent.split("\n").length

    largeChunkLines shouldBe totalLines
  }

  it should "handle empty CSV files gracefully" in {
    // Empty content
    val emptyContent = FileContent("".getBytes, MimeType.TextCsv)
      .asInstanceOf[FileContent[MimeType.TextCsv.type]]
    val emptyChunks = CsvSplitter.split(emptyContent, SplitConfig())
    emptyChunks.isEmpty shouldBe true

    // CSV with just a header
    val headerContent =
      FileContent("Header1,Header2,Header3".getBytes, MimeType.TextCsv)
        .asInstanceOf[FileContent[MimeType.TextCsv.type]]
    val headerChunks = CsvSplitter.split(headerContent, SplitConfig())
    headerChunks.isEmpty shouldBe true

    // CSV with header and empty rows
    val emptyRowsContent =
      FileContent("Header1,Header2\n\n\n".getBytes, MimeType.TextCsv)
        .asInstanceOf[FileContent[MimeType.TextCsv.type]]
    val emptyRowsChunks =
      CsvSplitter.split(emptyRowsContent, SplitConfig())
    emptyRowsChunks.isEmpty shouldBe true
  }
}
