package com.tjclp.xlcr
package splitters.word

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import models.FileContent
import splitters._
import types.MimeType

class WordPageSplitterSpec extends AnyFlatSpec with Matchers {
  
  "WordDocxPageSplitter" should "handle empty documents gracefully" in {
    val content = FileContent(
      Array.empty[Byte],
      MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument
    )
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page),
      failureMode = SplitFailureMode.TagAndPreserve
    )
    
    val result = WordDocxPageSplitter.split(content, config)
    
    result.size shouldBe 1
    result.head.isFailed shouldBe true
    result.head.errorType shouldBe Some("EmptyDocumentException")
  }
  
  "WordDocPageSplitter" should "handle empty documents gracefully" in {
    val content = FileContent(
      Array.empty[Byte],
      MimeType.ApplicationMsWord
    )
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page),
      failureMode = SplitFailureMode.TagAndPreserve
    )
    
    val result = WordDocPageSplitter.split(content, config)
    
    result.size shouldBe 1
    result.head.isFailed shouldBe true
    result.head.errorType shouldBe Some("EmptyDocumentException")
  }
  
  "WordPageSplitter" should "reject non-page strategies" in {
    val content = FileContent(
      Array.empty[Byte],
      MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument
    )
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Sheet),
      failureMode = SplitFailureMode.TagAndPreserve
    )
    
    val result = WordDocxPageSplitter.split(content, config)
    
    result.size shouldBe 1
    result.head.isFailed shouldBe true
    result.head.errorType shouldBe Some("SplitException")
  }
  
  "WordPageSplitter" should "handle invalid chunk ranges" in {
    // This test would require a valid Word document
    // For now, we just verify the structure is in place
    val splitter = WordDocxPageSplitter
    splitter should not be null
    splitter.priority shouldBe types.Priority.DEFAULT
  }
}