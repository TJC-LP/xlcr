package com.tjclp.xlcr.v2.types

import zio.Chunk

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Tests for Fragment and DynamicFragment types.
 */
class FragmentSpec extends AnyFlatSpec with Matchers:

  // ============================================================================
  // Fragment Construction Tests
  // ============================================================================

  "Fragment" should "be created with content and index" in {
    val content = Content.fromString("test", Mime.plain)
    val fragment = Fragment(content, 0, Some("test-name"))

    fragment.index shouldBe 0
    fragment.name shouldBe Some("test-name")
    fragment.mime shouldBe Mime.plain
  }

  it should "be created with factory method and metadata" in {
    val data = Chunk.fromArray("test".getBytes("UTF-8"))
    val fragment = Fragment(data, Mime.plain, 2, Some("sheet-3"), Some(5))

    fragment.index shouldBe 2
    fragment.name shouldBe Some("sheet-3")
    fragment.content.get(Content.MetadataKeys.Index) shouldBe Some("2")
    fragment.content.get(Content.MetadataKeys.Filename) shouldBe Some("sheet-3")
    fragment.content.get(Content.MetadataKeys.Total) shouldBe Some("5")
  }

  it should "be created from byte array" in {
    val data = "test data".getBytes("UTF-8")
    val fragment = Fragment.fromArray(data, Mime.plain, 0, Some("test"))

    new String(fragment.data.toArray, "UTF-8") shouldBe "test data"
    fragment.name shouldBe Some("test")
  }

  it should "preserve type parameter" in {
    val pdfContent = Content.fromString("pdf data", Mime.pdf)
    val fragment: Fragment[Mime.Pdf] = Fragment(pdfContent, 0)

    fragment.mime shouldBe Mime.pdf
  }

  // ============================================================================
  // Fragment Display Tests
  // ============================================================================

  "Fragment.displayIndex" should "return 1-based index" in {
    val fragment0 = Fragment(Content.fromString("", Mime.plain), 0)
    val fragment1 = Fragment(Content.fromString("", Mime.plain), 1)
    val fragment5 = Fragment(Content.fromString("", Mime.plain), 5)

    fragment0.displayIndex shouldBe 1
    fragment1.displayIndex shouldBe 2
    fragment5.displayIndex shouldBe 6
  }

  "Fragment.nameOrDefault" should "return name when present" in {
    val fragment = Fragment(Content.fromString("", Mime.plain), 0, Some("Sheet1"))

    fragment.nameOrDefault() shouldBe "Sheet1"
    fragment.nameOrDefault("page") shouldBe "Sheet1"
  }

  it should "return default with prefix when name absent" in {
    val fragment = Fragment(Content.fromString("", Mime.plain), 0, None)

    fragment.nameOrDefault() shouldBe "part-1"
    fragment.nameOrDefault("page") shouldBe "page-1"
    fragment.nameOrDefault("slide") shouldBe "slide-1"
  }

  it should "use 1-based index in default name" in {
    val fragment = Fragment(Content.fromString("", Mime.plain), 4, None)

    fragment.nameOrDefault() shouldBe "part-5"
    fragment.nameOrDefault("page") shouldBe "page-5"
  }

  // ============================================================================
  // Fragment Data Access Tests
  // ============================================================================

  "Fragment.data" should "return underlying content data" in {
    val data = Chunk.fromArray("test content".getBytes("UTF-8"))
    val content = Content.fromChunk(data, Mime.plain)
    val fragment = Fragment(content, 0)

    fragment.data shouldBe data
  }

  "Fragment.mime" should "return content MIME type" in {
    val xlsxContent = Content.empty(Mime.xlsx)
    val fragment = Fragment(xlsxContent, 0)

    fragment.mime shouldBe Mime.xlsx
  }

  // ============================================================================
  // DynamicFragment Construction Tests
  // ============================================================================

  "DynamicFragment" should "be created with content and index" in {
    val content: Content[Mime] = Content.fromString("test", Mime.plain)
    val fragment = DynamicFragment(content, 0, Some("test-name"))

    fragment.index shouldBe 0
    fragment.name shouldBe Some("test-name")
    fragment.mime shouldBe Mime.plain
  }

  it should "be created with factory method and metadata" in {
    val data = Chunk.fromArray("test".getBytes("UTF-8"))
    val fragment = DynamicFragment(data, Mime.json, 3, Some("data.json"), Some(10))

    fragment.index shouldBe 3
    fragment.name shouldBe Some("data.json")
    fragment.content.get(Content.MetadataKeys.Index) shouldBe Some("3")
    fragment.content.get(Content.MetadataKeys.Filename) shouldBe Some("data.json")
    fragment.content.get(Content.MetadataKeys.Total) shouldBe Some("10")
  }

  it should "be created from byte array" in {
    val data = "json data".getBytes("UTF-8")
    val fragment = DynamicFragment.fromArray(data, Mime.json, 0, Some("test.json"))

    new String(fragment.data.toArray, "UTF-8") shouldBe "json data"
    fragment.mime shouldBe Mime.json
  }

  it should "be converted from typed Fragment" in {
    val typedContent = Content.fromString("pdf data", Mime.pdf)
    val typedFragment: Fragment[Mime.Pdf] = Fragment(typedContent, 5, Some("page-6"))
    val dynamic = DynamicFragment.from(typedFragment)

    dynamic.index shouldBe 5
    dynamic.name shouldBe Some("page-6")
    dynamic.mime shouldBe Mime.pdf
  }

  // ============================================================================
  // DynamicFragment Display Tests
  // ============================================================================

  "DynamicFragment.displayIndex" should "return 1-based index" in {
    val fragment = DynamicFragment(Content.fromString("", Mime.plain).asInstanceOf[Content[Mime]], 3, None)

    fragment.displayIndex shouldBe 4
  }

  "DynamicFragment.nameOrDefault" should "behave like Fragment" in {
    val withName = DynamicFragment(
      Content.fromString("", Mime.plain).asInstanceOf[Content[Mime]],
      0,
      Some("custom-name")
    )
    val withoutName = DynamicFragment(
      Content.fromString("", Mime.plain).asInstanceOf[Content[Mime]],
      2,
      None
    )

    withName.nameOrDefault() shouldBe "custom-name"
    withoutName.nameOrDefault() shouldBe "part-3"
    withoutName.nameOrDefault("attachment") shouldBe "attachment-3"
  }

  // ============================================================================
  // DynamicFragment.narrow Tests
  // ============================================================================

  "DynamicFragment.narrow" should "return Some when MIME matches" in {
    val pdfContent: Content[Mime] = Content.fromString("pdf", Mime.pdf)
    val dynamic = DynamicFragment(pdfContent, 0, Some("doc.pdf"))

    val narrowed: Option[Fragment[Mime.Pdf]] = dynamic.narrow(Mime.pdf)

    narrowed shouldBe defined
    narrowed.get.index shouldBe 0
    narrowed.get.name shouldBe Some("doc.pdf")
    narrowed.get.mime shouldBe Mime.pdf
  }

  it should "return None when MIME does not match" in {
    val pdfContent: Content[Mime] = Content.fromString("pdf", Mime.pdf)
    val dynamic = DynamicFragment(pdfContent, 0, Some("doc.pdf"))

    val narrowed: Option[Fragment[Mime.Html]] = dynamic.narrow(Mime.html)

    narrowed shouldBe None
  }

  it should "allow type-safe operations on narrowed fragment" in {
    val xlsxContent: Content[Mime] = Content.fromString("xlsx", Mime.xlsx)
    val dynamic = DynamicFragment(xlsxContent, 0, Some("sheet.xlsx"))

    dynamic.narrow(Mime.xlsx).foreach { typedFragment =>
      // This proves we have type-safe Fragment[Mime.Xlsx]
      val mime: Mime.Xlsx = typedFragment.mime
      mime shouldBe Mime.xlsx
    }
  }

  // ============================================================================
  // Edge Cases
  // ============================================================================

  "Fragment" should "handle empty content" in {
    val empty = Fragment(Content.empty(Mime.plain), 0)

    empty.data.isEmpty shouldBe true
    empty.content.isEmpty shouldBe true
  }

  it should "handle various MIME types" in {
    val types = Seq(
      Mime.pdf,
      Mime.xlsx,
      Mime.docx,
      Mime.pptx,
      Mime.html,
      Mime.json,
      Mime.xml,
      Mime.zip
    )

    types.foreach { mime =>
      val content = Content.empty(mime)
      val fragment = Fragment(content, 0)
      fragment.mime shouldBe mime
    }
  }

  "DynamicFragment" should "handle fragments from different sources" in {
    // Simulating fragments from a ZIP archive with different types
    val fragments = Seq(
      DynamicFragment.fromArray("text".getBytes, Mime.plain, 0, Some("readme.txt")),
      DynamicFragment.fromArray("{}".getBytes, Mime.json, 1, Some("config.json")),
      DynamicFragment.fromArray("<xml/>".getBytes, Mime.xml, 2, Some("data.xml")),
      DynamicFragment.fromArray(Array[Byte](0, 1, 2), Mime.octet, 3, Some("binary.bin"))
    )

    fragments(0).mime shouldBe Mime.plain
    fragments(1).mime shouldBe Mime.json
    fragments(2).mime shouldBe Mime.xml
    fragments(3).mime shouldBe Mime.octet

    fragments.map(_.name.get) shouldBe Seq("readme.txt", "config.json", "data.xml", "binary.bin")
  }
