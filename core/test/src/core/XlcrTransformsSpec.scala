package com.tjclp.xlcr.core

import com.tjclp.xlcr.base.{V2DocumentGenerators, V2TestSupport}
import com.tjclp.xlcr.transform.UnsupportedConversion
import com.tjclp.xlcr.types.{Content, Mime}

/**
 * Tests for the XlcrTransforms dispatch object.
 */
class XlcrTransformsSpec extends V2TestSupport:

  // ============================================================================
  // Conversion Dispatch Tests
  // ============================================================================

  "XlcrTransforms.convert" should "dispatch to anyToPlainText for text/plain target" in {
    val input: Content[Mime] = Content.fromString("<html><body>Test</body></html>", Mime.html)
    val result = runZIO(XlcrTransforms.convert(input, Mime.plain))

    result.mime shouldBe Mime.plain
    val text = new String(result.toArray, "UTF-8")
    text should include("Test")
  }

  it should "dispatch to anyToXml for application/xml target" in {
    val input: Content[Mime] = Content.fromString("Hello", Mime.plain)
    val result = runZIO(XlcrTransforms.convert(input, Mime.xml))

    result.mime shouldBe Mime.xml
    val xml = new String(result.toArray, "UTF-8")
    xml should include("<")
  }

  it should "dispatch to xlsxToOds for xlsx->ods conversion" in {
    val xlsx = V2DocumentGenerators.xlsxContent("Sheet1")
    val input: Content[Mime] = xlsx.asInstanceOf[Content[Mime]]
    val result = runZIO(XlcrTransforms.convert(input, Mime.ods))

    result.mime shouldBe Mime.ods
  }

  it should "fail with UnsupportedConversion for unsupported target" in {
    val input: Content[Mime] = Content.fromString("test", Mime.plain)
    val error = runZIOExpectingError(XlcrTransforms.convert(input, Mime.mp3))

    error shouldBe a[UnsupportedConversion]
    error.asInstanceOf[UnsupportedConversion].from shouldBe Mime.plain
    error.asInstanceOf[UnsupportedConversion].to shouldBe Mime.mp3
  }

  it should "fail with UnsupportedConversion for non-xlsx to ods" in {
    val input: Content[Mime] = Content.fromString("test", Mime.plain)
    val error = runZIOExpectingError(XlcrTransforms.convert(input, Mime.ods))

    error shouldBe a[UnsupportedConversion]
  }

  // ============================================================================
  // canConvert Tests
  // ============================================================================

  "XlcrTransforms.canConvert" should "return true for text/plain target" in {
    XlcrTransforms.canConvert(Mime.pdf, Mime.plain) shouldBe true
    XlcrTransforms.canConvert(Mime.xlsx, Mime.plain) shouldBe true
    XlcrTransforms.canConvert(Mime.docx, Mime.plain) shouldBe true
    XlcrTransforms.canConvert(Mime.html, Mime.plain) shouldBe true
    XlcrTransforms.canConvert(Mime.pptx, Mime.plain) shouldBe true
  }

  it should "return true for application/xml target" in {
    XlcrTransforms.canConvert(Mime.pdf, Mime.xml) shouldBe true
    XlcrTransforms.canConvert(Mime.xlsx, Mime.xml) shouldBe true
    XlcrTransforms.canConvert(Mime.docx, Mime.xml) shouldBe true
  }

  it should "return true for xlsx->ods" in {
    XlcrTransforms.canConvert(Mime.xlsx, Mime.ods) shouldBe true
  }

  it should "return false for unsupported conversions" in {
    XlcrTransforms.canConvert(Mime.pdf, Mime.xlsx) shouldBe false
    XlcrTransforms.canConvert(Mime.docx, Mime.pptx) shouldBe false
    XlcrTransforms.canConvert(Mime.plain, Mime.pdf) shouldBe false
    XlcrTransforms.canConvert(Mime.html, Mime.mp3) shouldBe false
  }

  it should "return false for ods target with non-xlsx source" in {
    XlcrTransforms.canConvert(Mime.csv, Mime.ods) shouldBe false
    XlcrTransforms.canConvert(Mime.pdf, Mime.ods) shouldBe false
  }

  // ============================================================================
  // Splitter Dispatch Tests
  // ============================================================================

  "XlcrTransforms.split" should "dispatch to xlsxSheetSplitter for XLSX" in {
    val xlsx = V2DocumentGenerators.xlsxContent("Sheet1", "Sheet2")
    val input: Content[Mime] = xlsx.asInstanceOf[Content[Mime]]
    val fragments = runZIO(XlcrTransforms.split(input))

    fragments.size shouldBe 2
  }

  it should "dispatch to pptxSlideSplitter for PPTX" in {
    val pptx = V2DocumentGenerators.pptxContent(3)
    val input: Content[Mime] = pptx.asInstanceOf[Content[Mime]]
    val fragments = runZIO(XlcrTransforms.split(input))

    fragments.size shouldBe 3
  }

  it should "dispatch to pdfPageSplitter for PDF" in {
    val pdf = V2DocumentGenerators.pdfContent(2)
    val input: Content[Mime] = pdf.asInstanceOf[Content[Mime]]
    val fragments = runZIO(XlcrTransforms.split(input))

    fragments.size shouldBe 2
  }

  it should "dispatch to textSplitter for plain text" in {
    val text = V2DocumentGenerators.textContentWithParagraphs("Para 1", "Para 2")
    val input: Content[Mime] = text.asInstanceOf[Content[Mime]]
    val fragments = runZIO(XlcrTransforms.split(input))

    fragments.size shouldBe 2
  }

  it should "dispatch to csvSplitter for CSV" in {
    val csv = V2DocumentGenerators.csvContentWithHeader(
      Seq("Header"),
      Seq(Seq("Row1"), Seq("Row2"))
    )
    val input: Content[Mime] = csv.asInstanceOf[Content[Mime]]
    val fragments = runZIO(XlcrTransforms.split(input))

    fragments.size shouldBe 2
  }

  it should "dispatch to zipEntrySplitter for ZIP" in {
    val zip = V2DocumentGenerators.zipContentWithTextFiles(Map(
      "a.txt" -> "A",
      "b.txt" -> "B"
    ))
    val input: Content[Mime] = zip.asInstanceOf[Content[Mime]]
    val fragments = runZIO(XlcrTransforms.split(input))

    fragments.size shouldBe 2
  }

  it should "fail with UnsupportedConversion for unsupported input" in {
    val input: Content[Mime] = Content.fromString("audio", Mime.mp3)
    val error = runZIOExpectingError(XlcrTransforms.split(input))

    error shouldBe a[UnsupportedConversion]
  }

  // ============================================================================
  // canSplit Tests
  // ============================================================================

  "XlcrTransforms.canSplit" should "return true for supported types" in {
    XlcrTransforms.canSplit(Mime.xlsx) shouldBe true
    XlcrTransforms.canSplit(Mime.xls) shouldBe true
    XlcrTransforms.canSplit(Mime.ods) shouldBe true
    XlcrTransforms.canSplit(Mime.pptx) shouldBe true
    XlcrTransforms.canSplit(Mime.docx) shouldBe true
    XlcrTransforms.canSplit(Mime.pdf) shouldBe true
    XlcrTransforms.canSplit(Mime.plain) shouldBe true
    XlcrTransforms.canSplit(Mime.csv) shouldBe true
    XlcrTransforms.canSplit(Mime.eml) shouldBe true
    XlcrTransforms.canSplit(Mime.msg) shouldBe true
    XlcrTransforms.canSplit(Mime.zip) shouldBe true
  }

  it should "return false for unsupported types" in {
    XlcrTransforms.canSplit(Mime.mp3) shouldBe false
    XlcrTransforms.canSplit(Mime.mp4) shouldBe false
    XlcrTransforms.canSplit(Mime.jpeg) shouldBe false
    XlcrTransforms.canSplit(Mime.png) shouldBe false
    XlcrTransforms.canSplit(Mime.html) shouldBe false
    XlcrTransforms.canSplit(Mime.json) shouldBe false
  }

  // ============================================================================
  // Integration Tests
  // ============================================================================

  "XlcrTransforms" should "convert then split XLSX" in {
    val xlsx = V2DocumentGenerators.xlsxContent("Data1", "Data2")
    val xlsxContent: Content[Mime] = xlsx.asInstanceOf[Content[Mime]]

    // First check we can split
    XlcrTransforms.canSplit(xlsxContent.mime) shouldBe true

    // Convert to ODS
    val ods = runZIO(XlcrTransforms.convert(xlsxContent, Mime.ods))
    ods.mime shouldBe Mime.ods

    // Split the ODS
    XlcrTransforms.canSplit(ods.mime) shouldBe true
    val fragments = runZIO(XlcrTransforms.split(ods))

    fragments.size shouldBe 2
  }

  it should "extract text from any document then split" in {
    val pdf = V2DocumentGenerators.pdfContent(3)
    val pdfContent: Content[Mime] = pdf.asInstanceOf[Content[Mime]]

    // Convert to plain text
    val text = runZIO(XlcrTransforms.convert(pdfContent, Mime.plain))
    text.mime shouldBe Mime.plain

    // Can split plain text
    XlcrTransforms.canSplit(text.mime) shouldBe true
  }
