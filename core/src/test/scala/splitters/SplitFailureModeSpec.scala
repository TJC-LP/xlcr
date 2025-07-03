package com.tjclp.xlcr
package splitters

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import models.FileContent
import types.MimeType

class SplitFailureModeSpec extends AnyFlatSpec with Matchers {
  
  "SplitFailureMode" should "parse from string correctly" in {
    SplitFailureMode.fromString("preserve") shouldBe Some(SplitFailureMode.PreserveAsChunk)
    SplitFailureMode.fromString("throw") shouldBe Some(SplitFailureMode.ThrowException)
    SplitFailureMode.fromString("drop") shouldBe Some(SplitFailureMode.DropDocument)
    SplitFailureMode.fromString("tag") shouldBe Some(SplitFailureMode.TagAndPreserve)
    SplitFailureMode.fromString("PRESERVE") shouldBe Some(SplitFailureMode.PreserveAsChunk)
    SplitFailureMode.fromString(" tag ") shouldBe Some(SplitFailureMode.TagAndPreserve)
    SplitFailureMode.fromString("invalid") shouldBe None
  }
  
  it should "provide all valid names" in {
    SplitFailureMode.validNames should include("preserve")
    SplitFailureMode.validNames should include("throw")
    SplitFailureMode.validNames should include("drop")
    SplitFailureMode.validNames should include("tag")
  }
  
  "SplitConfig" should "support failure mode configuration" in {
    val config = SplitConfig()
    config.failureMode shouldBe SplitFailureMode.PreserveAsChunk
    
    val throwConfig = config.withFailureMode(SplitFailureMode.ThrowException)
    throwConfig.failureMode shouldBe SplitFailureMode.ThrowException
    
    val contextConfig = config
      .withFailureContext("source", "test")
      .withFailureContext("environment", "production")
    contextConfig.failureContext should contain("source" -> "test")
    contextConfig.failureContext should contain("environment" -> "production")
  }
  
  "DocChunk" should "support failure metadata" in {
    val content = FileContent("test".getBytes, MimeType.TextPlain)
    val chunk = DocChunk(content, "test", 0, 1)
    
    chunk.isFailed shouldBe false
    chunk.errorMessage shouldBe None
    
    val failedChunk = chunk.asFailure("Test error", "TestException")
    failedChunk.isFailed shouldBe true
    failedChunk.errorMessage shouldBe Some("Test error")
    failedChunk.errorType shouldBe Some("TestException")
    failedChunk.metadata should contain key "failed_at"
    
    val successChunk = chunk.asSuccess
    successChunk.metadata should contain("split_status" -> "success")
    successChunk.isFailed shouldBe false
  }
  
  "SplitException" should "include rich context in message" in {
    val ex = new SplitException(
      "Test error",
      mimeType = "application/pdf",
      strategy = Some("page"),
      context = Map("file" -> "test.pdf", "size" -> "1024")
    )
    
    ex.getMessage should include("Test error")
    ex.getMessage should include("mime: application/pdf")
    ex.getMessage should include("strategy: page")
    ex.getMessage should include("file=test.pdf")
    ex.getMessage should include("size=1024")
    
    val enriched = ex.withContext("attempt", "2")
    enriched.context should contain("attempt" -> "2")
  }
  
  "InvalidStrategyException" should "provide clear error message" in {
    val ex = new InvalidStrategyException("application/vnd.ms-excel", "page")
    ex.getMessage should include("Strategy 'page' is not supported for MIME type 'application/vnd.ms-excel'")
    ex.mimeType shouldBe "application/vnd.ms-excel"
    ex.strategy shouldBe Some("page")
  }
  
  "CorruptedDocumentException" should "include cause details" in {
    val cause = new java.io.IOException("Bad PDF structure")
    val ex = new CorruptedDocumentException(
      "application/pdf",
      "Invalid PDF header",
      cause
    )
    ex.getMessage should include("Document appears to be corrupted: Invalid PDF header")
    ex.getCause shouldBe cause
  }
}