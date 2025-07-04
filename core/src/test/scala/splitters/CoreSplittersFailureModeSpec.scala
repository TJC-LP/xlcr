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
import splitters.text._
import splitters.word._
import types.MimeType

class CoreSplittersFailureModeSpec extends AnyWordSpec with Matchers {

  val invalidBytes = Array[Byte](0x00, 0x01, 0x02, 0x03)
  val emptyBytes = Array.empty[Byte]

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
        0x50, 0x4B, 0x05, 0x06, 0x00, 0x00, 0x00, 0x00,
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
      val content = FileContent(invalidBytes, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)
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
      val content = FileContent(invalidBytes, MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation)
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
      val content = FileContent(invalidBytes, MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument)
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
}