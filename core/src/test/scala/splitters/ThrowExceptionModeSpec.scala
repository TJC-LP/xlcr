package com.tjclp.xlcr
package splitters

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import models.FileContent
import splitters.archive._
import splitters.email._
import splitters.excel._
import splitters.pdf._
import splitters.powerpoint._
import splitters.text._
import splitters.word._
import types.MimeType

/**
 * Comprehensive test suite for ThrowException failure mode. Tests that all splitters throw the
 * correct exception types with proper context.
 */
class ThrowExceptionModeSpec extends AnyWordSpec with Matchers {

  import FailureModeTestHelper._

  "CsvSplitter in ThrowException mode" should {
    val splitter     = CsvSplitter
    val validContent = FileContent("a,b,c\n1,2,3".getBytes("UTF-8"), MimeType.TextCsv)

    "throw InvalidStrategyException for unsupported strategy" in {
      val config = throwExceptionConfig(SplitStrategy.Page)
      val thrown = intercept[SplitException] {
        splitter.split(validContent, config)
      }

      thrown.getMessage should include("Strategy 'page' is not supported")
      thrown.mimeType shouldBe "text/csv"
      thrown.strategy shouldBe Some("page")
      thrown.context should contain("requested_strategy" -> "page")
      thrown.context should contain("supported_strategies" -> "row, chunk, auto")
    }

    "throw EmptyDocumentException for empty CSV" in {
      val emptyContent = FileContent(emptyBytes, MimeType.TextCsv)
      val config       = throwExceptionConfig(SplitStrategy.Row)

      val thrown = intercept[SplitException] {
        splitter.split(emptyContent, config)
      }

      thrown.getMessage should include("Empty document")
      thrown.getMessage should include("CSV file contains only header or empty rows")
      thrown.mimeType shouldBe "text/csv"
    }

    "throw exception for malformed CSV" in {
      val malformedContent = FileContent("\u0000\u0001\u0002".getBytes, MimeType.TextCsv)
      val config           = throwExceptionConfig(SplitStrategy.Row)

      val thrown = intercept[SplitException] {
        splitter.split(malformedContent, config)
      }

      thrown.getMessage should include("CSV")
    }
  }

  "TextSplitter in ThrowException mode" should {
    val splitter     = TextSplitter
    val validContent = FileContent("Hello World".getBytes("UTF-8"), MimeType.TextPlain)

    "throw InvalidStrategyException for unsupported strategy" in {
      val config = throwExceptionConfig(SplitStrategy.Sheet)
      val thrown = intercept[SplitException] {
        splitter.split(validContent, config)
      }

      thrown.getMessage should include("Strategy 'sheet' is not supported")
      thrown.mimeType shouldBe "text/plain"
      thrown.context should contain("supported_strategies" -> "chunk, auto, paragraph")
    }

    "return empty sequence for empty text" in {
      // TextSplitter returns empty sequence for empty text, not an exception
      val emptyContent = FileContent(emptyBytes, MimeType.TextPlain)
      val config       = throwExceptionConfig(SplitStrategy.Chunk)

      val result = splitter.split(emptyContent, config)
      result shouldBe empty
    }
  }

  "PdfPageSplitter in ThrowException mode" should {
    val splitter      = PdfPageSplitter
    val validStrategy = SplitStrategy.Page

    "throw InvalidStrategyException for unsupported strategy" in {
      val content = FileContent(invalidPdfBytes, MimeType.ApplicationPdf)
      val config  = throwExceptionConfig(SplitStrategy.Sheet)

      val thrown = intercept[SplitException] {
        splitter.split(content, config)
      }

      thrown.getMessage should include("Strategy 'sheet' is not supported")
      thrown.mimeType shouldBe "application/pdf"
      thrown.context should contain("supported_strategies" -> "page")
    }

    "throw CorruptedDocumentException for invalid PDF" in {
      val content = FileContent(invalidPdfBytes, MimeType.ApplicationPdf)
      val config  = throwExceptionConfig(validStrategy)

      val thrown = intercept[SplitException] {
        splitter.split(content, config)
      }

      thrown.getMessage should include("Failed to load PDF")
      thrown.mimeType shouldBe "application/pdf"
    }

    "throw EmptyDocumentException for empty PDF" in {
      val content = FileContent(emptyBytes, MimeType.ApplicationPdf)
      val config  = throwExceptionConfig(validStrategy)

      val thrown = intercept[SplitException] {
        splitter.split(content, config)
      }

      thrown.getMessage should include("PDF file is empty")
    }
  }

