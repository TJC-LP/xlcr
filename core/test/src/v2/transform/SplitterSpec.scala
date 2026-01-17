package com.tjclp.xlcr.v2.transform

import zio.{Chunk, ZIO}

import com.tjclp.xlcr.v2.base.V2TestSupport
import com.tjclp.xlcr.v2.types.{Content, DynamicFragment, Fragment, Mime}

/**
 * Tests for Splitter and DynamicSplitter transform types.
 */
class SplitterSpec extends V2TestSupport:

  // ============================================================================
  // Splitter Basic Tests
  // ============================================================================

  "Splitter" should "produce multiple fragments" in {
    val splitter = Splitter[Mime.Plain, Mime.Plain] { input =>
      val text = new String(input.toArray, "UTF-8")
      val paragraphs = text.split("\n\n")
      val fragments = paragraphs.zipWithIndex.map { case (para, idx) =>
        Fragment(Content.fromString(para, Mime.plain), idx, Some(s"para-${idx + 1}"))
      }
      ZIO.succeed(Chunk.fromArray(fragments))
    }

    val input = Content.fromString("First paragraph\n\nSecond paragraph\n\nThird paragraph", Mime.plain)
    val result = runZIO(splitter.split(input))

    result.size shouldBe 3
    result(0).name shouldBe Some("para-1")
    result(1).name shouldBe Some("para-2")
    result(2).name shouldBe Some("para-3")
  }

  it should "return fragments with correct indices" in {
    val splitter = Splitter[Mime.Plain, Mime.Plain] { input =>
      ZIO.succeed(Chunk(
        Fragment(Content.fromString("a", Mime.plain), 0),
        Fragment(Content.fromString("b", Mime.plain), 1),
        Fragment(Content.fromString("c", Mime.plain), 2)
      ))
    }

    val input = Content.fromString("test", Mime.plain)
    val result = runZIO(splitter.split(input))

    result(0).index shouldBe 0
    result(0).displayIndex shouldBe 1
    result(1).index shouldBe 1
    result(1).displayIndex shouldBe 2
    result(2).index shouldBe 2
    result(2).displayIndex shouldBe 3
  }

  it should "implement apply by extracting content from fragments" in {
    val splitter = Splitter[Mime.Plain, Mime.Plain] { input =>
      ZIO.succeed(Chunk(
        Fragment(Content.fromString("one", Mime.plain), 0),
        Fragment(Content.fromString("two", Mime.plain), 1)
      ))
    }

    val input = Content.fromString("test", Mime.plain)
    val result = runZIO(splitter.apply(input))

    result.size shouldBe 2
    new String(result(0).toArray, "UTF-8") shouldBe "one"
    new String(result(1).toArray, "UTF-8") shouldBe "two"
  }

  // ============================================================================
  // Splitter Factory Tests
  // ============================================================================

  "Splitter.withPriority" should "set custom priority" in {
    val highPrioritySplitter = Splitter.withPriority[Mime.Pdf, Mime.Pdf](100) { _ =>
      ZIO.succeed(Chunk.empty)
    }

    highPrioritySplitter.priority shouldBe 100
  }

  "Splitter.named" should "set name and priority" in {
    val namedSplitter = Splitter.named[Mime.Xlsx, Mime.Xlsx]("excel-sheet-splitter", prio = 50) { _ =>
      ZIO.succeed(Chunk.empty)
    }

    namedSplitter.name shouldBe "excel-sheet-splitter"
    namedSplitter.priority shouldBe 50
  }

  "Splitter.fromThrowing" should "catch exceptions" in {
    val failingSplitter = Splitter.fromThrowing[Mime.Plain, Mime.Plain] { _ =>
      throw new RuntimeException("split failed")
    }

    val input = Content.fromString("test", Mime.plain)
    val error = runZIOExpectingError(failingSplitter.split(input))

    error shouldBe a[GeneralError]
    error.message shouldBe "split failed"
  }

  it should "succeed when no exception" in {
    val splitter = Splitter.fromThrowing[Mime.Plain, Mime.Plain] { _ =>
      Chunk(Fragment(Content.fromString("success", Mime.plain), 0))
    }

    val input = Content.fromString("test", Mime.plain)
    val result = runZIO(splitter.split(input))

    result.size shouldBe 1
  }

  // ============================================================================
  // DynamicSplitter Basic Tests
  // ============================================================================

  "DynamicSplitter" should "produce fragments with varying MIME types" in {
    val dynamicSplitter = DynamicSplitter[Mime.Zip] { _ =>
      ZIO.succeed(Chunk(
        DynamicFragment.fromArray("text content".getBytes, Mime.plain, 0, Some("readme.txt")),
        DynamicFragment.fromArray("{}".getBytes, Mime.json, 1, Some("config.json")),
        DynamicFragment.fromArray("<html/>".getBytes, Mime.html, 2, Some("index.html"))
      ))
    }

    val input = Content.empty(Mime.zip)
    val result = runZIO(dynamicSplitter.splitDynamic(input))

    result.size shouldBe 3
    result(0).mime shouldBe Mime.plain
    result(1).mime shouldBe Mime.json
    result(2).mime shouldBe Mime.html
  }

  it should "implement apply by extracting content" in {
    val dynamicSplitter = DynamicSplitter[Mime.Zip] { _ =>
      ZIO.succeed(Chunk(
        DynamicFragment.fromArray("one".getBytes, Mime.plain, 0),
        DynamicFragment.fromArray("two".getBytes, Mime.json, 1)
      ))
    }

    val input = Content.empty(Mime.zip)
    val result = runZIO(dynamicSplitter.apply(input))

    result.size shouldBe 2
    result(0).mime shouldBe Mime.plain
    result(1).mime shouldBe Mime.json
  }

  // ============================================================================
  // DynamicSplitter Factory Tests
  // ============================================================================

  "DynamicSplitter.withPriority" should "set custom priority" in {
    val splitter = DynamicSplitter.withPriority[Mime.Zip](75) { _ =>
      ZIO.succeed(Chunk.empty)
    }

    splitter.priority shouldBe 75
  }

  "DynamicSplitter.named" should "set name and priority" in {
    val splitter = DynamicSplitter.named[Mime.Eml]("email-attachment-splitter", prio = 25) { _ =>
      ZIO.succeed(Chunk.empty)
    }

    splitter.name shouldBe "email-attachment-splitter"
    splitter.priority shouldBe 25
  }

  "DynamicSplitter.fromThrowing" should "catch exceptions" in {
    val failingSplitter = DynamicSplitter.fromThrowing[Mime.Zip] { _ =>
      throw new RuntimeException("extraction failed")
    }

    val input = Content.empty(Mime.zip)
    val error = runZIOExpectingError(failingSplitter.splitDynamic(input))

    error shouldBe a[GeneralError]
    error.message shouldBe "extraction failed"
  }

  // ============================================================================
  // Fragment Metadata Tests
  // ============================================================================

  "Splitter fragments" should "have correct metadata populated" in {
    val splitter = Splitter[Mime.Plain, Mime.Plain] { input =>
      val data = Chunk.fromArray("content".getBytes("UTF-8"))
      val fragment = Fragment(data, Mime.plain, 2, Some("sheet-3"), Some(5))
      ZIO.succeed(Chunk(fragment))
    }

    val input = Content.fromString("test", Mime.plain)
    val result = runZIO(splitter.split(input))

    val fragment = result.head
    fragment.content.get(Content.MetadataKeys.Index) shouldBe Some("2")
    fragment.content.get(Content.MetadataKeys.Filename) shouldBe Some("sheet-3")
    fragment.content.get(Content.MetadataKeys.Total) shouldBe Some("5")
  }

  "DynamicSplitter fragments" should "have correct metadata populated" in {
    val splitter = DynamicSplitter[Mime.Zip] { _ =>
      val data = Chunk.fromArray("content".getBytes("UTF-8"))
      val fragment = DynamicFragment(data, Mime.pdf, 3, Some("document.pdf"), Some(10))
      ZIO.succeed(Chunk(fragment))
    }

    val input = Content.empty(Mime.zip)
    val result = runZIO(splitter.splitDynamic(input))

    val fragment = result.head
    fragment.content.get(Content.MetadataKeys.Index) shouldBe Some("3")
    fragment.content.get(Content.MetadataKeys.Filename) shouldBe Some("document.pdf")
    fragment.content.get(Content.MetadataKeys.Total) shouldBe Some("10")
  }

  // ============================================================================
  // Error Handling Tests
  // ============================================================================

  "Splitter" should "propagate SplitError correctly" in {
    val failingSplitter = Splitter[Mime.Pdf, Mime.Pdf] { input =>
      ZIO.fail(SplitError("Could not split PDF", input.mime))
    }

    val input = Content.empty(Mime.pdf)
    val error = runZIOExpectingError(failingSplitter.split(input))

    error shouldBe a[SplitError]
    error.asInstanceOf[SplitError].inputMime shouldBe Mime.pdf
  }

  "DynamicSplitter" should "propagate errors correctly" in {
    val failingSplitter = DynamicSplitter[Mime.Eml] { _ =>
      ZIO.fail(ParseError("Invalid email format"))
    }

    val input = Content.empty(Mime.eml)
    val error = runZIOExpectingError(failingSplitter.splitDynamic(input))

    error shouldBe a[ParseError]
    error.message shouldBe "Invalid email format"
  }

  // ============================================================================
  // Empty Results Tests
  // ============================================================================

  "Splitter" should "handle empty results" in {
    val emptySplitter = Splitter[Mime.Plain, Mime.Plain] { _ =>
      ZIO.succeed(Chunk.empty)
    }

    val input = Content.fromString("test", Mime.plain)
    val result = runZIO(emptySplitter.split(input))

    result shouldBe empty
  }

  "DynamicSplitter" should "handle empty results" in {
    val emptySplitter = DynamicSplitter[Mime.Zip] { _ =>
      ZIO.succeed(Chunk.empty)
    }

    val input = Content.empty(Mime.zip)
    val result = runZIO(emptySplitter.splitDynamic(input))

    result shouldBe empty
  }

  // ============================================================================
  // Composition Tests
  // ============================================================================

  "Splitter" should "compose with conversions" in {
    val splitter = Splitter[Mime.Plain, Mime.Plain] { input =>
      val parts = new String(input.toArray, "UTF-8").split(",")
      ZIO.succeed(Chunk.fromArray(
        parts.zipWithIndex.map { case (p, i) =>
          Fragment(Content.fromString(p.trim, Mime.plain), i)
        }
      ))
    }

    val toUpper = Conversion.pure[Mime.Plain, Mime.Plain] { input =>
      Content.fromString(new String(input.toArray, "UTF-8").toUpperCase, Mime.plain)
    }

    val composed = splitter >>> toUpper
    val input = Content.fromString("a, b, c", Mime.plain)
    val results = runZIO(composed.apply(input))

    // Splitter produces 3 outputs, each passed through toUpper
    results.size shouldBe 3
    new String(results(0).toArray, "UTF-8") shouldBe "A"
    new String(results(1).toArray, "UTF-8") shouldBe "B"
    new String(results(2).toArray, "UTF-8") shouldBe "C"
  }
