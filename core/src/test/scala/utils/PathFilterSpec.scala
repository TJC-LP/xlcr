package com.tjclp.xlcr
package utils

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PathFilterSpec extends AnyFlatSpec with Matchers {

  "PathFilter" should "identify macOS metadata files" in {
    PathFilter.isMacOsMetadata("__MACOSX/file.txt") shouldBe true
    PathFilter.isMacOsMetadata("folder/__MACOSX/file.txt") shouldBe true
    PathFilter.isMacOsMetadata("._file.txt") shouldBe true
    PathFilter.isMacOsMetadata("folder/._file.txt") shouldBe true
    PathFilter.isMacOsMetadata(".DS_Store") shouldBe true
    PathFilter.isMacOsMetadata("folder/.DS_Store") shouldBe true
  }

  it should "not identify regular files as macOS metadata" in {
    PathFilter.isMacOsMetadata("file.txt") shouldBe false
    PathFilter.isMacOsMetadata("folder/file.txt") shouldBe false
    PathFilter.isMacOsMetadata("MACOSX/file.txt") shouldBe false
    PathFilter.isMacOsMetadata("_file.txt") shouldBe false
  }

  it should "clean paths for display correctly" in {
    PathFilter.cleanPathForDisplay("folder/file.txt") shouldBe "file.txt"
    PathFilter.cleanPathForDisplay("folder/subfolder/file.txt") shouldBe "file.txt"
    PathFilter.cleanPathForDisplay("._file.txt") shouldBe "file.txt"
    PathFilter.cleanPathForDisplay("folder/._file.txt") shouldBe "file.txt"
  }

  it should "normalize path separators" in {
    PathFilter.normalizeSeparators("folder\\file.txt") shouldBe "folder/file.txt"
    PathFilter.normalizeSeparators("folder\\subfolder\\file.txt") shouldBe "folder/subfolder/file.txt"
    PathFilter.normalizeSeparators("folder/file.txt") shouldBe "folder/file.txt"
  }

  it should "filter paths correctly" in {
    val paths = Seq(
      "file.txt",
      "folder/file.doc",
      "__MACOSX/file.xlsx",
      "folder/__MACOSX/file.pptx",
      "folder/._file.pdf",
      ".DS_Store",
      "folder/.DS_Store"
    )

    val filtered = PathFilter.filterPaths(paths)
    filtered should contain ("file.txt")
    filtered should contain ("folder/file.doc")
    filtered should not contain "__MACOSX/file.xlsx"
    filtered should not contain "folder/__MACOSX/file.pptx"
    filtered should not contain "folder/._file.pdf"
    filtered should not contain ".DS_Store"
    filtered should not contain "folder/.DS_Store"
  }

  it should "filter paths with additional filters" in {
    val paths = Seq(
      "file.txt",
      "temp.tmp",
      "backup.bak",
      "document.doc"
    )

    val filtered = PathFilter.filterPaths(
      paths,
      includeMacOsFilter = true,
      additionalFilters = Seq("\\.tmp$", "\\.bak$")
    )
    
    filtered should contain ("file.txt")
    filtered should contain ("document.doc")
    filtered should not contain "temp.tmp"
    filtered should not contain "backup.bak"
  }
}