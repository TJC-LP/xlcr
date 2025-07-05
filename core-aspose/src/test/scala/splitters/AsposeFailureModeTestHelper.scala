package com.tjclp.xlcr
package splitters

import org.scalatest.matchers.should.Matchers

/**
 * Helper utilities for testing failure modes in Aspose splitters. Extends the core
 * FailureModeTestHelper with Aspose-specific utilities.
 */
object AsposeFailureModeTestHelper extends Matchers {

  // Common invalid test data
  val invalidBytes: Array[Byte] = Array[Byte](0x00, 0x01, 0x02, 0x03)
  val emptyBytes: Array[Byte]   = Array.empty[Byte]

  // Invalid but more realistic data for different formats
  val invalidPdfBytes: Array[Byte] = "Not a PDF file".getBytes("UTF-8")
  val invalidZipBytes: Array[Byte] =
    "PK\u0000\u0000Invalid".getBytes("UTF-8") // Starts like ZIP but invalid
  val invalidExcelBytes: Array[Byte] = Array.fill(100)(0xff.toByte) // Random bytes
  val invalidWordBytes: Array[Byte]  = "This is not a Word document".getBytes("UTF-8")

  // Valid but empty files
  val emptyZipBytes: Array[Byte] = Array[Byte](
    0x50, 0x4b, 0x05, 0x06, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00
  )

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

  def tagAndPreserveConfig(
    strategy: SplitStrategy,
    context: Map[String, String] = Map.empty
  ): SplitConfig =
    SplitConfig(
      strategy = Some(strategy),
      failureMode = SplitFailureMode.TagAndPreserve,
      failureContext = context
    )
}