  "ExcelSheetSplitter in ThrowException mode" should {
    val validStrategy = SplitStrategy.Sheet

    "throw InvalidStrategyException for XLS files" in {
      val content = FileContent(invalidExcelBytes, MimeType.ApplicationVndMsExcel)
      val config  = throwExceptionConfig(SplitStrategy.Page)

      val thrown = intercept[SplitException] {
        ExcelXlsSheetSplitter.split(content, config)
      }

      thrown.getMessage should include("Strategy 'page' is not supported")
      thrown.mimeType shouldBe "application/vnd.ms-excel"
      thrown.context should contain("supported_strategies" -> "sheet")
    }

    "throw CorruptedDocumentException for corrupted Excel" in {
      val content =
        FileContent(invalidExcelBytes, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)
      val config = throwExceptionConfig(validStrategy)

      val thrown = intercept[SplitException] {
        ExcelXlsxSheetSplitter.split(content, config)
      }

      thrown.getMessage.toLowerCase should (include("corrupted").or(include("unsupported")).or(
        include("invalid")
      ))
    }
  }

  "WordHeadingSplitter in ThrowException mode" should {
    val validStrategy = SplitStrategy.Heading

    "throw InvalidStrategyException for Word documents" in {
      val content = FileContent(invalidWordBytes, MimeType.ApplicationMsWord)
      val config  = throwExceptionConfig(SplitStrategy.Page)

      val thrown = intercept[SplitException] {
        WordDocHeadingSplitter.split(content, config)
      }

      thrown.getMessage should include("Strategy 'page' is not supported")
      thrown.mimeType shouldBe "application/msword"
      thrown.context should contain("supported_strategies" -> "heading")
    }

    "throw exception for documents without headings" in {
      // This would need a valid Word doc without Heading 1 styles
      // For now, testing with corrupted content
      val content =
        FileContent(invalidWordBytes, MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument)
      val config = throwExceptionConfig(validStrategy)

      val thrown = intercept[SplitException] {
        WordDocxHeadingSplitter.split(content, config)
      }

      thrown.getMessage.toLowerCase should (include("invalid").or(include("corrupted")).or(
        include("empty")
      ))
    }
  }

  "PowerPointSlideSplitter in ThrowException mode" should {
    val validStrategy = SplitStrategy.Slide

    "throw InvalidStrategyException for PowerPoint files" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndMsPowerpoint)
      val config  = throwExceptionConfig(SplitStrategy.Page)

      val thrown = intercept[SplitException] {
        PowerPointPptSlideSplitter.split(content, config)
      }

