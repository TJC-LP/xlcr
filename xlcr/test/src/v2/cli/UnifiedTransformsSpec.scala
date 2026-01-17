package com.tjclp.xlcr.v2.cli

import java.io.ByteArrayOutputStream

import zio.Chunk

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.pdfbox.pdmodel.{PDDocument, PDPage}
import org.apache.pdfbox.pdmodel.common.PDRectangle

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import com.tjclp.xlcr.v2.types.{Content, Mime}

/**
 * Tests for the UnifiedTransforms fallback chain.
 *
 * Note: Some tests may require Aspose or LibreOffice to be installed.
 * Tests focus on the fallback behavior to XLCR Core which is always available.
 */
class UnifiedTransformsSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll:

  // Helper to run ZIO effects
  import zio.{Runtime, Unsafe}
  private val runtime = Runtime.default

  private def runZIO[E, A](effect: zio.ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private def runZIOEither[E, A](effect: zio.ZIO[Any, E, A]): Either[E, A] =
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(effect.either).getOrThrowFiberFailure()
    }

  // Document generators
  private def xlsxContent(sheets: String*): Content[Mime.Xlsx] =
    val workbook = new XSSFWorkbook()
    val names = if sheets.isEmpty then Seq("Sheet1") else sheets
    names.foreach { name =>
      val sheet = workbook.createSheet(name)
      val row = sheet.createRow(0)
      row.createCell(0).setCellValue(s"Data in $name")
    }
    val baos = new ByteArrayOutputStream()
    workbook.write(baos)
    workbook.close()
    Content(baos.toByteArray, Mime.xlsx)

  private def pptxContent(slideCount: Int): Content[Mime.Pptx] =
    val ppt = new XMLSlideShow()
    (1 to slideCount).foreach { i =>
      val slide = ppt.createSlide()
      val textBox = slide.createTextBox()
      textBox.setAnchor(new java.awt.Rectangle(50, 50, 400, 100))
      textBox.setText(s"Slide $i")
    }
    val baos = new ByteArrayOutputStream()
    ppt.write(baos)
    ppt.close()
    Content(baos.toByteArray, Mime.pptx)

  private def pdfContent(pages: Int): Content[Mime.Pdf] =
    val doc = new PDDocument()
    (1 to pages).foreach { _ =>
      doc.addPage(new PDPage(PDRectangle.A4))
    }
    val baos = new ByteArrayOutputStream()
    doc.save(baos)
    doc.close()
    Content(baos.toByteArray, Mime.pdf)

  // ============================================================================
  // canConvert Tests - Always Works
  // ============================================================================

  "UnifiedTransforms.canConvert" should "return true for text extraction (ANY -> text/plain)" in {
    // Tika fallback always works for text extraction
    UnifiedTransforms.canConvert(Mime.pdf, Mime.plain) shouldBe true
    UnifiedTransforms.canConvert(Mime.xlsx, Mime.plain) shouldBe true
    UnifiedTransforms.canConvert(Mime.docx, Mime.plain) shouldBe true
    UnifiedTransforms.canConvert(Mime.pptx, Mime.plain) shouldBe true
    UnifiedTransforms.canConvert(Mime.html, Mime.plain) shouldBe true
  }

  it should "return true for XML extraction (ANY -> application/xml)" in {
    // Tika fallback always works for XML extraction
    UnifiedTransforms.canConvert(Mime.pdf, Mime.xml) shouldBe true
    UnifiedTransforms.canConvert(Mime.xlsx, Mime.xml) shouldBe true
    UnifiedTransforms.canConvert(Mime.docx, Mime.xml) shouldBe true
  }

  it should "return true for XLSX to ODS conversion" in {
    // XLCR Core supports this
    UnifiedTransforms.canConvert(Mime.xlsx, Mime.ods) shouldBe true
  }

  // ============================================================================
  // canSplit Tests - Always Works
  // ============================================================================

  "UnifiedTransforms.canSplit" should "return true for supported types via XLCR Core" in {
    // These are always supported by XLCR Core
    UnifiedTransforms.canSplit(Mime.xlsx) shouldBe true
    UnifiedTransforms.canSplit(Mime.pptx) shouldBe true
    UnifiedTransforms.canSplit(Mime.pdf) shouldBe true
    UnifiedTransforms.canSplit(Mime.plain) shouldBe true
    UnifiedTransforms.canSplit(Mime.csv) shouldBe true
    UnifiedTransforms.canSplit(Mime.zip) shouldBe true
  }

  it should "return false for unsupported types" in {
    UnifiedTransforms.canSplit(Mime.mp3) shouldBe false
    UnifiedTransforms.canSplit(Mime.mp4) shouldBe false
    UnifiedTransforms.canSplit(Mime.jpeg) shouldBe false
  }

  // ============================================================================
  // Conversion Tests with XLCR Core Fallback
  // ============================================================================

  "UnifiedTransforms.convert" should "extract text from XLSX via fallback chain" in {
    val xlsx = xlsxContent("TestSheet")
    val xlsxMime: Content[Mime] = xlsx.asInstanceOf[Content[Mime]]

    val result = runZIO(UnifiedTransforms.convert(xlsxMime, Mime.plain))

    result.mime shouldBe Mime.plain
    val text = new String(result.toArray, "UTF-8")
    text should include("TestSheet")
  }

  it should "extract text from PPTX via fallback chain" in {
    val pptx = pptxContent(2)
    val pptxMime: Content[Mime] = pptx.asInstanceOf[Content[Mime]]

    val result = runZIO(UnifiedTransforms.convert(pptxMime, Mime.plain))

    result.mime shouldBe Mime.plain
    val text = new String(result.toArray, "UTF-8")
    text should include("Slide")
  }

  it should "extract text from PDF via fallback chain" in {
    // Note: Empty PDF pages won't have text, but conversion should succeed
    val pdf = pdfContent(1)
    val pdfMime: Content[Mime] = pdf.asInstanceOf[Content[Mime]]

    val result = runZIOEither(UnifiedTransforms.convert(pdfMime, Mime.plain))

    result.isRight shouldBe true
    result.toOption.get.mime shouldBe Mime.plain
  }

  it should "convert XLSX to ODS via fallback chain" in {
    val xlsx = xlsxContent("Sheet1", "Sheet2")
    val xlsxMime: Content[Mime] = xlsx.asInstanceOf[Content[Mime]]

    val result = runZIO(UnifiedTransforms.convert(xlsxMime, Mime.ods))

    result.mime shouldBe Mime.ods
    result.size should be > 0
  }

  it should "extract XML from plain text via fallback chain" in {
    val text: Content[Mime] = Content.fromString("Hello, World!", Mime.plain)

    val result = runZIO(UnifiedTransforms.convert(text, Mime.xml))

    result.mime shouldBe Mime.xml
    val xml = new String(result.toArray, "UTF-8")
    xml should include("Hello")
  }

  // ============================================================================
  // Splitter Tests with XLCR Core Fallback
  // ============================================================================

  "UnifiedTransforms.split" should "split XLSX into sheets via fallback chain" in {
    val xlsx = xlsxContent("Sheet1", "Sheet2", "Sheet3")
    val xlsxMime: Content[Mime] = xlsx.asInstanceOf[Content[Mime]]

    val fragments = runZIO(UnifiedTransforms.split(xlsxMime))

    fragments.size shouldBe 3
  }

  it should "split PDF into pages via fallback chain" in {
    val pdf = pdfContent(3)
    val pdfMime: Content[Mime] = pdf.asInstanceOf[Content[Mime]]

    val fragments = runZIO(UnifiedTransforms.split(pdfMime))

    fragments.size shouldBe 3
  }

  it should "split plain text into paragraphs via fallback chain" in {
    val text: Content[Mime] = Content.fromString("Para 1\n\nPara 2\n\nPara 3", Mime.plain)

    val fragments = runZIO(UnifiedTransforms.split(text))

    fragments.size shouldBe 3
  }

  it should "split CSV into rows via fallback chain" in {
    val csv: Content[Mime] = Content.fromString("Header\nRow1\nRow2\nRow3", Mime.csv)

    val fragments = runZIO(UnifiedTransforms.split(csv))

    // 3 data rows (header is preserved in each)
    fragments.size shouldBe 3
  }

  // ============================================================================
  // Fallback Chain Tests
  // ============================================================================

  "Unified fallback chain" should "succeed for text/plain target regardless of backend availability" in {
    // This test verifies the fundamental contract: ANY -> text/plain always works
    // because XLCR Core with Tika is always available
    val mimeTypes = Seq(Mime.pdf, Mime.xlsx, Mime.docx, Mime.pptx, Mime.html, Mime.plain)

    mimeTypes.foreach { inputMime =>
      val canConvert = UnifiedTransforms.canConvert(inputMime, Mime.plain)
      canConvert shouldBe true
    }
  }

  it should "succeed for application/xml target regardless of backend availability" in {
    // Same fundamental contract: ANY -> application/xml always works
    val mimeTypes = Seq(Mime.pdf, Mime.xlsx, Mime.docx, Mime.pptx)

    mimeTypes.foreach { inputMime =>
      val canConvert = UnifiedTransforms.canConvert(inputMime, Mime.xml)
      canConvert shouldBe true
    }
  }

  it should "always be able to split document types supported by XLCR Core" in {
    // These splits are always supported regardless of Aspose/LibreOffice
    val supportedTypes = Seq(Mime.xlsx, Mime.pptx, Mime.pdf, Mime.plain, Mime.csv, Mime.zip)

    supportedTypes.foreach { mime =>
      val canSplit = UnifiedTransforms.canSplit(mime)
      canSplit shouldBe true
    }
  }

  // ============================================================================
  // Edge Cases
  // ============================================================================

  it should "fail with ParseError for empty content" in {
    val empty: Content[Mime] = Content.empty(Mime.plain)

    val result = runZIOEither(UnifiedTransforms.convert(empty, Mime.xml))

    // Tika fails on empty input
    result.isLeft shouldBe true
  }

  it should "preserve MIME type through extraction" in {
    val html: Content[Mime] = Content.fromString("<p>Test</p>", Mime.html)

    val result = runZIO(UnifiedTransforms.convert(html, Mime.plain))

    result.mime shouldBe Mime.plain
  }
