package com.tjclp.xlcr.v2.types

import zio.Chunk

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Tests for the Content type-safe wrapper.
 */
class ContentSpec extends AnyFlatSpec with Matchers:

  // ============================================================================
  // Construction Tests
  // ============================================================================

  "Content" should "be created from byte array" in {
    val data = "Hello, World!".getBytes("UTF-8")
    val content = Content(data, Mime.plain)

    content.size shouldBe data.length
    content.mime shouldBe Mime.plain
    new String(content.toArray, "UTF-8") shouldBe "Hello, World!"
  }

  it should "be created from ZIO Chunk" in {
    val data = Chunk.fromArray("Test data".getBytes("UTF-8"))
    val content = Content.fromChunk(data, Mime.plain)

    content.size shouldBe data.length
    content.data shouldBe data
  }

  it should "be created from string with UTF-8 encoding" in {
    val content = Content.fromString("Hello, World!", Mime.plain)

    new String(content.toArray, "UTF-8") shouldBe "Hello, World!"
    content.mime shouldBe Mime.plain
  }

  it should "be created from string with specified charset" in {
    val content = Content.fromString("Hello", Mime.plain, "UTF-16")

    content.charset shouldBe Some("UTF-16")
    new String(content.toArray, "UTF-16") shouldBe "Hello"
  }

  it should "be created empty" in {
    val content = Content.empty(Mime.pdf)

    content.isEmpty shouldBe true
    content.size shouldBe 0
    content.mime shouldBe Mime.pdf
  }

  it should "preserve type parameter through operations" in {
    // This should compile - demonstrating type preservation
    val pdfContent: Content[Mime.Pdf] = Content("test".getBytes, Mime.pdf)
    val updated: Content[Mime.Pdf] = pdfContent.withMetadata("key", "value")

    updated.mime shouldBe Mime.pdf
  }

  // ============================================================================
  // Size and Empty Tests
  // ============================================================================

  "Content.size" should "return correct byte count" in {
    val content = Content("test".getBytes, Mime.plain)
    content.size shouldBe 4
  }

  "Content.isEmpty" should "return true for empty content" in {
    Content.empty(Mime.plain).isEmpty shouldBe true
    Content(Array.empty[Byte], Mime.plain).isEmpty shouldBe true
  }

  it should "return false for non-empty content" in {
    Content("x".getBytes, Mime.plain).isEmpty shouldBe false
  }

  "Content.nonEmpty" should "return true for non-empty content" in {
    Content("x".getBytes, Mime.plain).nonEmpty shouldBe true
  }

  it should "return false for empty content" in {
    Content.empty(Mime.plain).nonEmpty shouldBe false
  }

  // ============================================================================
  // Metadata Tests
  // ============================================================================

  "Content.get" should "return metadata value when present" in {
    val content = Content("test".getBytes, Mime.plain, Map("key" -> "value"))
    content.get("key") shouldBe Some("value")
  }

  it should "return None when key not present" in {
    val content = Content("test".getBytes, Mime.plain)
    content.get("missing") shouldBe None
  }

  "Content.withMetadata" should "add single metadata entry" in {
    val content = Content("test".getBytes, Mime.plain)
      .withMetadata("filename", "test.txt")

    content.get("filename") shouldBe Some("test.txt")
  }

  it should "update existing metadata entry" in {
    val content = Content("test".getBytes, Mime.plain, Map("key" -> "old"))
      .withMetadata("key", "new")

    content.get("key") shouldBe Some("new")
  }

  it should "add multiple metadata entries" in {
    val content = Content("test".getBytes, Mime.plain)
      .withMetadata("key1" -> "value1", "key2" -> "value2", "key3" -> "value3")

    content.get("key1") shouldBe Some("value1")
    content.get("key2") shouldBe Some("value2")
    content.get("key3") shouldBe Some("value3")
  }

  "Content.withoutMetadata" should "remove metadata entry" in {
    val content = Content("test".getBytes, Mime.plain, Map("key" -> "value"))
      .withoutMetadata("key")

    content.get("key") shouldBe None
  }

  it should "not fail when removing non-existent key" in {
    val content = Content("test".getBytes, Mime.plain)
      .withoutMetadata("missing")

    content.get("missing") shouldBe None
  }

  // ============================================================================
  // Convenience Accessor Tests
  // ============================================================================

  "Content.filename" should "return filename from metadata" in {
    val content = Content("test".getBytes, Mime.plain)
      .withMetadata(Content.MetadataKeys.Filename, "document.pdf")

    content.filename shouldBe Some("document.pdf")
  }

  it should "return None when not set" in {
    val content = Content("test".getBytes, Mime.plain)
    content.filename shouldBe None
  }

  "Content.charset" should "return charset from metadata" in {
    val content = Content("test".getBytes, Mime.plain)
      .withMetadata(Content.MetadataKeys.Charset, "UTF-16")

    content.charset shouldBe Some("UTF-16")
  }

  "Content.sourcePath" should "return source path from metadata" in {
    val content = Content("test".getBytes, Mime.plain)
      .withMetadata(Content.MetadataKeys.SourcePath, "/path/to/file.txt")

    content.sourcePath shouldBe Some("/path/to/file.txt")
  }

  // ============================================================================
  // Data Modification Tests
  // ============================================================================

  "Content.withData" should "replace data with new Chunk" in {
    val original = Content("original".getBytes, Mime.plain, Map("key" -> "value"))
    val newData = Chunk.fromArray("new data".getBytes("UTF-8"))
    val updated = original.withData(newData)

    new String(updated.toArray, "UTF-8") shouldBe "new data"
    updated.get("key") shouldBe Some("value") // Metadata preserved
    updated.mime shouldBe Mime.plain // MIME preserved
  }

  it should "replace data with new byte array" in {
    val original = Content("original".getBytes, Mime.plain)
    val updated = original.withData("new data".getBytes("UTF-8"))

    new String(updated.toArray, "UTF-8") shouldBe "new data"
  }

  "Content.toArray" should "return byte array copy" in {
    val original = "test data".getBytes("UTF-8")
    val content = Content(original, Mime.plain)
    val array = content.toArray

    array shouldBe original
    // Should be a copy, not the same reference
    array should not be theSameInstanceAs(original)
  }

  // ============================================================================
  // MetadataKeys Tests
  // ============================================================================

  "Content.MetadataKeys" should "have standard key names" in {
    Content.MetadataKeys.Filename shouldBe "filename"
    Content.MetadataKeys.Charset shouldBe "charset"
    Content.MetadataKeys.SourcePath shouldBe "source-path"
    Content.MetadataKeys.PageCount shouldBe "page-count"
    Content.MetadataKeys.SheetCount shouldBe "sheet-count"
    Content.MetadataKeys.SlideCount shouldBe "slide-count"
    Content.MetadataKeys.Index shouldBe "index"
    Content.MetadataKeys.Total shouldBe "total"
    Content.MetadataKeys.Title shouldBe "title"
    Content.MetadataKeys.Author shouldBe "author"
    Content.MetadataKeys.CreatedAt shouldBe "created-at"
    Content.MetadataKeys.ModifiedAt shouldBe "modified-at"
  }

  // ============================================================================
  // Type Preservation Tests
  // ============================================================================

  "Content operations" should "preserve type parameter" in {
    // Type-safe content with specific MIME type
    val pdfContent: Content[Mime.Pdf] = Content("test".getBytes, Mime.pdf)

    // All operations should return same type
    val withMeta: Content[Mime.Pdf] = pdfContent.withMetadata("key", "value")
    val withData: Content[Mime.Pdf] = pdfContent.withData("new".getBytes)
    val withoutMeta: Content[Mime.Pdf] = pdfContent.withoutMetadata("key")

    withMeta.mime shouldBe Mime.pdf
    withData.mime shouldBe Mime.pdf
    withoutMeta.mime shouldBe Mime.pdf
  }

  // ============================================================================
  // Edge Cases
  // ============================================================================

  "Content" should "handle binary data" in {
    val binaryData = Array[Byte](0x00, 0x01, 0x02, 0xFF.toByte, 0xFE.toByte)
    val content = Content(binaryData, Mime.octet)

    content.toArray shouldBe binaryData
  }

  it should "handle large data" in {
    val largeData = new Array[Byte](1024 * 1024) // 1 MB
    java.util.Arrays.fill(largeData, 0x42.toByte)
    val content = Content(largeData, Mime.octet)

    content.size shouldBe 1024 * 1024
    content.toArray(0) shouldBe 0x42.toByte
  }

  it should "handle unicode text" in {
    val unicodeText = "Hello, 世界! 🌍 Привет"
    val content = Content.fromString(unicodeText, Mime.plain)

    new String(content.toArray, "UTF-8") shouldBe unicodeText
  }