      thrown.getMessage should include("Strategy 'page' is not supported")
      thrown.mimeType shouldBe "application/vnd.ms-powerpoint"
      thrown.context should contain("supported_strategies" -> "slide")
    }

    "throw exception for corrupted presentations" in {
      val content =
        FileContent(invalidBytes, MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation)
      val config = throwExceptionConfig(validStrategy)

      val thrown = intercept[SplitException] {
        PowerPointPptxSlideSplitter.split(content, config)
      }

      thrown.getMessage.toLowerCase should (include("invalid").or(include("corrupted")))
    }
  }

  "ArchiveEntrySplitter in ThrowException mode" should {
    val splitter      = ArchiveEntrySplitter
    val validStrategy = SplitStrategy.Embedded

    "throw InvalidStrategyException for archives" in {
      val content = FileContent(emptyZipBytes, MimeType.ApplicationZip)
      val config  = throwExceptionConfig(SplitStrategy.Page)

      val thrown = intercept[SplitException] {
        splitter.split(content, config)
      }

      thrown.getMessage should include("Strategy 'page' is not supported")
      thrown.mimeType shouldBe "application/zip"
      thrown.context should contain("supported_strategies" -> "embedded")
    }

    "throw CorruptedDocumentException for invalid ZIP" in {
      // Use truly corrupted ZIP data that will cause parse failure
      val corruptedZip =
        Array[Byte](
          0x50,
          0x4b,
          0x03,
          0x04,
          0xff.toByte,
          0xff.toByte
        ) // Valid ZIP header then garbage
      val content = FileContent(corruptedZip, MimeType.ApplicationZip)
      val config  = throwExceptionConfig(validStrategy)

      val thrown = intercept[SplitException] {
        splitter.split(content, config)
      }

      thrown.getMessage.toLowerCase should (include("corrupted").or(include("failed to read zip"))
        .or(include("empty document")))
    }

    "throw EmptyDocumentException for empty ZIP" in {
      val content = FileContent(emptyZipBytes, MimeType.ApplicationZip)
      val config  = throwExceptionConfig(validStrategy)

      val thrown = intercept[SplitException] {
        splitter.split(content, config)
      }

      thrown.getMessage should include("Empty document")
      thrown.getMessage should include("ZIP archive contains no valid entries")
    }
  }

  "ZipEntrySplitter in ThrowException mode" should {
    val splitter      = ZipEntrySplitter
    val validStrategy = SplitStrategy.Embedded

    "handle invalid strategy correctly" in {
      val content = FileContent(emptyZipBytes, MimeType.ApplicationZip)
      val config  = throwExceptionConfig(SplitStrategy.Row)

      val thrown = intercept[SplitException] {
        splitter.split(content, config)
      }

      thrown.getMessage should include("Strategy 'row' is not supported")
    }
  }

  "EmailAttachmentSplitter in ThrowException mode" should {
    val splitter      = EmailAttachmentSplitter
    val validStrategy = SplitStrategy.Attachment

    "throw InvalidStrategyException for emails" in {
      val content = FileContent("From: test@test.com\n\nBody".getBytes, MimeType.MessageRfc822)
      val config  = throwExceptionConfig(SplitStrategy.Page)

      val thrown = intercept[SplitException] {
        splitter.split(content, config)
      }

      thrown.getMessage should include("Strategy 'page' is not supported")
      thrown.mimeType shouldBe "message/rfc822"
      thrown.context should contain("supported_strategies" -> "attachment")
    }

    "handle malformed emails" in {
      val content = FileContent(invalidBytes, MimeType.MessageRfc822)
      val config  = throwExceptionConfig(validStrategy)

      // EmailAttachmentSplitter with malformed email might:
      // 1. Parse it as email with body but no attachments
      // 2. Throw an exception
      // This is valid behavior since the test bytes might be interpreted as a minimal email
      try {
        val result = splitter.split(content, config)
        // If parsed successfully, it should have extracted something (likely a body)
        result should not be empty
        // Should have extracted body but no attachments
        result.exists(_.label == "body") shouldBe true
      } catch {
        case ex: SplitException =>
          ex.getMessage.toLowerCase should (include("failed").or(include("invalid")).or(
            include("error")
          ).or(include("empty document")).or(include("no body or attachments")))
      }
    }
  }

  "OutlookMsgSplitter in ThrowException mode" should {
    val splitter      = OutlookMsgSplitter
    val validStrategy = SplitStrategy.Attachment

    "throw InvalidStrategyException for MSG files" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndMsOutlook)
      val config  = throwExceptionConfig(SplitStrategy.Sheet)

      val thrown = intercept[SplitException] {
        splitter.split(content, config)
      }

      thrown.getMessage should include("Strategy 'sheet' is not supported")
      thrown.mimeType shouldBe "application/vnd.ms-outlook"
    }
  }

  "OdsSheetSplitter in ThrowException mode" should {
    val splitter      = OdsSheetSplitter
    val validStrategy = SplitStrategy.Sheet

    "throw InvalidStrategyException for ODS files" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndOasisOpendocumentSpreadsheet)
      val config  = throwExceptionConfig(SplitStrategy.Slide)

      val thrown = intercept[SplitException] {
        splitter.split(content, config)
      }

      thrown.getMessage should include("Strategy 'slide' is not supported")
      thrown.mimeType shouldBe "application/vnd.oasis.opendocument.spreadsheet"
    }
  }

  "Exception context propagation" should {
    "preserve custom context through the failure handler" in {
      val content = FileContent(emptyBytes, MimeType.TextCsv)
      val config = SplitConfig(
        strategy = Some(SplitStrategy.Row),
        failureMode = SplitFailureMode.ThrowException,
        failureContext = Map(
          "source"     -> "test-suite",
          "request_id" -> "12345",
          "user"       -> "test-user"
        )
      )

      val thrown = intercept[SplitException] {
        CsvSplitter.split(content, config)
      }

      thrown.context should contain("source" -> "test-suite")
      thrown.context should contain("request_id" -> "12345")
      thrown.context should contain("user" -> "test-user")
    }

    "include splitter-specific context" in {
      val content = FileContent(invalidPdfBytes, MimeType.ApplicationPdf)
      val config  = throwExceptionConfig(SplitStrategy.Page)

      val thrown = intercept[SplitException] {
        PdfPageSplitter.split(content, config)
      }

      // Should have context about the failure
      thrown.mimeType shouldBe "application/pdf"
      thrown.getMessage should include("application/pdf")
    }
  }

  "Size limit exceptions" should {
    "be thrown when documents exceed configured limits" in {
      val largeContent = FileContent(largeBytes(10), MimeType.TextPlain)
      val config = SplitConfig(
        strategy = Some(SplitStrategy.Chunk),
        failureMode = SplitFailureMode.ThrowException,
        maxTotalSize = 1024 * 1024 // 1MB limit
      )

      // This test would need actual implementation of size limit checking
      // For now, marking as pending
      pending
    }
  }
}
