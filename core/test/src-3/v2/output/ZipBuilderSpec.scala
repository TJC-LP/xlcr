package com.tjclp.xlcr.v2.output

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Smoke tests for ZipBuilder.
 *
 * Naming logic is tested in FragmentNamingSpec. These tests just verify ZipBuilder produces valid
 * ZIPs with correct structure.
 */
class ZipBuilderSpec extends AnyFlatSpec with Matchers {

  import com.tjclp.xlcr.v2.types.{ Content, DynamicFragment, Mime }

  private def makeFragment(name: String, mime: Mime): DynamicFragment =
    DynamicFragment(
      content = Content(zio.Chunk.fromArray("test".getBytes), mime),
      index = 0,
      name = Some(name)
    )

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

  "ZipBuilder" should "create a valid ZIP with correct naming" in {
    val fragments = Seq(
      makeFragment("Sheet 1", Mime.xlsx),
      makeFragment("Sheet 2", Mime.xlsx)
    )

    val zipBytes = ZipBuilder.buildZip(fragments)
    val entries  = extractEntryNames(zipBytes)

    entries shouldBe List("1__Sheet 1.xlsx", "2__Sheet 2.xlsx")
  }

  it should "handle empty fragment list" in {
    val zipBytes = ZipBuilder.buildZip(Seq.empty)
    val entries  = extractEntryNames(zipBytes)

    entries shouldBe empty
  }
}
