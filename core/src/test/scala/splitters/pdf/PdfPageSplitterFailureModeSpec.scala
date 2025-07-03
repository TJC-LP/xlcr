package com.tjclp.xlcr
package splitters
package pdf

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import models.FileContent
import types.MimeType

class PdfPageSplitterFailureModeSpec extends AnyFlatSpec with Matchers {
  
  val invalidPdfBytes = Array[Byte](0, 1, 2, 3, 4) // Not a valid PDF
  val emptyPdfBytes = Array.empty[Byte]
  
  "PdfPageSplitter with ThrowException mode" should "throw on invalid strategy" in {
    val content = FileContent(invalidPdfBytes, MimeType.ApplicationPdf)
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Sheet),
      failureMode = SplitFailureMode.ThrowException
    )
    
    val thrown = intercept[SplitException] {
      PdfPageSplitter.split(content, config)
    }
    thrown.getMessage should include("Strategy 'sheet' is not supported")
  }
  
  it should "throw on corrupted PDF" in {
    val content = FileContent(invalidPdfBytes, MimeType.ApplicationPdf)
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page),
      failureMode = SplitFailureMode.ThrowException
    )
    
    val thrown = intercept[SplitException] {
      PdfPageSplitter.split(content, config)
    }
    thrown.getMessage should include("Failed to load PDF")
  }
  
  it should "throw on empty PDF" in {
    val content = FileContent(emptyPdfBytes, MimeType.ApplicationPdf)
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page),
      failureMode = SplitFailureMode.ThrowException
    )
    
    val thrown = intercept[SplitException] {
      PdfPageSplitter.split(content, config)
    }
    thrown.getMessage should include("PDF file is empty")
  }
  
  "PdfPageSplitter with PreserveAsChunk mode" should "return single chunk on error" in {
    val content = FileContent(invalidPdfBytes, MimeType.ApplicationPdf)
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page),
      failureMode = SplitFailureMode.PreserveAsChunk
    )
    
    val result = PdfPageSplitter.split(content, config)
    result.size shouldBe 1
    result.head.label shouldBe "document"
    result.head.index shouldBe 0
    result.head.total shouldBe 1
    result.head.content shouldBe content
  }
  
  "PdfPageSplitter with DropDocument mode" should "return empty sequence on error" in {
    val content = FileContent(invalidPdfBytes, MimeType.ApplicationPdf)
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page),
      failureMode = SplitFailureMode.DropDocument
    )
    
    val result = PdfPageSplitter.split(content, config)
    result shouldBe empty
  }
  
  "PdfPageSplitter with TagAndPreserve mode" should "tag failed documents" in {
    val content = FileContent(invalidPdfBytes, MimeType.ApplicationPdf)
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page),
      failureMode = SplitFailureMode.TagAndPreserve,
      failureContext = Map("test_id" -> "123")
    )
    
    val result = PdfPageSplitter.split(content, config)
    result.size shouldBe 1
    
    val chunk = result.head
    chunk.label shouldBe "failed_document"
    chunk.isFailed shouldBe true
    chunk.errorMessage.isDefined shouldBe true
    chunk.errorType shouldBe Some("CorruptedDocumentException")
    chunk.metadata should contain("mime_type" -> "application/pdf")
    chunk.metadata should contain("test_id" -> "123")
    chunk.metadata should contain key "failed_at"
  }
  
  it should "tag invalid strategy errors" in {
    val content = FileContent(invalidPdfBytes, MimeType.ApplicationPdf)
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Sheet),
      failureMode = SplitFailureMode.TagAndPreserve
    )
    
    val result = PdfPageSplitter.split(content, config)
    result.size shouldBe 1
    
    val chunk = result.head
    chunk.isFailed shouldBe true
    chunk.errorType shouldBe Some("SplitException")
    chunk.metadata should contain("requested_strategy" -> "sheet")
    chunk.metadata should contain("supported_strategies" -> "page")
  }
  
  "PdfPageSplitter with valid PDF" should "mark chunks as successful in TagAndPreserve mode" in {
    // This test would require a valid PDF - skipping for now as it requires test resources
    pending
  }
}