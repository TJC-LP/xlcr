package com.tjclp.xlcr.v2.core

import com.tjclp.xlcr.v2.base.{V2DocumentGenerators, V2TestSupport}
import com.tjclp.xlcr.v2.types.{Content, Mime}

/**
 * Tests for XLCR Core splitters using POI, PDFBox, and standard libraries.
 */
class XlcrSplittersSpec extends V2TestSupport:

  // ============================================================================
  // Excel Sheet Splitter Tests
  // ============================================================================

  "XlcrSplitters.xlsxSheetSplitter" should "split workbook into sheets" in {
    val xlsx = V2DocumentGenerators.xlsxContent("Sheet1", "Sheet2", "Sheet3")
    val fragments = runZIO(XlcrSplitters.xlsxSheetSplitter.split(xlsx))

    fragments.size shouldBe 3
  }

  it should "preserve sheet names" in {
    val xlsx = V2DocumentGenerators.xlsxContent("Revenue", "Expenses", "Summary")
    val fragments = runZIO(XlcrSplitters.xlsxSheetSplitter.split(xlsx))

    fragments.map(_.name.get) should contain allOf ("Revenue", "Expenses", "Summary")
  }

  it should "have correct indices" in {
    val xlsx = V2DocumentGenerators.xlsxContent("A", "B")
    val fragments = runZIO(XlcrSplitters.xlsxSheetSplitter.split(xlsx))

    fragments(0).index shouldBe 0
    fragments(0).displayIndex shouldBe 1
    fragments(1).index shouldBe 1
    fragments(1).displayIndex shouldBe 2
  }

  it should "output valid XLSX files" in {
    val xlsx = V2DocumentGenerators.xlsxContent("Test")
    val fragments = runZIO(XlcrSplitters.xlsxSheetSplitter.split(xlsx))

    fragments.head.mime shouldBe Mime.xlsx
    // Check for ZIP magic number (XLSX is a ZIP file)
    fragments.head.data.toArray.take(2) shouldBe Array(0x50.toByte, 0x4B.toByte)
  }

  it should "have CORE_PRIORITY (-100)" in {
    XlcrSplitters.xlsxSheetSplitter.priority shouldBe -100
  }

  // ============================================================================
  // PowerPoint Slide Splitter Tests
  // ============================================================================

  "XlcrSplitters.pptxSlideSplitter" should "split presentation into slides" in {
    val pptx = V2DocumentGenerators.pptxContent(3)
    val fragments = runZIO(XlcrSplitters.pptxSlideSplitter.split(pptx))

    fragments.size shouldBe 3
  }

  it should "have correct indices" in {
    val pptx = V2DocumentGenerators.pptxContent(2)
    val fragments = runZIO(XlcrSplitters.pptxSlideSplitter.split(pptx))

    fragments(0).index shouldBe 0
    fragments(1).index shouldBe 1
  }

  it should "output valid PPTX files" in {
    val pptx = V2DocumentGenerators.pptxContent(1)
    val fragments = runZIO(XlcrSplitters.pptxSlideSplitter.split(pptx))

    fragments.head.mime shouldBe Mime.pptx
    // Check for ZIP magic number
    fragments.head.data.toArray.take(2) shouldBe Array(0x50.toByte, 0x4B.toByte)
  }

  it should "have CORE_PRIORITY (-100)" in {
    XlcrSplitters.pptxSlideSplitter.priority shouldBe -100
  }

  // ============================================================================
  // PDF Page Splitter Tests
  // ============================================================================

  "XlcrSplitters.pdfPageSplitter" should "split PDF into pages" in {
    val pdf = V2DocumentGenerators.pdfContent(3)
    val fragments = runZIO(XlcrSplitters.pdfPageSplitter.split(pdf))

    fragments.size shouldBe 3
  }

  it should "have correct page names" in {
    val pdf = V2DocumentGenerators.pdfContent(2)
    val fragments = runZIO(XlcrSplitters.pdfPageSplitter.split(pdf))

    fragments(0).name shouldBe Some("Page 1")
    fragments(1).name shouldBe Some("Page 2")
  }

  it should "output valid PDF files" in {
    val pdf = V2DocumentGenerators.pdfContent(1)
    val fragments = runZIO(XlcrSplitters.pdfPageSplitter.split(pdf))

    fragments.head.mime shouldBe Mime.pdf
    // Check for PDF magic number
    val header = new String(fragments.head.data.toArray.take(5))
    header shouldBe "%PDF-"
  }

  it should "preserve content in each page" in {
    val pdf = V2DocumentGenerators.pdfContent(2)
    val fragments = runZIO(XlcrSplitters.pdfPageSplitter.split(pdf))

    // Each fragment should be a non-empty PDF
    fragments.foreach { f =>
      f.data.size should be > 0
      f.mime shouldBe Mime.pdf
    }
  }

  it should "have CORE_PRIORITY (-100)" in {
    XlcrSplitters.pdfPageSplitter.priority shouldBe -100
  }

  // ============================================================================
  // Text Splitter Tests
  // ============================================================================

  "XlcrSplitters.textSplitter" should "split text into paragraphs" in {
    val text = V2DocumentGenerators.textContentWithParagraphs(
      "First paragraph",
      "Second paragraph",
      "Third paragraph"
    )
    val fragments = runZIO(XlcrSplitters.textSplitter.splitDynamic(text))

    fragments.size shouldBe 3
  }

  it should "name paragraphs sequentially" in {
    val text = V2DocumentGenerators.textContentWithParagraphs("A", "B")
    val fragments = runZIO(XlcrSplitters.textSplitter.splitDynamic(text))

    fragments(0).name shouldBe Some("Paragraph 1")
    fragments(1).name shouldBe Some("Paragraph 2")
  }

  it should "handle empty text" in {
    val text = Content.fromString("", Mime.plain)
    val fragments = runZIO(XlcrSplitters.textSplitter.splitDynamic(text))

    fragments shouldBe empty
  }

  it should "handle single paragraph" in {
    val text = Content.fromString("Single paragraph content", Mime.plain)
    val fragments = runZIO(XlcrSplitters.textSplitter.splitDynamic(text))

    fragments.size shouldBe 1
  }

  it should "have CORE_PRIORITY (-100)" in {
    XlcrSplitters.textSplitter.priority shouldBe -100
  }

  // ============================================================================
  // CSV Splitter Tests
  // ============================================================================

  "XlcrSplitters.csvSplitter" should "split CSV into rows with header" in {
    val csv = V2DocumentGenerators.csvContentWithHeader(
      Seq("Name", "Age", "City"),
      Seq(
        Seq("Alice", "30", "NYC"),
        Seq("Bob", "25", "LA"),
        Seq("Carol", "35", "Chicago")
      )
    )
    val fragments = runZIO(XlcrSplitters.csvSplitter.splitDynamic(csv))

    fragments.size shouldBe 3
  }

  it should "preserve header in each fragment" in {
    val csv = V2DocumentGenerators.csvContentWithHeader(
      Seq("Col1", "Col2"),
      Seq(Seq("A", "B"))
    )
    val fragments = runZIO(XlcrSplitters.csvSplitter.splitDynamic(csv))

    val text = new String(fragments.head.data.toArray, "UTF-8")
    text should include("Col1")
    text should include("A")
  }

  it should "name rows with correct line numbers" in {
    val csv = V2DocumentGenerators.csvContentWithHeader(
      Seq("Header"),
      Seq(Seq("Row1"), Seq("Row2"))
    )
    val fragments = runZIO(XlcrSplitters.csvSplitter.splitDynamic(csv))

    // Row 2 in original file (header is row 1)
    fragments(0).name shouldBe Some("Row 2")
    fragments(1).name shouldBe Some("Row 3")
  }

  it should "handle empty CSV" in {
    val csv = Content.fromString("Header1,Header2\n", Mime.csv)
    val fragments = runZIO(XlcrSplitters.csvSplitter.splitDynamic(csv))

    fragments shouldBe empty
  }

  it should "have CORE_PRIORITY (-100)" in {
    XlcrSplitters.csvSplitter.priority shouldBe -100
  }

  // ============================================================================
  // ZIP Splitter Tests
  // ============================================================================

  "XlcrSplitters.zipEntrySplitter" should "extract entries from ZIP" in {
    val zip = V2DocumentGenerators.zipContentWithTextFiles(Map(
      "file1.txt" -> "Content 1",
      "file2.txt" -> "Content 2",
      "file3.json" -> "{}"
    ))
    val fragments = runZIO(XlcrSplitters.zipEntrySplitter.splitDynamic(zip))

    fragments.size shouldBe 3
  }

  it should "detect MIME types from extensions" in {
    val zip = V2DocumentGenerators.zipContentWithTextFiles(Map(
      "readme.txt" -> "text",
      "config.json" -> "{}",
      "data.xml" -> "<root/>"
    ))
    val fragments = runZIO(XlcrSplitters.zipEntrySplitter.splitDynamic(zip))

    val mimeTypes = fragments.map(_.mime).toSet
    mimeTypes should contain(Mime.plain)
    mimeTypes should contain(Mime.json)
    mimeTypes should contain(Mime.xml)
  }

  it should "preserve file content" in {
    val zip = V2DocumentGenerators.zipContentWithTextFiles(Map(
      "test.txt" -> "Hello, World!"
    ))
    val fragments = runZIO(XlcrSplitters.zipEntrySplitter.splitDynamic(zip))

    val text = new String(fragments.head.data.toArray, "UTF-8")
    text shouldBe "Hello, World!"
  }

  it should "use filename as name" in {
    val zip = V2DocumentGenerators.zipContentWithTextFiles(Map(
      "document.txt" -> "content"
    ))
    val fragments = runZIO(XlcrSplitters.zipEntrySplitter.splitDynamic(zip))

    fragments.head.name shouldBe Some("document.txt")
  }

  it should "skip directories" in {
    // Create ZIP with directory entry
    import java.io.ByteArrayOutputStream
    import java.util.zip.{ZipEntry, ZipOutputStream}

    val baos = new ByteArrayOutputStream()
    val zos = new ZipOutputStream(baos)
    zos.putNextEntry(new ZipEntry("folder/"))
    zos.closeEntry()
    zos.putNextEntry(new ZipEntry("folder/file.txt"))
    zos.write("content".getBytes)
    zos.closeEntry()
    zos.close()

    val zip = Content(baos.toByteArray, Mime.zip)
    val fragments = runZIO(XlcrSplitters.zipEntrySplitter.splitDynamic(zip))

    // Should only have the file, not the directory
    fragments.size shouldBe 1
    fragments.head.name shouldBe Some("file.txt")
  }

  it should "have CORE_PRIORITY (-100)" in {
    XlcrSplitters.zipEntrySplitter.priority shouldBe -100
  }

  // ============================================================================
  // ODS Splitter Tests
  // ============================================================================

  "XlcrSplitters.odsSheetSplitter" should "split ODS into sheets" in {
    // First create an ODS from XLSX
    val xlsx = V2DocumentGenerators.xlsxContent("Sheet1", "Sheet2")
    val ods = runZIO(XlcrConversions.xlsxToOds.convert(xlsx))

    val fragments = runZIO(XlcrSplitters.odsSheetSplitter.split(ods))

    fragments.size shouldBe 2
  }

  it should "preserve sheet names" in {
    val xlsx = V2DocumentGenerators.xlsxContent("Alpha", "Beta")
    val ods = runZIO(XlcrConversions.xlsxToOds.convert(xlsx))

    val fragments = runZIO(XlcrSplitters.odsSheetSplitter.split(ods))

    fragments.map(_.name.get) should contain allOf ("Alpha", "Beta")
  }

  it should "have CORE_PRIORITY (-100)" in {
    XlcrSplitters.odsSheetSplitter.priority shouldBe -100
  }
