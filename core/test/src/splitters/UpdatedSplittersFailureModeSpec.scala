package com.tjclp.xlcr
package splitters

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import models.FileContent
import types.MimeType
import splitters.text.TextSplitter
import splitters.archive.ZipEntrySplitter
import splitters.email.EmailAttachmentSplitter

/**
 * Simple tests for the minimal failure mode implementation in updated splitters.
 */
class UpdatedSplittersFailureModeSpec extends AnyFlatSpec with Matchers {

  // Test data
  val invalidTextBytes  = Array.empty[Byte]          // Empty text file
  val invalidZipBytes   = Array[Byte](0, 1, 2, 3, 4) // Not a valid ZIP
  val invalidEmailBytes = Array[Byte](0, 1, 2, 3, 4) // Not a valid email

  "TextSplitter" should "handle invalid strategies" in {
    val content = FileContent("test content".getBytes, MimeType.TextPlain)
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page), // Invalid for text
      failureMode = SplitFailureMode.ThrowException
    )

    val thrown = intercept[SplitException] {
      TextSplitter.split(content, config)
    }
    thrown.getMessage should include("Strategy 'page' is not supported")
  }

  it should "respect failure modes for errors" in {
    val content = FileContent("test".getBytes, MimeType.TextPlain)

    // PreserveAsChunk mode (default)
    val preserveConfig = SplitConfig()
    val preserveResult = TextSplitter.split(content, preserveConfig)
    preserveResult.size should be >= 1 // Should return at least one chunk

    // TagAndPreserve mode
    val tagConfig = SplitConfig(failureMode = SplitFailureMode.TagAndPreserve)
    val tagResult = TextSplitter.split(content, tagConfig)
    tagResult.foreach { chunk =>
      chunk.metadata should contain("split_status" -> "success")
    }
  }

  "ZipEntrySplitter" should "handle invalid strategies" in {
    val content = FileContent(invalidZipBytes, MimeType.ApplicationZip)
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page), // Invalid for ZIP
      failureMode = SplitFailureMode.ThrowException
    )

    val thrown = intercept[SplitException] {
      ZipEntrySplitter.split(content, config)
    }
    thrown.getMessage should include("Strategy 'page' is not supported")
  }

  it should "handle corrupted ZIP files" in {
    val content = FileContent(invalidZipBytes, MimeType.ApplicationZip)

    // ThrowException mode
    val throwConfig = SplitConfig(
      strategy = Some(SplitStrategy.Embedded),
      failureMode = SplitFailureMode.ThrowException
    )
    assertThrows[SplitException] {
      ZipEntrySplitter.split(content, throwConfig)
    }

    // PreserveAsChunk mode
    val preserveConfig = SplitConfig(
      strategy = Some(SplitStrategy.Embedded),
      failureMode = SplitFailureMode.PreserveAsChunk
    )
    val preserveResult = ZipEntrySplitter.split(content, preserveConfig)
    preserveResult.size shouldBe 1
    preserveResult.head.label shouldBe "document"

    // DropDocument mode
    val dropConfig = SplitConfig(
      strategy = Some(SplitStrategy.Embedded),
      failureMode = SplitFailureMode.DropDocument
    )
    val dropResult = ZipEntrySplitter.split(content, dropConfig)
    dropResult shouldBe empty

    // TagAndPreserve mode
    val tagConfig = SplitConfig(
      strategy = Some(SplitStrategy.Embedded),
      failureMode = SplitFailureMode.TagAndPreserve
    )
    val tagResult = ZipEntrySplitter.split(content, tagConfig)
    tagResult.size shouldBe 1
    tagResult.head.isFailed shouldBe true
    tagResult.head.errorMessage.isDefined shouldBe true
  }

  it should "handle empty ZIP archives" in {
    // Create a valid but empty ZIP
    val baos = new java.io.ByteArrayOutputStream()
    val zos  = new java.util.zip.ZipOutputStream(baos)
    zos.close()
    val emptyZipBytes = baos.toByteArray

    val content = FileContent(emptyZipBytes, MimeType.ApplicationZip)
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Embedded),
      failureMode = SplitFailureMode.ThrowException
    )

    val thrown = intercept[SplitException] {
      ZipEntrySplitter.split(content, config)
    }
    thrown.getMessage should include("ZIP archive contains no valid entries")
  }

  "EmailAttachmentSplitter" should "handle invalid strategies" in {
    val content = FileContent(invalidEmailBytes, MimeType.MessageRfc822)
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page), // Invalid for email
      failureMode = SplitFailureMode.ThrowException
    )

    val thrown = intercept[SplitException] {
      EmailAttachmentSplitter.split(content, config)
    }
    thrown.getMessage should include("Strategy 'page' is not supported")
  }

  it should "handle malformed emails" in {
    val content = FileContent(invalidEmailBytes, MimeType.MessageRfc822)

    // Note: Jakarta Mail might parse invalid bytes without throwing,
    // creating an email with no body or attachments

    // PreserveAsChunk mode - should handle gracefully
    val preserveConfig = SplitConfig(
      strategy = Some(SplitStrategy.Attachment),
      failureMode = SplitFailureMode.PreserveAsChunk
    )
    val preserveResult = EmailAttachmentSplitter.split(content, preserveConfig)
    preserveResult.size shouldBe 1
    // Jakarta Mail might extract some content even from invalid bytes
    preserveResult.head.label should (be("document").or(be("body")))

    // TagAndPreserve mode
    val tagConfig = SplitConfig(
      strategy = Some(SplitStrategy.Attachment),
      failureMode = SplitFailureMode.TagAndPreserve
    )
    val tagResult = EmailAttachmentSplitter.split(content, tagConfig)
    tagResult.size shouldBe 1
    // Should either be marked as failed or successful with no attachments
    if (tagResult.head.isFailed) {
      tagResult.head.errorMessage.isDefined shouldBe true
    } else {
      tagResult.head.metadata should contain("split_status" -> "success")
    }
  }

  "All updated splitters" should "maintain backward compatibility" in {
    // Default config should use PreserveAsChunk mode
    val defaultConfig = SplitConfig()
    defaultConfig.failureMode shouldBe SplitFailureMode.PreserveAsChunk

    // Test with each splitter - should not throw exceptions by default
    val textContent = FileContent("test".getBytes, MimeType.TextPlain)
    noException should be thrownBy TextSplitter.split(textContent, defaultConfig)

    val zipContent = FileContent(invalidZipBytes, MimeType.ApplicationZip)
    noException should be thrownBy ZipEntrySplitter.split(
      zipContent,
      defaultConfig.copy(strategy = Some(SplitStrategy.Embedded))
    )

    val emailContent = FileContent(invalidEmailBytes, MimeType.MessageRfc822)
    noException should be thrownBy EmailAttachmentSplitter.split(
      emailContent,
      defaultConfig.copy(strategy = Some(SplitStrategy.Attachment))
    )
  }
}
