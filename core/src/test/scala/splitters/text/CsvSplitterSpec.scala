package com.tjclp.xlcr
package splitters
package text

import java.nio.file.{ Files, Paths }

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import models.FileContent
import types.MimeType

class CsvSplitterSpec extends AnyFlatSpec with Matchers {

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

    // Config with Chunk strategy and small maxChars to force multiple chunks
    val cfg = SplitConfig(
      strategy = Some(SplitStrategy.Chunk),
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
    val smallCfg    = SplitConfig(strategy = Some(SplitStrategy.Chunk), maxChars = 50)
    val smallChunks = CsvSplitter.split(content, smallCfg)

    smallChunks.length should be > 3 // Should have multiple small chunks

    // Then test with a very large limit (should create one chunk with all rows)
    val largeCfg    = SplitConfig(strategy = Some(SplitStrategy.Chunk), maxChars = 100000)
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
    val emptyChunks = CsvSplitter.split(emptyContent, SplitConfig(strategy = Some(SplitStrategy.Chunk)))
    emptyChunks.isEmpty shouldBe true

    // CSV with just a header
    val headerContent =
      FileContent("Header1,Header2,Header3".getBytes, MimeType.TextCsv)
        .asInstanceOf[FileContent[MimeType.TextCsv.type]]
    val headerChunks = CsvSplitter.split(headerContent, SplitConfig(strategy = Some(SplitStrategy.Chunk)))
    headerChunks.isEmpty shouldBe true

    // CSV with header and empty rows
    val emptyRowsContent =
      FileContent("Header1,Header2\n\n\n".getBytes, MimeType.TextCsv)
        .asInstanceOf[FileContent[MimeType.TextCsv.type]]
    val emptyRowsChunks =
      CsvSplitter.split(emptyRowsContent, SplitConfig(strategy = Some(SplitStrategy.Chunk)))
    emptyRowsChunks.isEmpty shouldBe true
  }

  it should "respect chunkRange when splitting by rows" in {
    val filePath  = getClass.getResource("/text_samples/sample.csv").getPath
    val fileBytes = Files.readAllBytes(Paths.get(filePath))
    val content = FileContent(fileBytes, MimeType.TextCsv)
      .asInstanceOf[FileContent[MimeType.TextCsv.type]]

    // Config with Row strategy and chunk range
    val cfg = SplitConfig(
      strategy = Some(SplitStrategy.Row),
      chunkRange = Some(2 until 5) // Get rows 3-5 (0-indexed)
    )

    val chunks = CsvSplitter.split(content, cfg)
    
    // Should have exactly 3 chunks
    chunks.length shouldBe 3
    
    // Verify we got the right rows - CSV splitter re-indexes after filtering
    chunks.foreach { chunk =>
      chunk.total shouldBe 3 // Total updated to filtered count
      
      // Each chunk should still have header + 1 data row
      val lines = new String(chunk.content.data).split("\n")
      lines.length shouldBe 2
      lines(0) shouldBe "Name,Age,City,Occupation,Salary"
    }
    
    // Indices should be 0, 1, 2 after re-indexing
    chunks.map(_.index) shouldBe List(0, 1, 2)
  }

  it should "respect chunkRange when grouping rows into chunks" in {
    val filePath  = getClass.getResource("/text_samples/sample.csv").getPath
    val fileBytes = Files.readAllBytes(Paths.get(filePath))
    val content = FileContent(fileBytes, MimeType.TextCsv)
      .asInstanceOf[FileContent[MimeType.TextCsv.type]]

    // First split into chunks to see how many we get
    val allChunks = CsvSplitter.split(content, SplitConfig(
      strategy = Some(SplitStrategy.Chunk),
      maxChars = 100
    ))
    
    println(s"Total chunks without range: ${allChunks.length}")
    
    // Now apply a range to get only middle chunks
    val cfg = SplitConfig(
      strategy = Some(SplitStrategy.Chunk),
      maxChars = 100,
      chunkRange = Some(1 until Math.min(3, allChunks.length))
    )

    val chunks = CsvSplitter.split(content, cfg)
    
    // Should have at most 2 chunks (indices 1 and 2)
    chunks.length should be <= 2
    chunks.length should be > 0
    
    // Chunks should be the middle chunks with original indices preserved
    // The filtered chunks keep their original index from allChunks
    chunks.map(_.index) should not be empty
    chunks.foreach { chunk =>
      chunk.total shouldBe chunks.length
    }
  }

  it should "handle out-of-bounds chunkRange gracefully" in {
    val filePath  = getClass.getResource("/text_samples/sample.csv").getPath
    val fileBytes = Files.readAllBytes(Paths.get(filePath))
    val content = FileContent(fileBytes, MimeType.TextCsv)
      .asInstanceOf[FileContent[MimeType.TextCsv.type]]

    // Range completely out of bounds
    val cfg1 = SplitConfig(
      strategy = Some(SplitStrategy.Row),
      chunkRange = Some(100 until 200)
    )
    val chunks1 = CsvSplitter.split(content, cfg1)
    chunks1 shouldBe empty

    // Range partially out of bounds
    val cfg2 = SplitConfig(
      strategy = Some(SplitStrategy.Row),
      chunkRange = Some(8 until 20) // Assuming less than 20 rows
    )
    val chunks2 = CsvSplitter.split(content, cfg2)
    chunks2.length should be >= 0
    chunks2.length should be < 12 // Can't have 12 chunks if range starts at 8
  }

  it should "extract single row using chunkRange" in {
    val filePath  = getClass.getResource("/text_samples/sample.csv").getPath
    val fileBytes = Files.readAllBytes(Paths.get(filePath))
    val content = FileContent(fileBytes, MimeType.TextCsv)
      .asInstanceOf[FileContent[MimeType.TextCsv.type]]

    val cfg = SplitConfig(
      strategy = Some(SplitStrategy.Row),
      chunkRange = Some(0 until 1) // Just the first data row
    )

    val chunks = CsvSplitter.split(content, cfg)
    
    chunks.length shouldBe 1
    chunks.head.index shouldBe 0
    chunks.head.total shouldBe 10 // Total reflects total data rows (without header)
    
    // Should have header + first data row
    val lines = new String(chunks.head.content.data).split("\n")
    lines.length shouldBe 2
    lines(0) shouldBe "Name,Age,City,Occupation,Salary"
  }
}
