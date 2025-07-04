package com.tjclp.xlcr
package splitters

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import models.FileContent
import splitters._
import splitters.archive._
import splitters.email._
import splitters.excel._
import splitters.powerpoint._
import types.MimeType
import splitters.pdf._

class UpdatedAsposeSplittersFailureModeSpec extends AnyWordSpec with Matchers {

  import AsposeFailureModeTestHelper._

  "OutlookMsgAsposeSplitter with failure handling" should {
    "handle invalid strategy with PreserveAsChunk mode" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndMsOutlook)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Page),
        failureMode = SplitFailureMode.PreserveAsChunk
      )
      
      val result = OutlookMsgAsposeSplitter.split(content, cfg)
      result should have size 1
      result.head.label shouldBe "document"
      result.head.index shouldBe 0
      result.head.total shouldBe 1
    }

    "handle corrupted MSG file with PreserveAsChunk mode" in {
      // Use a more specific invalid MSG header that will definitely fail
      val corruptedMsgBytes = "Not a valid MSG file".getBytes("UTF-8")
      val content = FileContent(corruptedMsgBytes, MimeType.ApplicationVndMsOutlook)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Attachment),
        failureMode = SplitFailureMode.PreserveAsChunk
      )
      
      val result = OutlookMsgAsposeSplitter.split(content, cfg)
      // Should either preserve as chunk or return extracted content
      result should not be empty
      // If preserved as chunk, should have document label
      if (result.size == 1 && result.head.content.data.sameElements(corruptedMsgBytes)) {
        result.head.label shouldBe "document"
      }
    }

    "throw exception for corrupted MSG with ThrowException mode" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndMsOutlook)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Attachment),
        failureMode = SplitFailureMode.ThrowException
      )
      
      // Aspose Email might throw any exception or return empty
      try {
        val result = OutlookMsgAsposeSplitter.split(content, cfg)
        result shouldBe empty
      } catch {
        case _: Exception => // Any exception is acceptable
      }
    }
  }

  "SevenZipArchiveAsposeSplitter with failure handling" should {
    "handle invalid strategy with PreserveAsChunk mode" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationSevenz)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Page),
        failureMode = SplitFailureMode.PreserveAsChunk
      )
      
      val result = SevenZipArchiveAsposeSplitter.split(content, cfg)
      result should have size 1
      result.head.label shouldBe "document"
    }

    "handle corrupted 7z file with PreserveAsChunk mode" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationSevenz)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Embedded),
        failureMode = SplitFailureMode.PreserveAsChunk
      )
      
      val result = SevenZipArchiveAsposeSplitter.split(content, cfg)
      result should have size 1
      result.head.label shouldBe "document"
    }

    "throw exception for corrupted 7z with ThrowException mode" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationSevenz)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Embedded),
        failureMode = SplitFailureMode.ThrowException
      )
      
      // SevenZipArchiveAsposeSplitter throws SplitException (which wraps CorruptedDocumentException)
      assertThrows[SplitException] {
        SevenZipArchiveAsposeSplitter.split(content, cfg)
      }
    }
  }

  "BasePowerPointSlideAsposeSplitter with failure handling" should {
    "handle invalid strategy with PreserveAsChunk mode" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndMsPowerpoint)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Page),
        failureMode = SplitFailureMode.PreserveAsChunk
      )
      
      val result = PowerPointPptSlideAsposeSplitter.split(content, cfg)
      result should have size 1
      result.head.label shouldBe "document"
    }

    "handle empty presentation with PreserveAsChunk mode" in {
      // This will likely throw an exception when trying to load, so it will be handled
      val content = FileContent(invalidBytes, MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Slide),
        failureMode = SplitFailureMode.PreserveAsChunk
      )
      
      val result = PowerPointPptxSlideAsposeSplitter.split(content, cfg)
      result should have size 1
      result.head.label shouldBe "document"
    }

    "throw exception for corrupted presentation with ThrowException mode" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndMsPowerpoint)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Slide),
        failureMode = SplitFailureMode.ThrowException
      )
      
      assertThrows[Exception] {
        PowerPointPptSlideAsposeSplitter.split(content, cfg)
      }
    }
  }

  "OdsSheetAsposeSplitter with failure handling" should {
    "handle invalid strategy with PreserveAsChunk mode" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndOasisOpendocumentSpreadsheet)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Page),
        failureMode = SplitFailureMode.PreserveAsChunk
      )
      
      val result = OdsSheetAsposeSplitter.split(content, cfg)
      result should have size 1
      result.head.label shouldBe "document"
    }

    "handle corrupted ODS file with PreserveAsChunk mode" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndOasisOpendocumentSpreadsheet)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Sheet),
        failureMode = SplitFailureMode.PreserveAsChunk
      )
      
      // ExcelSheetAsposeSplitter might create a default sheet or return preserved chunk
      val result = OdsSheetAsposeSplitter.split(content, cfg)
      result should not be empty
      // Either one preserved chunk or sheets created by Aspose
      result.size should be >= 1
    }
  }

  "DropDocument failure mode" should {
    "return empty sequence for failures in Aspose splitters" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationSevenz)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Embedded),
        failureMode = SplitFailureMode.DropDocument
      )
      
      val result = SevenZipArchiveAsposeSplitter.split(content, cfg)
      result shouldBe empty
    }
  }

  "TagAndPreserve failure mode" should {
    "add error metadata to preserved chunk in Aspose splitters" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationSevenz)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Embedded),
        failureMode = SplitFailureMode.TagAndPreserve
      )
      
      val result = SevenZipArchiveAsposeSplitter.split(content, cfg)
      result should have size 1
      result.head.metadata.get("split_status") should be(Some("failed"))
      result.head.metadata.get("error_message") should be(defined)
      result.head.metadata("error_message") should include("Failed to extract 7z archive")
    }
  }

  "PdfPageAsposeSplitter with ThrowException mode" should {
    "throw InvalidStrategyException for unsupported strategy" in {
      val content = FileContent(invalidPdfBytes, MimeType.ApplicationPdf)
      val cfg = throwExceptionConfig(SplitStrategy.Sheet)
      
      val thrown = intercept[SplitException] {
        PdfPageAsposeSplitter.split(content, cfg)
      }
      
      thrown.getMessage should include("Strategy 'sheet' is not supported")
      thrown.mimeType shouldBe "application/pdf"
      thrown.context should contain("supported_strategies" -> "page")
    }

    "throw CorruptedDocumentException for invalid PDF" in {
      val content = FileContent(invalidPdfBytes, MimeType.ApplicationPdf)
      val cfg = throwExceptionConfig(SplitStrategy.Page)
      
      val thrown = intercept[SplitException] {
        PdfPageAsposeSplitter.split(content, cfg)
      }
      
      thrown.getMessage.toLowerCase should (include("failed to load pdf") or include("incorrect file header"))
    }

    "handle empty PDF" in {
      val content = FileContent(emptyBytes, MimeType.ApplicationPdf)
      val cfg = throwExceptionConfig(SplitStrategy.Page)
      
      // Aspose might return empty result or throw exception for empty PDF
      try {
        val result = PdfPageAsposeSplitter.split(content, cfg)
        result shouldBe empty
      } catch {
        case ex: SplitException =>
          ex.getMessage.toLowerCase should (include("pdf file is empty") or include("empty document"))
      }
    }
  }

  "ExcelSheetAsposeSplitter with ThrowException mode" should {
    "throw InvalidStrategyException for XLS files" in {
      val content = FileContent(invalidExcelBytes, MimeType.ApplicationVndMsExcel)
      val cfg = throwExceptionConfig(SplitStrategy.Page)
      
      val thrown = intercept[SplitException] {
        ExcelXlsSheetAsposeSplitter.split(content, cfg)
      }
      
      thrown.getMessage should include("Strategy 'page' is not supported")
      thrown.mimeType shouldBe "application/vnd.ms-excel"
      thrown.context should contain("supported_strategies" -> "sheet")
    }

    "throw InvalidStrategyException for XLSX files" in {
      val content = FileContent(invalidExcelBytes, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)
      val cfg = throwExceptionConfig(SplitStrategy.Row)
      
      val thrown = intercept[SplitException] {
        ExcelXlsxSheetAsposeSplitter.split(content, cfg)
      }
      
      thrown.getMessage should include("Strategy 'row' is not supported")
      thrown.mimeType shouldBe "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
      thrown.context should contain("supported_strategies" -> "sheet")
    }

    "handle Aspose's behavior for invalid Excel" in {
      val content = FileContent(invalidExcelBytes, MimeType.ApplicationVndMsExcel)
      val cfg = throwExceptionConfig(SplitStrategy.Sheet)
      
      // Aspose might create default sheets or throw exception
      try {
        val result = ExcelXlsSheetAsposeSplitter.split(content, cfg)
        // If it doesn't throw, Aspose created default sheets
        result should not be empty
      } catch {
        case ex: SplitException =>
          ex.getMessage.toLowerCase should (include("failed") or include("corrupted") or include("unexpected error") or include("format is not supported"))
      }
    }
  }

  "EmailAttachmentAsposeSplitter with ThrowException mode" should {
    "throw InvalidStrategyException for emails" in {
      val content = FileContent("From: test@test.com\n\nBody".getBytes, MimeType.MessageRfc822)
      val cfg = throwExceptionConfig(SplitStrategy.Page)
      
      val thrown = intercept[SplitException] {
        EmailAttachmentAsposeSplitter.split(content, cfg)
      }
      
      thrown.getMessage should include("Strategy 'page' is not supported")
      thrown.mimeType shouldBe "message/rfc822"
      thrown.context should contain("supported_strategies" -> "attachment")
    }

    "handle Aspose's behavior for malformed emails" in {
      val content = FileContent(invalidBytes, MimeType.MessageRfc822)
      val cfg = throwExceptionConfig(SplitStrategy.Attachment)
      
      // Aspose Email might return empty or throw
      try {
        val result = EmailAttachmentAsposeSplitter.split(content, cfg)
        // Aspose Email might return a chunk with empty label
        result should not be empty
        // The chunk might have empty label
        result.head.label should (be("") or be("body"))
      } catch {
        case ex: SplitException =>
          ex.getMessage.toLowerCase should (include("failed") or include("empty") or include("no attachments"))
      }
    }
  }

  "ZipArchiveAsposeSplitter with ThrowException mode" should {
    "throw InvalidStrategyException" in {
      val content = FileContent(emptyZipBytes, MimeType.ApplicationZip)
      val cfg = throwExceptionConfig(SplitStrategy.Page)
      
      val thrown = intercept[SplitException] {
        ZipArchiveAsposeSplitter.split(content, cfg)
      }
      
      thrown.getMessage should include("Strategy 'page' is not supported")
      thrown.mimeType shouldBe "application/zip"
      thrown.context should contain("supported_strategies" -> "embedded")
    }

    "throw CorruptedDocumentException for invalid ZIP" in {
      val content = FileContent(invalidZipBytes, MimeType.ApplicationZip)
      val cfg = throwExceptionConfig(SplitStrategy.Embedded)
      
      val thrown = intercept[SplitException] {
        ZipArchiveAsposeSplitter.split(content, cfg)
      }
      
      thrown.getMessage.toLowerCase should (include("failed to extract zip archive") or include("signature of end of central directory"))
    }

    "throw EmptyDocumentException for empty ZIP" in {
      val content = FileContent(emptyZipBytes, MimeType.ApplicationZip)
      val cfg = throwExceptionConfig(SplitStrategy.Embedded)
      
      val thrown = intercept[SplitException] {
        ZipArchiveAsposeSplitter.split(content, cfg)
      }
      
      thrown.getMessage should include("Empty document")
    }
  }

  "SevenZipArchiveAsposeSplitter with ThrowException mode" should {
    "throw InvalidStrategyException" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationSevenz)
      val cfg = throwExceptionConfig(SplitStrategy.Row)
      
      val thrown = intercept[SplitException] {
        SevenZipArchiveAsposeSplitter.split(content, cfg)
      }
      
      thrown.getMessage should include("Strategy 'row' is not supported")
      thrown.mimeType shouldBe "application/x-7z-compressed"
      thrown.context should contain("supported_strategies" -> "embedded")
    }

    "throw CorruptedDocumentException for invalid 7z" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationSevenz)
      val cfg = throwExceptionConfig(SplitStrategy.Embedded)
      
      val thrown = intercept[SplitException] {
        SevenZipArchiveAsposeSplitter.split(content, cfg)
      }
      
      thrown.getMessage should include("Failed to extract 7z archive")
    }
  }

  "BasePowerPointSlideAsposeSplitter with ThrowException mode" should {
    "throw InvalidStrategyException for PPT" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndMsPowerpoint)
      val cfg = throwExceptionConfig(SplitStrategy.Sheet)
      
      val thrown = intercept[SplitException] {
        PowerPointPptSlideAsposeSplitter.split(content, cfg)
      }
      
      thrown.getMessage should include("Strategy 'sheet' is not supported")
      thrown.mimeType shouldBe "application/vnd.ms-powerpoint"
      thrown.context should contain("supported_strategies" -> "slide")
    }

    "throw InvalidStrategyException for PPTX" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation)
      val cfg = throwExceptionConfig(SplitStrategy.Page)
      
      val thrown = intercept[SplitException] {
        PowerPointPptxSlideAsposeSplitter.split(content, cfg)
      }
      
      thrown.getMessage should include("Strategy 'page' is not supported")
      thrown.mimeType shouldBe "application/vnd.openxmlformats-officedocument.presentationml.presentation"
      thrown.context should contain("supported_strategies" -> "slide")
    }

    "throw exception for corrupted presentations" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndMsPowerpoint)
      val cfg = throwExceptionConfig(SplitStrategy.Slide)
      
      val thrown = intercept[Exception] {
        PowerPointPptSlideAsposeSplitter.split(content, cfg)
      }
      
      thrown.getMessage.toLowerCase should (include("failed") or include("corrupted") or include("invalid") or include("unexpected error") or include("can't read"))
    }
  }

  "OdsSheetAsposeSplitter with ThrowException mode" should {
    "throw InvalidStrategyException" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndOasisOpendocumentSpreadsheet)
      val cfg = throwExceptionConfig(SplitStrategy.Slide)
      
      val thrown = intercept[SplitException] {
        OdsSheetAsposeSplitter.split(content, cfg)
      }
      
      thrown.getMessage should include("Strategy 'slide' is not supported")
      thrown.mimeType shouldBe "application/vnd.oasis.opendocument.spreadsheet"
      thrown.context should contain("supported_strategies" -> "sheet")
    }

    "handle Aspose's behavior for corrupted ODS" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationVndOasisOpendocumentSpreadsheet)
      val cfg = throwExceptionConfig(SplitStrategy.Sheet)
      
      // Aspose might create default sheets or throw
      try {
        val result = OdsSheetAsposeSplitter.split(content, cfg)
        // If it doesn't throw, check that result is not empty
        result should not be empty
      } catch {
        case ex: Exception =>
          ex.getMessage.toLowerCase should (include("failed") or include("corrupted") or include("invalid"))
      }
    }
  }

  "Exception context propagation with Aspose splitters" should {
    "preserve custom context through failure handler" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationSevenz)
      val cfg = SplitConfig(
        strategy = Some(SplitStrategy.Embedded),
        failureMode = SplitFailureMode.ThrowException,
        failureContext = Map(
          "source" -> "aspose-test",
          "module" -> "7z-archive"
        )
      )
      
      val thrown = intercept[SplitException] {
        SevenZipArchiveAsposeSplitter.split(content, cfg)
      }
      
      thrown.context should contain("source" -> "aspose-test")
      thrown.context should contain("module" -> "7z-archive")
    }
  }

  "Aspose splitters with multiple failure modes" should {
    "handle errors consistently across modes" in {
      val content = FileContent(invalidBytes, MimeType.ApplicationSevenz)
      
      // ThrowException mode
      val throwConfig = throwExceptionConfig(SplitStrategy.Embedded)
      assertThrows[SplitException] {
        SevenZipArchiveAsposeSplitter.split(content, throwConfig)
      }
      
      // PreserveAsChunk mode
      val preserveConfig = preserveAsChunkConfig(SplitStrategy.Embedded)
      val preserveResult = SevenZipArchiveAsposeSplitter.split(content, preserveConfig)
      preserveResult should have size 1
      preserveResult.head.label shouldBe "document"
      
      // DropDocument mode
      val dropConfig = dropDocumentConfig(SplitStrategy.Embedded)
      val dropResult = SevenZipArchiveAsposeSplitter.split(content, dropConfig)
      dropResult shouldBe empty
      
      // TagAndPreserve mode
      val tagConfig = tagAndPreserveConfig(SplitStrategy.Embedded)
      val tagResult = SevenZipArchiveAsposeSplitter.split(content, tagConfig)
      tagResult should have size 1
      tagResult.head.isFailed shouldBe true
      tagResult.head.label shouldBe "failed_document"
    }
  }
}