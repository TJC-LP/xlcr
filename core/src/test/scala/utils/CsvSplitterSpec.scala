package com.tjclp.xlcr
package utils

import models.FileContent
import types.MimeType

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.File
import java.nio.file.{Files, Paths}

class CsvSplitterSpec extends AnyFlatSpec with Matchers {
  
  "CsvSplitter" should "split CSV by individual rows with Row strategy" in {
    val filePath = getClass.getResource("/text_samples/sample.csv").getPath
    val fileBytes = Files.readAllBytes(Paths.get(filePath))
    val content = FileContent(fileBytes, MimeType.TextCsv).asInstanceOf[FileContent[MimeType.TextCsv.type]]
    
    // Config with Row strategy
    val cfg = SplitConfig(
      strategy = Some(SplitStrategy.Row)
    )
    
    val splitter = new CsvSplitter()
    val chunks = splitter.split(content, cfg)
    
    // We should have one chunk per row (excluding header)
    val expectedRows = 10
    chunks.length shouldBe expectedRows
    
    // Verify labels show individual rows
    chunks.foreach { chunk =>
      chunk.label should startWith("row")
      println(s"Chunk label: ${chunk.label}")
      
      // Each chunk should contain the header + 1 row
      val chunkContent = new String(chunk.content.data)
      val lines = chunkContent.split("\n")
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
  
  it should "group rows into chunks with default strategy" in {
    val filePath = getClass.getResource("/text_samples/sample.csv").getPath
    val fileBytes = Files.readAllBytes(Paths.get(filePath))
    val content = FileContent(fileBytes, MimeType.TextCsv).asInstanceOf[FileContent[MimeType.TextCsv.type]]
    
    // Config with default strategy but small maxChars to force multiple chunks
    val cfg = SplitConfig(
      strategy = None,  // Use default
      maxChars = 3      // 3 rows per chunk
    )
    
    val splitter = new CsvSplitter()
    val chunks = splitter.split(content, cfg)
    
    // We should have multiple chunks
    chunks.length should be > 1
    
    // Verify labels
    chunks.foreach { chunk =>
      chunk.label should startWith("rows")
      println(s"Chunk label: ${chunk.label}")
      
      // Each chunk should contain the header + some rows
      val chunkContent = new String(chunk.content.data)
      val lines = chunkContent.split("\n")
      lines.length should be > 1
      
      // First line should be header
      lines(0) shouldBe "Name,Age,City,Occupation,Salary"
    }
  }
}