package com.tjclp.xlcr
package splitters

import org.scalatest.matchers.should.Matchers

import models.FileContent
import types.MimeType

/**
 * Helper utilities for testing failure modes across different splitters.
 * Provides common test data, assertions, and utility methods.
 */
object FailureModeTestHelper extends Matchers {
  
  // Common invalid test data
  val invalidBytes: Array[Byte] = Array[Byte](0x00, 0x01, 0x02, 0x03)
  val emptyBytes: Array[Byte] = Array.empty[Byte]
  
  // Invalid but more realistic data for different formats
  val invalidPdfBytes: Array[Byte] = "Not a PDF file".getBytes("UTF-8")
  val invalidZipBytes: Array[Byte] = "PK\u0000\u0000Invalid".getBytes("UTF-8") // Starts like ZIP but invalid
  val invalidExcelBytes: Array[Byte] = Array.fill(100)(0xFF.toByte) // Random bytes
  val invalidWordBytes: Array[Byte] = "This is not a Word document".getBytes("UTF-8")
  
  // Valid but empty files
  val emptyZipBytes: Array[Byte] = Array[Byte](
    0x50, 0x4B, 0x05, 0x06, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00
  )
  
  // Large data for size limit testing
  def largeBytes(sizeMB: Int): Array[Byte] = Array.fill(sizeMB * 1024 * 1024)(0x41.toByte) // 'A' repeated
  
  /**
   * Create test content for a specific MIME type with invalid data
   */
  def invalidContentFor(mimeType: MimeType): FileContent[_ <: MimeType] = {
    val bytes = mimeType match {
      case MimeType.ApplicationPdf => invalidPdfBytes
      case MimeType.ApplicationZip => invalidZipBytes
      case MimeType.ApplicationVndMsExcel => invalidExcelBytes
      case MimeType.ApplicationMsWord => invalidWordBytes
      case _ => invalidBytes
    }
    FileContent(bytes, mimeType)
  }
  
  /**
   * Create test content for a specific MIME type with empty data
   */
  def emptyContentFor(mimeType: MimeType): FileContent[_ <: MimeType] = {
    val bytes = mimeType match {
      case MimeType.ApplicationZip => emptyZipBytes
      case _ => emptyBytes
    }
    FileContent(bytes, mimeType)
  }
  
  /**
   * Validate that an exception has the expected properties
   */
  def validateException[T <: SplitException](
    ex: T,
    expectedMessage: String = "",
    expectedMimeType: String = "",
    expectedContext: Map[String, String] = Map.empty
  ): Unit = {
    if (expectedMessage.nonEmpty) {
      ex.getMessage should include(expectedMessage)
    }
    if (expectedMimeType.nonEmpty) {
      ex.mimeType shouldBe expectedMimeType
    }
    expectedContext.foreach { case (key, value) =>
      ex.context should contain(key -> value)
    }
  }
  
  /**
   * Validate that a DocChunk represents a failure with expected properties
   */
  def validateFailedChunk(
    chunk: DocChunk[_ <: MimeType],
    expectedLabel: String = "document",
    expectedError: Option[String] = None,
    expectedErrorType: Option[String] = None
  ): Unit = {
    chunk.label shouldBe expectedLabel
    chunk.isFailed shouldBe true
    
    expectedError.foreach { error =>
      chunk.errorMessage should be(defined)
      chunk.errorMessage.get should include(error)
    }
    
    expectedErrorType.foreach { errorType =>
      chunk.errorType shouldBe Some(errorType)
    }
    
    // Check standard failure metadata
    chunk.metadata should contain key "split_status"
    chunk.metadata("split_status") shouldBe "failed"
  }
  
  /**
   * Test configurations for different failure modes
   */
  def throwExceptionConfig(strategy: SplitStrategy): SplitConfig = 
    SplitConfig(
      strategy = Some(strategy),
      failureMode = SplitFailureMode.ThrowException
    )
    
