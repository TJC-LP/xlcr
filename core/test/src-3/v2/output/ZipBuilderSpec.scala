package com.tjclp.xlcr.v2.output

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import zio.Chunk

import com.tjclp.xlcr.v2.types.{ Content, DynamicFragment, Mime }

class ZipBuilderSpec extends AnyFlatSpec with Matchers {

  // Helper to create a DynamicFragment
  private def makeFragment(
    content: String,
    index: Int,
    name: Option[String],
    mime: Mime
  ): DynamicFragment =
    DynamicFragment(
      content = Content.fromChunk(Chunk.fromArray(content.getBytes("UTF-8")), mime),
      index = index,
      name = name
    )

  // Helper to extract entry names from a ZIP
  private def extractEntryNames(zipBytes: Array[Byte]): List[String] =
    val zis     = new ZipInputStream(new ByteArrayInputStream(zipBytes))
    val builder = List.newBuilder[String]
    try
      var entry = zis.getNextEntry
      while entry != null do
        builder += entry.getName
        zis.closeEntry()
        entry = zis.getNextEntry
      builder.result()
    finally zis.close()

  // Helper to extract entry contents from a ZIP
  private def extractEntryContent(zipBytes: Array[Byte], entryName: String): Option[String] =
    val zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))
    try
      var entry = zis.getNextEntry
      while entry != null do
        if entry.getName == entryName then
          val content = new String(zis.readAllBytes(), "UTF-8")
          return Some(content)
        zis.closeEntry()
        entry = zis.getNextEntry
      None
    finally zis.close()

  // ============================================================================
  // buildZip tests
  // ============================================================================

  "buildZip" should "create a ZIP with correct entry names for small fragment count" in {
    val fragments = Seq(
      makeFragment("content1", 0, Some("Sheet 1"), Mime.xlsx),
      makeFragment("content2", 1, Some("Sheet 2"), Mime.xlsx),
      makeFragment("content3", 2, Some("Sheet 3"), Mime.xlsx)
    )

    val zipBytes = ZipBuilder.buildZip(fragments)
    val entries  = extractEntryNames(zipBytes)

    entries should contain theSameElementsInOrderAs List(
      "1__Sheet 1.xlsx",
      "2__Sheet 2.xlsx",
      "3__Sheet 3.xlsx"
    )
  }

  it should "create a ZIP with zero-padded names for larger fragment counts" in {
    val fragments = (0 until 15).map { i =>
      makeFragment(s"content$i", i, Some(s"Page ${i + 1}"), Mime.pdf)
    }

    val zipBytes = ZipBuilder.buildZip(fragments)
    val entries  = extractEntryNames(zipBytes)

    entries.head shouldBe "01__Page 1.pdf"
    entries(9) shouldBe "10__Page 10.pdf"
    entries.last shouldBe "15__Page 15.pdf"
  }

  it should "use index-based names for fragments without names" in {
    val fragments = Seq(
      makeFragment("content1", 0, None, Mime.pdf),
      makeFragment("content2", 1, None, Mime.pdf)
    )

    val zipBytes = ZipBuilder.buildZip(fragments)
    val entries  = extractEntryNames(zipBytes)

    entries should contain theSameElementsInOrderAs List(
      "1__fragment_1.pdf",
      "2__fragment_2.pdf"
    )
  }

  it should "preserve fragment content" in {
    val fragments = Seq(
      makeFragment("Hello World", 0, Some("test"), Mime.plain)
    )

    val zipBytes = ZipBuilder.buildZip(fragments)
    val content  = extractEntryContent(zipBytes, "1__test.txt")

    content shouldBe Some("Hello World")
  }

  it should "use correct extension based on MIME type" in {
    val fragments = Seq(
      makeFragment("pdf content", 0, Some("doc1"), Mime.pdf),
      makeFragment("html content", 1, Some("doc2"), Mime.html),
      makeFragment("xlsx content", 2, Some("doc3"), Mime.xlsx)
    )

    val zipBytes = ZipBuilder.buildZip(fragments)
    val entries  = extractEntryNames(zipBytes)

    entries(0) shouldBe "1__doc1.pdf"
    entries(1) shouldBe "2__doc2.html"
    entries(2) shouldBe "3__doc3.xlsx"
  }

  it should "use default extension for unknown MIME types" in {
    val unknownMime = Mime("application/x-unknown")
    val fragments = Seq(
      makeFragment("content", 0, Some("unknown"), unknownMime)
    )

    val zipBytes = ZipBuilder.buildZip(fragments, "dat")
    val entries  = extractEntryNames(zipBytes)

    entries.head shouldBe "1__unknown.dat"
  }

  // ============================================================================
  // buildZip with Chunk tests
  // ============================================================================

  "buildZip(Chunk)" should "work with ZIO Chunks" in {
    val fragments = Chunk(
      makeFragment("content1", 0, Some("Sheet 1"), Mime.xlsx),
      makeFragment("content2", 1, Some("Sheet 2"), Mime.xlsx)
    )

    val zipBytes = ZipBuilder.buildZip(fragments)
    val entries  = extractEntryNames(zipBytes)

    entries should contain theSameElementsInOrderAs List(
      "1__Sheet 1.xlsx",
      "2__Sheet 2.xlsx"
    )
  }

  // ============================================================================
  // Edge cases
  // ============================================================================

  "buildZip" should "handle empty fragment list" in {
    val zipBytes = ZipBuilder.buildZip(Seq.empty)
    val entries  = extractEntryNames(zipBytes)

    entries shouldBe empty
  }

  it should "handle fragments with special characters in names" in {
    val fragments = Seq(
      makeFragment("content", 0, Some("Sheet: Q1/Q2 <Results>"), Mime.xlsx)
    )

    val zipBytes = ZipBuilder.buildZip(fragments)
    val entries  = extractEntryNames(zipBytes)

    // Only filesystem-invalid characters (: / < >) are replaced
    entries.head shouldBe "1__Sheet_ Q1_Q2 _Results_.xlsx"
  }

  // Note: Padding logic is tested in FragmentNamingSpec.
  // We only verify ZipBuilder uses it correctly with a small count here.
}
