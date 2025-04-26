package com.tjclp.xlcr
package utils

import models.FileContent
import types.MimeType

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.File
import java.nio.file.{Files, Paths}

class TextSplitterSpec extends AnyFlatSpec with Matchers {
  
  "TextSplitter" should "split text into chunks using the Chunk strategy" in {
    val filePath = getClass.getResource("/text_samples/sample.txt").getPath
    val fileBytes = Files.readAllBytes(Paths.get(filePath))
    val content = FileContent(fileBytes, MimeType.TextPlain).asInstanceOf[FileContent[MimeType.TextPlain.type]]
    
    // Config with Chunk strategy
    val cfg = SplitConfig(
      strategy = Some(SplitStrategy.Chunk),
      maxChars = 500  // Small enough to split our sample
    )
    
    val splitter = new TextSplitter()
    val chunks = splitter.split(content, cfg)
    
    // We should have multiple chunks
    chunks.length should be > 1
    
    // Verify labels show paragraph ranges and character bounds
    chunks.foreach { chunk =>
      chunk.label should (startWith("paragraphs") or startWith("paragraph"))
      chunk.label should include("chars")
      println(s"Chunk label: ${chunk.label}, size: ${chunk.content.data.length} bytes")
    }
    
    // Verify chunk index and totals
    chunks.zipWithIndex.foreach { case (chunk, idx) =>
      chunk.index shouldBe idx
      chunk.total shouldBe chunks.length
    }
  }
  
  it should "use character ranges in labels with Chunk strategy" in {
    val filePath = getClass.getResource("/text_samples/sample.txt").getPath
    val fileBytes = Files.readAllBytes(Paths.get(filePath))
    val content = FileContent(fileBytes, MimeType.TextPlain).asInstanceOf[FileContent[MimeType.TextPlain.type]]
    
    // Use limited content to avoid memory issues
    val shortContent = FileContent(
      "This is a short text for testing character labels.\n\nJust a few lines to verify the labels.".getBytes(),
      MimeType.TextPlain
    ).asInstanceOf[FileContent[MimeType.TextPlain.type]]
    
    // Default strategy with small chunk size
    val cfg = SplitConfig(
      strategy = Some(SplitStrategy.Chunk),
      maxChars = 30,  // Very small chunks for test
      overlap = 0     // No overlap to avoid memory issues
    )
    
    val splitter = new TextSplitter()
    val chunks = splitter.split(shortContent, cfg)
    
    // We should have multiple chunks
    chunks.length should be > 1
    
    // Verify labels contain character bounds and proper formatting
    chunks.foreach { chunk =>
      chunk.label should include("chars")
      // Labels should include paragraph number and character positions
      chunk.label should (startWith("paragraph") or startWith("paragraphs"))
      // Create debug output to verify the formatting 
      println(s"Chunk label: ${chunk.label}, size: ${chunk.content.data.length} bytes")
    }
  }
}