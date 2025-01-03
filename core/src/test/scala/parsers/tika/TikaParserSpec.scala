package com.tjclp.xlcr
package parsers.tika

import types.MimeType
import utils.FileUtils

import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.{PDDocument, PDPage, PDPageContentStream}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, Path}
import scala.util.{Failure, Success}

class TikaParserSpec extends AnyFlatSpec with Matchers {

  "StandardTikaParser" should "extract text content from PDF files" in {
    FileUtils.withTempFile("test", ".pdf") { path =>
      createMinimalPdfFile(path)
      val result = StandardTikaParser.extractContent(path)
      result shouldBe a[Success[_]]
      result.get.contentType shouldBe MimeType.TextPlain.mimeType
      new String(result.get.data) should include("Test PDF Content")
    }
  }

  "XMLTikaParser" should "extract content as XML from PDF files" in {
    FileUtils.withTempFile("test", ".pdf") { path =>
      createMinimalPdfFile(path)
      val result = XMLTikaParser.extractContent(path)
      result shouldBe a[Success[_]]
      result.get.contentType shouldBe MimeType.ApplicationXml.mimeType
      new String(result.get.data) should include("<html")
      new String(result.get.data) should include("Test PDF Content")
    }
  }

  "StandardTikaParser" should "fail on empty files" in {
    FileUtils.withTempFile("test", ".txt") { path =>
      Files.write(path, Array.emptyByteArray)
      val standardResult = StandardTikaParser.extractContent(path)
      standardResult shouldBe a[Failure[_]]
      standardResult.failed.get.getMessage should include("must have > 0 bytes")
    }
  }

  "XMLTikaParser" should "fail on empty files" in {
    FileUtils.withTempFile("test", ".txt") { path =>
      Files.write(path, Array.emptyByteArray)
      val xmlResult = XMLTikaParser.extractContent(path)
      xmlResult shouldBe a[Failure[_]]
      xmlResult.failed.get.getMessage should include("must have > 0 bytes")
    }
  }

  "StandardTikaParser" should "handle non-existent files appropriately" in {
    val nonExistentPath = Path.of("non-existent.txt")
    val standardResult = StandardTikaParser.extractContent(nonExistentPath)
    standardResult shouldBe a[Failure[_]]
    standardResult.failed.get.getMessage should include("does not exist")
  }

  "XMLTikaParser" should "handle non-existent files appropriately" in {
    val nonExistentPath = Path.of("non-existent.txt")
    val xmlResult = XMLTikaParser.extractContent(nonExistentPath)
    xmlResult shouldBe a[Failure[_]]
    xmlResult.failed.get.getMessage should include("does not exist")
  }

  private def createMinimalPdfFile(path: Path): Unit = {
    val doc = new PDDocument()
    val page = new PDPage()
    doc.addPage(page)

    val contentStream = new PDPageContentStream(doc, page)
    contentStream.beginText()
    contentStream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(Standard14Fonts.FontName.HELVETICA), 12)
    contentStream.newLineAtOffset(100, 700)
    contentStream.showText("Test PDF Content")
    contentStream.endText()
    contentStream.close()

    doc.save(path.toFile)
    doc.close()
  }
}