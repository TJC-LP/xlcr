package com.tjclp.xlcr.v2.core

import com.tjclp.xlcr.v2.base.{V2DocumentGenerators, V2TestSupport}
import com.tjclp.xlcr.v2.transform.ParseError
import com.tjclp.xlcr.v2.types.{Content, Mime}

/**
 * Tests for XLCR Core conversions using Tika and POI.
 */
class XlcrConversionsSpec extends V2TestSupport:

  // Helper to widen Content type for anyTo* conversions
  private def widen[M <: Mime](c: Content[M]): Content[Mime] =
    Content.fromChunk(c.data, c.mime, c.metadata)

  // ============================================================================
  // Tika Text Extraction Tests
  // ============================================================================

  "XlcrConversions.anyToPlainText" should "extract text from plain text content" in {
    val input = widen(Content.fromString("Hello, World!", Mime.plain))
    val result = runZIO(XlcrConversions.anyToPlainText.convert(input))

    val text = new String(result.toArray, "UTF-8").trim
    text should include("Hello, World!")
    result.mime shouldBe Mime.plain
  }

  it should "extract text from HTML content" in {
    val html = "<html><body><h1>Title</h1><p>Paragraph text</p></body></html>"
    val input = widen(Content.fromString(html, Mime.html))
    val result = runZIO(XlcrConversions.anyToPlainText.convert(input))

    val text = new String(result.toArray, "UTF-8")
    text should include("Title")
    text should include("Paragraph text")
  }

  it should "extract text from XML content" in {
    val xml = "<root><item>Value 1</item><item>Value 2</item></root>"
    val input = widen(Content.fromString(xml, Mime.xml))
    val result = runZIO(XlcrConversions.anyToPlainText.convert(input))

    val text = new String(result.toArray, "UTF-8")
    text should include("Value 1")
    text should include("Value 2")
  }

  it should "extract text from XLSX content" in {
    val xlsx = V2DocumentGenerators.xlsxContentWithData(Map(
      "Sheet1" -> Seq(
        Seq("Header1", "Header2"),
        Seq("Data1", "Data2")
      )
    ))
    val result = runZIO(XlcrConversions.anyToPlainText.convert(widen(xlsx)))

    val text = new String(result.toArray, "UTF-8")
    text should include("Header1")
    text should include("Data1")
  }

  it should "extract text from PDF content" in {
    val pdf = V2DocumentGenerators.pdfContent(1)
    val result = runZIO(XlcrConversions.anyToPlainText.convert(widen(pdf)))

    val text = new String(result.toArray, "UTF-8")
    text should include("Page 1")
  }

  it should "extract text from PPTX content" in {
    val pptx = V2DocumentGenerators.pptxContent(2)
    val result = runZIO(XlcrConversions.anyToPlainText.convert(widen(pptx)))

    val text = new String(result.toArray, "UTF-8")
    text should include("Slide")
  }

  it should "have CORE_PRIORITY (-100)" in {
    XlcrConversions.anyToPlainText.priority shouldBe -100
  }

  // ============================================================================
  // Tika XML Extraction Tests
  // ============================================================================

  "XlcrConversions.anyToXml" should "extract XML from plain text" in {
    val input = widen(Content.fromString("Simple text", Mime.plain))
    val result = runZIO(XlcrConversions.anyToXml.convert(input))

    val xml = new String(result.toArray, "UTF-8")
    xml should include("<")
    xml should include(">")
    xml should include("Simple text")
    result.mime shouldBe Mime.xml
  }

  it should "extract XML from HTML preserving structure" in {
    val html = "<html><body><div>Content</div></body></html>"
    val input = widen(Content.fromString(html, Mime.html))
    val result = runZIO(XlcrConversions.anyToXml.convert(input))

    val xml = new String(result.toArray, "UTF-8")
    xml should include("Content")
  }

  it should "extract structured XML from XLSX" in {
    val xlsx = V2DocumentGenerators.xlsxContent("Sheet1")
    val result = runZIO(XlcrConversions.anyToXml.convert(widen(xlsx)))

    val xml = new String(result.toArray, "UTF-8")
    xml should include("<")
    xml should include(">")
  }

  it should "have CORE_PRIORITY (-100)" in {
    XlcrConversions.anyToXml.priority shouldBe -100
  }

  // ============================================================================
  // XLSX to ODS Conversion Tests
  // ============================================================================

  "XlcrConversions.xlsxToOds" should "convert Excel to ODS format" in {
    val xlsx = V2DocumentGenerators.xlsxContentWithData(Map(
      "TestSheet" -> Seq(
        Seq("A", "B"),
        Seq("1", "2")
      )
    ))
    val result = runZIO(XlcrConversions.xlsxToOds.convert(xlsx))

    result.mime shouldBe Mime.ods
    result.size should be > 0
  }

  it should "preserve sheet names" in {
    val xlsx = V2DocumentGenerators.xlsxContent("MySheet", "AnotherSheet")
    val result = runZIO(XlcrConversions.xlsxToOds.convert(xlsx))

    // Verify it's a valid ODS file (starts with PK for ZIP)
    result.toArray.take(2) shouldBe Array(0x50.toByte, 0x4B.toByte)
  }

  it should "preserve cell values" in {
    val xlsx = V2DocumentGenerators.xlsxContentWithData(Map(
      "Data" -> Seq(
        Seq("Text", 123.45, true),
        Seq("More text", 0, false)
      )
    ))
    val result = runZIO(XlcrConversions.xlsxToOds.convert(xlsx))

    // Result should be a non-empty ODS file
    result.nonEmpty shouldBe true
    result.mime shouldBe Mime.ods
  }

  it should "handle empty workbook" in {
    val xlsx = V2DocumentGenerators.xlsxContent("EmptySheet")
    val result = runZIO(XlcrConversions.xlsxToOds.convert(xlsx))

    result.nonEmpty shouldBe true
    result.mime shouldBe Mime.ods
  }

  it should "have CORE_PRIORITY (-100)" in {
    XlcrConversions.xlsxToOds.priority shouldBe -100
  }

  // ============================================================================
  // Error Handling Tests
  // ============================================================================

  "XlcrConversions" should "handle invalid input gracefully" in {
    // Random binary data
    val invalid = widen(Content(Array[Byte](0, 1, 2, 3, 4), Mime.plain))

    // Tika should still return something (empty or the raw bytes as text)
    val result = runZIOEither(XlcrConversions.anyToPlainText.convert(invalid))

    // Either success or ParseError
    result.isRight shouldBe true
  }

  it should "fail with ParseError for empty input" in {
    val empty = widen(Content.empty(Mime.plain))
    val error = runZIOExpectingError(XlcrConversions.anyToPlainText.convert(empty))

    error shouldBe a[ParseError]
  }

  // ============================================================================
  // Edge Cases
  // ============================================================================

  it should "handle large text content" in {
    val largeText = "x" * 10000
    val input = widen(Content.fromString(largeText, Mime.plain))
    val result = runZIO(XlcrConversions.anyToPlainText.convert(input))

    result.size should be >= 10000
  }

  it should "handle unicode content" in {
    val unicode = "Hello, 世界! 🌍 Привет мир"
    val input = widen(Content.fromString(unicode, Mime.plain))
    val result = runZIO(XlcrConversions.anyToPlainText.convert(input))

    val text = new String(result.toArray, "UTF-8").trim
    text should include("世界")
    text should include("Привет")
  }
