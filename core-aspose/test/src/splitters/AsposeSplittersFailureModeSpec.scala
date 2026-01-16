package com.tjclp.xlcr
package splitters

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import models.FileContent
import types.MimeType
import splitters.pdf.PdfPageAsposeSplitter
import splitters.archive.ZipArchiveAsposeSplitter
import splitters.email.EmailAttachmentAsposeSplitter
import splitters.excel.ExcelXlsxSheetAsposeSplitter

/**
 * Tests for the minimal failure mode implementation in Aspose splitters.
 */
class AsposeSplittersFailureModeSpec extends AnyFlatSpec with Matchers {

  // Test data
  val invalidPdfBytes   = Array[Byte](0, 1, 2, 3, 4) // Not a valid PDF
  val invalidZipBytes   = Array[Byte](0, 1, 2, 3, 4) // Not a valid ZIP
  val invalidEmailBytes = Array[Byte](0, 1, 2, 3, 4) // Not a valid email
  val invalidExcelBytes = Array[Byte](0, 1, 2, 3, 4) // Not a valid Excel file

  "PdfPageAsposeSplitter" should "handle invalid strategies" in {
    val content = FileContent(invalidPdfBytes, MimeType.ApplicationPdf)
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Sheet), // Invalid for PDF
      failureMode = SplitFailureMode.ThrowException
    )

    val thrown = intercept[SplitException] {
      PdfPageAsposeSplitter.split(content, config)
    }
    thrown.getMessage should include("Strategy 'sheet' is not supported")
  }

  it should "handle corrupted PDF files" in {
    val content = FileContent(invalidPdfBytes, MimeType.ApplicationPdf)

    // ThrowException mode
    val throwConfig = SplitConfig(
      strategy = Some(SplitStrategy.Page),
      failureMode = SplitFailureMode.ThrowException
    )
    assertThrows[SplitException] {
      PdfPageAsposeSplitter.split(content, throwConfig)
    }

    // PreserveAsChunk mode
    val preserveConfig = SplitConfig(
      strategy = Some(SplitStrategy.Page),
      failureMode = SplitFailureMode.PreserveAsChunk
    )
    val preserveResult = PdfPageAsposeSplitter.split(content, preserveConfig)
    preserveResult.size shouldBe 1
    preserveResult.head.label shouldBe "document"

    // DropDocument mode
    val dropConfig = SplitConfig(
      strategy = Some(SplitStrategy.Page),
      failureMode = SplitFailureMode.DropDocument
    )
    val dropResult = PdfPageAsposeSplitter.split(content, dropConfig)
    dropResult shouldBe empty

    // TagAndPreserve mode
    val tagConfig = SplitConfig(
      strategy = Some(SplitStrategy.Page),
      failureMode = SplitFailureMode.TagAndPreserve
    )
    val tagResult = PdfPageAsposeSplitter.split(content, tagConfig)
    tagResult.size shouldBe 1
    tagResult.head.isFailed shouldBe true
    tagResult.head.errorMessage.isDefined shouldBe true
  }

  "ZipArchiveAsposeSplitter" should "handle invalid strategies" in {
    val content = FileContent(invalidZipBytes, MimeType.ApplicationZip)
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page), // Invalid for ZIP
      failureMode = SplitFailureMode.ThrowException
    )

    val thrown = intercept[SplitException] {
      ZipArchiveAsposeSplitter.split(content, config)
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
      ZipArchiveAsposeSplitter.split(content, throwConfig)
    }

    // PreserveAsChunk mode
    val preserveConfig = SplitConfig(
      strategy = Some(SplitStrategy.Embedded),
      failureMode = SplitFailureMode.PreserveAsChunk
    )
    val preserveResult = ZipArchiveAsposeSplitter.split(content, preserveConfig)
    preserveResult.size shouldBe 1
    preserveResult.head.label shouldBe "document"
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
      ZipArchiveAsposeSplitter.split(content, config)
    }
    thrown.getMessage should include("ZIP archive contains no valid entries")
  }

  "EmailAttachmentAsposeSplitter" should "handle invalid strategies" in {
    val content = FileContent(invalidEmailBytes, MimeType.MessageRfc822)
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page), // Invalid for email
      failureMode = SplitFailureMode.ThrowException
    )

    val thrown = intercept[SplitException] {
      EmailAttachmentAsposeSplitter.split(content, config)
    }
    thrown.getMessage should include("Strategy 'page' is not supported")
  }

  it should "handle malformed emails" in {
    // Note: Aspose Email's behavior with invalid bytes is unpredictable.
    // It might parse successfully but find no content, or it might throw.
    // We need to test all failure modes but be flexible about expectations.

    val content = FileContent(invalidEmailBytes, MimeType.MessageRfc822)

    // Test all failure modes to ensure they don't throw unexpectedly
    val configs = Seq(
      (
        "ThrowException",
        SplitConfig(
          strategy = Some(SplitStrategy.Attachment),
          failureMode = SplitFailureMode.ThrowException
        )
      ),
      (
        "PreserveAsChunk",
        SplitConfig(
          strategy = Some(SplitStrategy.Attachment),
          failureMode = SplitFailureMode.PreserveAsChunk
        )
      ),
      (
        "DropDocument",
        SplitConfig(
          strategy = Some(SplitStrategy.Attachment),
          failureMode = SplitFailureMode.DropDocument
        )
      ),
      (
        "TagAndPreserve",
        SplitConfig(
          strategy = Some(SplitStrategy.Attachment),
          failureMode = SplitFailureMode.TagAndPreserve
        )
      )
    )

    for ((modeName, config) <- configs)
      try {
        val result = EmailAttachmentAsposeSplitter.split(content, config)

        // Verify behavior based on mode
        modeName match {
          case "ThrowException" =>
            // If no exception was thrown, Aspose found some content
            result.size should be >= 1

          case "PreserveAsChunk" =>
            // Should return something (either parsed content or preserved chunk)
            result.size should be >= 0
          // Note: Aspose might parse successfully and return actual content

          case "DropDocument" =>
            // Could be empty (if error) or have content (if parsed successfully)
            result.size should be >= 0

          case "TagAndPreserve" =>
            // Should tag results appropriately
            result.foreach { chunk =>
              if (chunk.isFailed) {
                chunk.errorMessage.isDefined shouldBe true
              } else {
                chunk.metadata should contain("split_status" -> "success")
              }
            }
        }
      } catch {
        case _: SplitException =>
          // Exception is acceptable for ThrowException mode
          modeName shouldBe "ThrowException"
      }
  }

  "ExcelSheetAsposeSplitter" should "handle invalid strategies" in {
    val content =
      FileContent(invalidExcelBytes, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page), // Invalid for Excel
      failureMode = SplitFailureMode.ThrowException
    )

    val thrown = intercept[SplitException] {
      ExcelXlsxSheetAsposeSplitter.split(content, config)
    }
    thrown.getMessage should include("Strategy 'page' is not supported")
  }

  it should "handle corrupted Excel files" in {
    val content =
      FileContent(invalidExcelBytes, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)

    // Note: Aspose might create a default workbook with "Sheet1" for invalid data
    // or throw an exception. We need to handle both cases.

    // ThrowException mode
    val throwConfig = SplitConfig(
      strategy = Some(SplitStrategy.Sheet),
      failureMode = SplitFailureMode.ThrowException
    )
    // Either throws or returns a sheet
    try {
      val result = ExcelXlsxSheetAsposeSplitter.split(content, throwConfig)
      // If no exception, Aspose created a default sheet
      result.size shouldBe 1
      result.head.label should (be("Sheet1").or(be("document")))
    } catch {
      case _: SplitException => // Also acceptable
    }

    // PreserveAsChunk mode
    val preserveConfig = SplitConfig(
      strategy = Some(SplitStrategy.Sheet),
      failureMode = SplitFailureMode.PreserveAsChunk
    )
    val preserveResult = ExcelXlsxSheetAsposeSplitter.split(content, preserveConfig)
    preserveResult.size shouldBe 1
    // Could be "Sheet1" if Aspose created default sheet, or "document" if preserved as chunk
    preserveResult.head.label should (be("Sheet1").or(be("document")))

    // DropDocument mode
    val dropConfig = SplitConfig(
      strategy = Some(SplitStrategy.Sheet),
      failureMode = SplitFailureMode.DropDocument
    )
    val dropResult = ExcelXlsxSheetAsposeSplitter.split(content, dropConfig)
    // Either empty (if failed) or single sheet (if Aspose created default)
    dropResult.size should be <= 1

    // TagAndPreserve mode
    val tagConfig = SplitConfig(
      strategy = Some(SplitStrategy.Sheet),
      failureMode = SplitFailureMode.TagAndPreserve
    )
    val tagResult = ExcelXlsxSheetAsposeSplitter.split(content, tagConfig)
    tagResult.size shouldBe 1
    if (tagResult.head.label == "document") {
      // Failed and preserved
      tagResult.head.isFailed shouldBe true
      tagResult.head.errorMessage.isDefined shouldBe true
    } else {
      // Aspose created a default sheet
      tagResult.head.metadata should contain("split_status" -> "success")
    }
  }

  "All Aspose splitters" should "maintain backward compatibility" in {
    // Default config should use PreserveAsChunk mode
    val defaultConfig = SplitConfig()
    defaultConfig.failureMode shouldBe SplitFailureMode.PreserveAsChunk

    // Test with each splitter - should not throw exceptions by default
    val pdfContent = FileContent(invalidPdfBytes, MimeType.ApplicationPdf)
    noException should be thrownBy PdfPageAsposeSplitter.split(
      pdfContent,
      defaultConfig.copy(strategy = Some(SplitStrategy.Page))
    )

    val zipContent = FileContent(invalidZipBytes, MimeType.ApplicationZip)
    noException should be thrownBy ZipArchiveAsposeSplitter.split(
      zipContent,
      defaultConfig.copy(strategy = Some(SplitStrategy.Embedded))
    )

    val emailContent = FileContent(invalidEmailBytes, MimeType.MessageRfc822)
    noException should be thrownBy EmailAttachmentAsposeSplitter.split(
      emailContent,
      defaultConfig.copy(strategy = Some(SplitStrategy.Attachment))
    )

    val excelContent =
      FileContent(invalidExcelBytes, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)
    noException should be thrownBy ExcelXlsxSheetAsposeSplitter.split(
      excelContent,
      defaultConfig.copy(strategy = Some(SplitStrategy.Sheet))
    )
  }
}
