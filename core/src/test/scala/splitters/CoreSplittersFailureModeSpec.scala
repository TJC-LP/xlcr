package com.tjclp.xlcr
package splitters

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import models.FileContent
import splitters.archive._
import splitters.email._
import splitters.excel._
import splitters.powerpoint._
import splitters.text._
import splitters.word._
import types.MimeType

class CoreSplittersFailureModeSpec extends AnyWordSpec with Matchers {

  import FailureModeTestHelper._

  val invalidBytes = Array[Byte](0x00, 0x01, 0x02, 0x03)
  val emptyBytes   = Array.empty[Byte]

  "CsvSplitter with failure handling" should {
    "handle invalid strategy with PreserveAsChunk mode" in {
      val content = FileContent("a,b,c\n1,2,3".getBytes("UTF-8"), MimeType.TextCsv)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Page),
        failureMode = SplitFailureMode.PreserveAsChunk
      )

      val result = CsvSplitter.split(content, cfg)
      result should have size 1
      result.head.label shouldBe "document"
      result.head.index shouldBe 0
      result.head.total shouldBe 1
    }

    "handle empty CSV with PreserveAsChunk mode" in {
      val content = FileContent(emptyBytes, MimeType.TextCsv)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Row),
        failureMode = SplitFailureMode.PreserveAsChunk
      )

      val result = CsvSplitter.split(content, cfg)
      result should have size 1
      result.head.label shouldBe "document"
    }

    "throw exception for empty CSV with ThrowException mode" in {
      val content = FileContent(emptyBytes, MimeType.TextCsv)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Row),
        failureMode = SplitFailureMode.ThrowException
      )

      // SplitFailureHandler wraps exceptions in SplitException
      assertThrows[SplitException] {
        CsvSplitter.split(content, cfg)
      }
    }
  }

  "OutlookMsgSplitter with failure handling" should {
    "handle invalid strategy with PreserveAsChunk mode" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndMsOutlook)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Page),
        failureMode = SplitFailureMode.PreserveAsChunk
      )

      val result = OutlookMsgSplitter.split(content, cfg)
      result should have size 1
      result.head.label shouldBe "document"
    }

    "handle corrupted MSG file with PreserveAsChunk mode" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndMsOutlook)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Attachment),
        failureMode = SplitFailureMode.PreserveAsChunk
      )

      val result = OutlookMsgSplitter.split(content, cfg)
      result should have size 1
      result.head.label shouldBe "document"
    }
  }

  "ArchiveEntrySplitter with failure handling" should {
    "handle invalid ZIP file with PreserveAsChunk mode" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationZip)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Embedded),
        failureMode = SplitFailureMode.PreserveAsChunk
      )

      val result = ArchiveEntrySplitter.split(content, cfg)
      result should have size 1
      result.head.label shouldBe "document"
    }

    "handle empty ZIP file with PreserveAsChunk mode" in {
      // Valid but empty ZIP file
      val emptyZip = Array[Byte](
        0x50, 0x4b, 0x05, 0x06, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00
      )
      val content = FileContent(emptyZip, MimeType.ApplicationZip)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Embedded),
        failureMode = SplitFailureMode.PreserveAsChunk
      )

      val result = ArchiveEntrySplitter.split(content, cfg)
      result should have size 1
      result.head.label shouldBe "document"
    }
  }

  "ExcelSheetSplitter with failure handling" should {
    "handle invalid strategy with PreserveAsChunk mode" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndMsExcel)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Page),
        failureMode = SplitFailureMode.PreserveAsChunk
      )

      val result = ExcelXlsSheetSplitter.split(content, cfg)
      result should have size 1
      result.head.label shouldBe "document"
    }

    "handle corrupted Excel file with PreserveAsChunk mode" in {
      val content =
        FileContent(invalidBytes, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Sheet),
        failureMode = SplitFailureMode.PreserveAsChunk
      )

      val result = ExcelXlsxSheetSplitter.split(content, cfg)
      result should have size 1
      result.head.label shouldBe "document"
    }
  }

  "PowerPointSlideSplitter with failure handling" should {
    "handle invalid strategy with PreserveAsChunk mode" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndMsPowerpoint)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Page),
        failureMode = SplitFailureMode.PreserveAsChunk
      )

      val result = PowerPointPptSlideSplitter.split(content, cfg)
      result should have size 1
      result.head.label shouldBe "document"
    }

    "handle corrupted PowerPoint file with PreserveAsChunk mode" in {
      val content =
        FileContent(invalidBytes, MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Slide),
        failureMode = SplitFailureMode.PreserveAsChunk
      )

      val result = PowerPointPptxSlideSplitter.split(content, cfg)
      result should have size 1
      result.head.label shouldBe "document"
    }
  }

  "WordHeadingSplitter with failure handling" should {
    "handle invalid strategy with PreserveAsChunk mode" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationMsWord)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Page),
        failureMode = SplitFailureMode.PreserveAsChunk
      )

      val result = WordDocHeadingSplitter.split(content, cfg)
      result should have size 1
      result.head.label shouldBe "document"
    }

    "handle corrupted Word file with PreserveAsChunk mode" in {
      val content =
        FileContent(invalidBytes, MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Heading),
        failureMode = SplitFailureMode.PreserveAsChunk
      )

      val result = WordDocxHeadingSplitter.split(content, cfg)
      result should have size 1
      result.head.label shouldBe "document"
    }
  }

  "DropDocument failure mode" should {
    "return empty sequence for failures" in {
      val content = FileContent(invalidBytes, MimeType.TextCsv)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Row),
        failureMode = SplitFailureMode.DropDocument
      )

      val result = CsvSplitter.split(content, cfg)
      result shouldBe empty
    }
  }

  "TagAndPreserve failure mode" should {
    "add error metadata to preserved chunk" in {
      val content = FileContent(invalidBytes, MimeType.TextCsv)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Row),
        failureMode = SplitFailureMode.TagAndPreserve
      )

      val result = CsvSplitter.split(content, cfg)
      result should have size 1
      result.head.metadata.get("split_status") should be(Some("failed"))
      result.head.metadata.get("error_message") should be(defined)
      // Check that error message exists and is non-empty
      result.head.metadata("error_message").nonEmpty shouldBe true
    }
  }

  "TextSplitter with ThrowException mode" should {
    "throw InvalidStrategyException for unsupported strategy" in {
      val content = FileContent("Hello World".getBytes("UTF-8"), MimeType.TextPlain)
      val cfg     = throwExceptionConfig(SplitStrategy.Sheet)

      val thrown = intercept[SplitException] {
        TextSplitter.split(content, cfg)
      }

      thrown.getMessage should include("Strategy 'sheet' is not supported")
      thrown.mimeType shouldBe "text/plain"
      thrown.context should contain("supported_strategies" -> "chunk, auto, paragraph")
    }

    "return empty sequence for empty text" in {
      // TextSplitter returns empty sequence for empty text, not an exception
      val content = FileContent(emptyBytes, MimeType.TextPlain)
      val cfg     = throwExceptionConfig(SplitStrategy.Chunk)

      val result = TextSplitter.split(content, cfg)
      result shouldBe empty
    }
  }

  "CsvSplitter with ThrowException mode" should {
    "throw InvalidStrategyException for unsupported strategy" in {
      val content = FileContent("a,b,c\n1,2,3".getBytes("UTF-8"), MimeType.TextCsv)
      val cfg     = throwExceptionConfig(SplitStrategy.Page)

      val thrown = intercept[SplitException] {
        CsvSplitter.split(content, cfg)
      }

      thrown.getMessage should include("Strategy 'page' is not supported")
      thrown.mimeType shouldBe "text/csv"
      thrown.context should contain("supported_strategies" -> "row, chunk, auto")
    }

    "throw EmptyDocumentException for empty CSV" in {
      val content = FileContent(emptyBytes, MimeType.TextCsv)
      val cfg     = throwExceptionConfig(SplitStrategy.Row)

      val thrown = intercept[SplitException] {
        CsvSplitter.split(content, cfg)
      }

      thrown.getMessage should include("Empty document")
      thrown.getMessage should include("CSV file contains only header or empty rows")
    }
  }

  "ExcelSheetSplitter with ThrowException mode" should {
    "throw InvalidStrategyException for XLS files" in {
      val content = FileContent(invalidExcelBytes, MimeType.ApplicationVndMsExcel)
      val cfg     = throwExceptionConfig(SplitStrategy.Page)

      val thrown = intercept[SplitException] {
        ExcelXlsSheetSplitter.split(content, cfg)
      }

      thrown.getMessage should include("Strategy 'page' is not supported")
      thrown.mimeType shouldBe "application/vnd.ms-excel"
      thrown.context should contain("supported_strategies" -> "sheet")
    }

    "throw InvalidStrategyException for XLSX files" in {
      val content =
        FileContent(invalidExcelBytes, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)
      val cfg = throwExceptionConfig(SplitStrategy.Row)

      val thrown = intercept[SplitException] {
        ExcelXlsxSheetSplitter.split(content, cfg)
      }

      thrown.getMessage should include("Strategy 'row' is not supported")
      thrown.mimeType shouldBe "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
      thrown.context should contain("supported_strategies" -> "sheet")
    }

    "throw exception for corrupted Excel files" in {
      val content = FileContent(invalidExcelBytes, MimeType.ApplicationVndMsExcel)
      val cfg     = throwExceptionConfig(SplitStrategy.Sheet)

      val thrown = intercept[SplitException] {
        ExcelXlsSheetSplitter.split(content, cfg)
      }

      thrown.getMessage.toLowerCase should (include("corrupted").or(include("unsupported")).or(
        include("invalid")
      ))
    }
  }

  "WordHeadingSplitter with ThrowException mode" should {
    "throw InvalidStrategyException for DOC files" in {
      val content = FileContent(invalidWordBytes, MimeType.ApplicationMsWord)
      val cfg     = throwExceptionConfig(SplitStrategy.Page)

      val thrown = intercept[SplitException] {
        WordDocHeadingSplitter.split(content, cfg)
      }

      thrown.getMessage should include("Strategy 'page' is not supported")
      thrown.mimeType shouldBe "application/msword"
      thrown.context should contain("supported_strategies" -> "heading")
    }

    "throw InvalidStrategyException for DOCX files" in {
      val content =
        FileContent(invalidWordBytes, MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument)
      val cfg = throwExceptionConfig(SplitStrategy.Sheet)

      val thrown = intercept[SplitException] {
        WordDocxHeadingSplitter.split(content, cfg)
      }

      thrown.getMessage should include("Strategy 'sheet' is not supported")
      thrown.mimeType shouldBe "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      thrown.context should contain("supported_strategies" -> "heading")
    }

    "throw exception for corrupted Word files" in {
      val content = FileContent(invalidWordBytes, MimeType.ApplicationMsWord)
      val cfg     = throwExceptionConfig(SplitStrategy.Heading)

      val thrown = intercept[SplitException] {
        WordDocHeadingSplitter.split(content, cfg)
      }

      thrown.getMessage.toLowerCase should (include("invalid").or(include("corrupted")).or(
        include("empty")
      ))
    }
  }

  "PowerPointSlideSplitter with ThrowException mode" should {
    "throw InvalidStrategyException for PPT files" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndMsPowerpoint)
      val cfg     = throwExceptionConfig(SplitStrategy.Page)

      val thrown = intercept[SplitException] {
        PowerPointPptSlideSplitter.split(content, cfg)
      }

      thrown.getMessage should include("Strategy 'page' is not supported")
      thrown.mimeType shouldBe "application/vnd.ms-powerpoint"
      thrown.context should contain("supported_strategies" -> "slide")
    }

    "throw InvalidStrategyException for PPTX files" in {
      val content =
        FileContent(invalidBytes, MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation)
      val cfg = throwExceptionConfig(SplitStrategy.Sheet)

      val thrown = intercept[SplitException] {
        PowerPointPptxSlideSplitter.split(content, cfg)
      }

      thrown.getMessage should include("Strategy 'sheet' is not supported")
      thrown.mimeType shouldBe "application/vnd.openxmlformats-officedocument.presentationml.presentation"
      thrown.context should contain("supported_strategies" -> "slide")
    }

    "throw exception for corrupted presentations" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndMsPowerpoint)
      val cfg     = throwExceptionConfig(SplitStrategy.Slide)

      val thrown = intercept[SplitException] {
        PowerPointPptSlideSplitter.split(content, cfg)
      }

      thrown.getMessage.toLowerCase should (include("invalid").or(include("corrupted")))
    }
  }

  "ArchiveEntrySplitter with ThrowException mode" should {
    "throw InvalidStrategyException for archives" in {
      val content = FileContent(emptyZipBytes, MimeType.ApplicationZip)
      val cfg     = throwExceptionConfig(SplitStrategy.Page)

      val thrown = intercept[SplitException] {
        ArchiveEntrySplitter.split(content, cfg)
      }

      thrown.getMessage should include("Strategy 'page' is not supported")
      thrown.mimeType shouldBe "application/zip"
      thrown.context should contain("supported_strategies" -> "embedded")
    }

    "throw CorruptedDocumentException for invalid ZIP" in {
      val corruptedZip = Array[Byte](0x50, 0x4b, 0x03, 0x04, 0xff.toByte, 0xff.toByte)
      val content      = FileContent(corruptedZip, MimeType.ApplicationZip)
      val cfg          = throwExceptionConfig(SplitStrategy.Embedded)

      val thrown = intercept[SplitException] {
        ArchiveEntrySplitter.split(content, cfg)
      }

      thrown.getMessage.toLowerCase should (include("corrupted").or(include("failed to read zip"))
        .or(include("empty document")))
    }

    "throw EmptyDocumentException for empty ZIP" in {
      val content = FileContent(emptyZipBytes, MimeType.ApplicationZip)
      val cfg     = throwExceptionConfig(SplitStrategy.Embedded)

      val thrown = intercept[SplitException] {
        ArchiveEntrySplitter.split(content, cfg)
      }

      thrown.getMessage should include("Empty document")
      thrown.getMessage should include("ZIP archive contains no valid entries")
    }
  }

  "ZipEntrySplitter with ThrowException mode" should {
    "throw InvalidStrategyException" in {
      val content = FileContent(emptyZipBytes, MimeType.ApplicationZip)
      val cfg     = throwExceptionConfig(SplitStrategy.Row)

      val thrown = intercept[SplitException] {
        ZipEntrySplitter.split(content, cfg)
      }

      thrown.getMessage should include("Strategy 'row' is not supported")
    }

    "throw CorruptedDocumentException for invalid ZIP" in {
      val corruptedZip = Array[Byte](0x50, 0x4b, 0x03, 0x04, 0xff.toByte, 0xff.toByte)
      val content      = FileContent(corruptedZip, MimeType.ApplicationZip)
      val cfg          = throwExceptionConfig(SplitStrategy.Embedded)

      val thrown = intercept[SplitException] {
        ZipEntrySplitter.split(content, cfg)
      }

      thrown.getMessage.toLowerCase should (include("corrupted").or(include("failed to read zip"))
        .or(include("empty document")))
    }
  }

  "EmailAttachmentSplitter with ThrowException mode" should {
    "throw InvalidStrategyException for emails" in {
      val content = FileContent("From: test@test.com\n\nBody".getBytes, MimeType.MessageRfc822)
      val cfg     = throwExceptionConfig(SplitStrategy.Page)

      val thrown = intercept[SplitException] {
        EmailAttachmentSplitter.split(content, cfg)
      }

      thrown.getMessage should include("Strategy 'page' is not supported")
      thrown.mimeType shouldBe "message/rfc822"
      thrown.context should contain("supported_strategies" -> "attachment")
    }

    "handle malformed emails" in {
      val content = FileContent(invalidBytes, MimeType.MessageRfc822)
      val cfg     = throwExceptionConfig(SplitStrategy.Attachment)

      // EmailAttachmentSplitter with malformed email might parse it as email with body
      try {
        val result = EmailAttachmentSplitter.split(content, cfg)
        result should not be empty
        result.exists(_.label == "body") shouldBe true
      } catch {
        case ex: SplitException =>
          ex.getMessage.toLowerCase should (include("failed").or(include("invalid")).or(
            include("error")
          ).or(include("empty document")).or(include("no body or attachments")))
      }
    }
  }

  "OutlookMsgSplitter with ThrowException mode" should {
    "throw InvalidStrategyException for MSG files" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndMsOutlook)
      val cfg     = throwExceptionConfig(SplitStrategy.Sheet)

      val thrown = intercept[SplitException] {
        OutlookMsgSplitter.split(content, cfg)
      }

      thrown.getMessage should include("Strategy 'sheet' is not supported")
      thrown.mimeType shouldBe "application/vnd.ms-outlook"
      thrown.context should contain("supported_strategies" -> "attachment")
    }

    "throw exception for corrupted MSG files" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndMsOutlook)
      val cfg     = throwExceptionConfig(SplitStrategy.Attachment)

      val thrown = intercept[SplitException] {
        OutlookMsgSplitter.split(content, cfg)
      }

      thrown.getMessage should (include("corrupted").or(include("Failed to parse")).or(
        include("IO error")
      ).or(include("Invalid header")))
    }
  }

  "OdsSheetSplitter with ThrowException mode" should {
    "throw InvalidStrategyException for ODS files" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndOasisOpendocumentSpreadsheet)
      val cfg     = throwExceptionConfig(SplitStrategy.Slide)

      val thrown = intercept[SplitException] {
        OdsSheetSplitter.split(content, cfg)
      }

      thrown.getMessage should include("Strategy 'slide' is not supported")
      thrown.mimeType shouldBe "application/vnd.oasis.opendocument.spreadsheet"
      thrown.context should contain("supported_strategies" -> "sheet")
    }

    "throw exception for corrupted ODS files" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndOasisOpendocumentSpreadsheet)
      val cfg     = throwExceptionConfig(SplitStrategy.Sheet)

      val thrown = intercept[SplitException] {
        OdsSheetSplitter.split(content, cfg)
      }

      thrown.getMessage.toLowerCase should (include("invalid").or(include("corrupted")).or(
        include("failed")
      ))
    }
  }

  "Exception context propagation in ThrowException mode" should {
    "preserve custom context through failure handler" in {
      val content = FileContent(emptyBytes, MimeType.TextCsv)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Row),
        failureMode = SplitFailureMode.ThrowException,
        failureContext = Map(
          "source"     -> "test-suite",
          "request_id" -> "12345",
          "user"       -> "test-user"
        )
      )

      val thrown = intercept[SplitException] {
        CsvSplitter.split(content, cfg)
      }

      thrown.context should contain("source" -> "test-suite")
      thrown.context should contain("request_id" -> "12345")
      thrown.context should contain("user" -> "test-user")
    }
  }

  "Multiple failure modes" should {
    "all handle the same error consistently" in {
      val content = FileContent(invalidBytes, MimeType.TextCsv)

      // ThrowException mode
      val throwConfig = throwExceptionConfig(SplitStrategy.Row)
      assertThrows[SplitException] {
        CsvSplitter.split(content, throwConfig)
      }

      // PreserveAsChunk mode
      val preserveConfig = preserveAsChunkConfig(SplitStrategy.Row)
      val preserveResult = CsvSplitter.split(content, preserveConfig)
      preserveResult should have size 1
      preserveResult.head.label shouldBe "document"

      // DropDocument mode
      val dropConfig = dropDocumentConfig(SplitStrategy.Row)
      val dropResult = CsvSplitter.split(content, dropConfig)
      dropResult shouldBe empty

      // TagAndPreserve mode
      val tagConfig = tagAndPreserveConfig(SplitStrategy.Row)
      val tagResult = CsvSplitter.split(content, tagConfig)
      tagResult should have size 1
      tagResult.head.isFailed shouldBe true
      tagResult.head.label shouldBe "failed_document"
    }
  }
}