  def preserveAsChunkConfig(strategy: SplitStrategy): SplitConfig = 
    SplitConfig(
      strategy = Some(strategy),
      failureMode = SplitFailureMode.PreserveAsChunk
    )
    
  def dropDocumentConfig(strategy: SplitStrategy): SplitConfig = 
    SplitConfig(
      strategy = Some(strategy),
      failureMode = SplitFailureMode.DropDocument
    )
    
  def tagAndPreserveConfig(strategy: SplitStrategy, context: Map[String, String] = Map.empty): SplitConfig = 
    SplitConfig(
      strategy = Some(strategy),
      failureMode = SplitFailureMode.TagAndPreserve,
      failureContext = context
    )
  
  /**
   * Common test scenarios that can be reused across different splitters
   */
  trait CommonFailureModeTests { self: Matchers =>
    
    def testInvalidStrategy[T <: MimeType](
      splitter: DocumentSplitter[T],
      content: FileContent[T],
      invalidStrategy: SplitStrategy,
      validStrategies: Seq[String]
    ): Unit = {
      // Test ThrowException mode
      val throwConfig = throwExceptionConfig(invalidStrategy)
      val thrown = intercept[SplitException] {
        splitter.split(content, throwConfig)
      }
      thrown.getMessage should include(s"Strategy '${invalidStrategy.displayName}' is not supported")
      thrown.context should contain("requested_strategy" -> invalidStrategy.displayName)
      thrown.context should contain("supported_strategies" -> validStrategies.mkString(", "))
      
      // Test PreserveAsChunk mode
      val preserveConfig = preserveAsChunkConfig(invalidStrategy)
      val preserveResult = splitter.split(content, preserveConfig)
      preserveResult should have size 1
      preserveResult.head.label shouldBe "document"
      
      // Test DropDocument mode
      val dropConfig = dropDocumentConfig(invalidStrategy)
      val dropResult = splitter.split(content, dropConfig)
      dropResult shouldBe empty
      
      // Test TagAndPreserve mode
      val tagConfig = tagAndPreserveConfig(invalidStrategy, Map("test_id" -> "123"))
      val tagResult = splitter.split(content, tagConfig)
      tagResult should have size 1
      validateFailedChunk(
        tagResult.head,
        expectedLabel = "failed_document",
        expectedErrorType = Some("SplitException")
      )
      tagResult.head.metadata should contain("test_id" -> "123")
    }
    
    def testCorruptedContent[T <: MimeType](
      splitter: DocumentSplitter[T],
      content: FileContent[T],
      validStrategy: SplitStrategy,
      expectedErrorMessage: String = "corrupted"
    ): Unit = {
      // Test ThrowException mode
      val throwConfig = throwExceptionConfig(validStrategy)
      val thrown = intercept[SplitException] {
        splitter.split(content, throwConfig)
      }
      thrown.getMessage.toLowerCase should include(expectedErrorMessage)
      
      // Test PreserveAsChunk mode
      val preserveConfig = preserveAsChunkConfig(validStrategy)
      val preserveResult = splitter.split(content, preserveConfig)
      preserveResult should have size 1
      preserveResult.head.label shouldBe "document"
    }
    
    def testEmptyContent[T <: MimeType](
      splitter: DocumentSplitter[T],
      content: FileContent[T],
      validStrategy: SplitStrategy
    ): Unit = {
      // Test ThrowException mode
      val throwConfig = throwExceptionConfig(validStrategy)
      val thrown = intercept[SplitException] {
        splitter.split(content, throwConfig)
      }
      thrown.getMessage.toLowerCase should include("empty")
      
      // Test PreserveAsChunk mode
      val preserveConfig = preserveAsChunkConfig(validStrategy)
      val preserveResult = splitter.split(content, preserveConfig)
      preserveResult should have size 1
      preserveResult.head.label shouldBe "document"
    }
  }
}