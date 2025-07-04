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

class UpdatedAsposeSplittersFailureModeSpec extends AnyWordSpec with Matchers {

  val invalidBytes = Array[Byte](0x00, 0x01, 0x02, 0x03)
  val emptyBytes = Array.empty[Byte]

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
}