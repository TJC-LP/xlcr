package com.tjclp.xlcr.v2.pipeline

import zio.{Chunk, ZIO}

import com.tjclp.xlcr.v2.base.V2TestSupport
import com.tjclp.xlcr.v2.registry.TransformRegistry
import com.tjclp.xlcr.v2.transform.{Conversion, DynamicSplitter, Splitter, UnsupportedConversion}
import com.tjclp.xlcr.v2.types.{Content, DynamicFragment, Fragment, Mime}

/**
 * Tests for the Pipeline high-level API.
 */
class PipelineSpec extends V2TestSupport:

  // Helper to register a conversion
  private def registerConversion(from: Mime, to: Mime, transform: String => String = identity): Unit =
    val conversion = Conversion.pure[Mime, Mime] { input =>
      val text = new String(input.toArray, "UTF-8")
      Content.fromString(transform(text), to)
    }
    TransformRegistry.registerConversion(from, to, conversion)

  // ============================================================================
  // Pipeline.convert Tests
  // ============================================================================

  "Pipeline.convert" should "convert content using best path" in {
    registerConversion(Mime.plain, Mime.html, t => s"<html>$t</html>")

    val input: Content[Mime] = Content.fromString("hello", Mime.plain)
    val result = runZIO(Pipeline.convert(input, Mime.html))

    new String(result.toArray, "UTF-8") shouldBe "<html>hello</html>"
    result.mime shouldBe Mime.html
  }

  it should "use multi-hop path when needed" in {
    registerConversion(Mime.plain, Mime.json, t => s"""{"text":"$t"}""")
    registerConversion(Mime.json, Mime.xml, t => s"<root>$t</root>")

    val input: Content[Mime] = Content.fromString("data", Mime.plain)
    val result = runZIO(Pipeline.convert(input, Mime.xml))

    val text = new String(result.toArray, "UTF-8")
    text should include("<root>")
    text should include("text")
    text should include("data")
  }

  it should "fail with UnsupportedConversion when no path exists" in {
    val input: Content[Mime] = Content.fromString("test", Mime.mp3)
    val error = runZIOExpectingError(Pipeline.convert(input, Mime.jpeg))

    error shouldBe a[UnsupportedConversion]
    error.asInstanceOf[UnsupportedConversion].from shouldBe Mime.mp3
    error.asInstanceOf[UnsupportedConversion].to shouldBe Mime.jpeg
  }

  it should "handle identity conversion" in {
    val input: Content[Mime] = Content.fromString("test", Mime.plain)
    val result = runZIO(Pipeline.convert(input, Mime.plain))

    result shouldBe input
  }

  // ============================================================================
  // Pipeline.transform Tests
  // ============================================================================

  "Pipeline.transform" should "use conversion path for transform" in {
    // Register a conversion that transforms content
    val conversion = Conversion.pure[Mime.Plain, Mime.Html] { input =>
      Content.fromString(s"<p>${new String(input.toArray, "UTF-8")}</p>", Mime.html)
    }
    TransformRegistry.registerConversion(Mime.plain, Mime.html, conversion)

    val input: Content[Mime] = Content.fromString("test", Mime.plain)
    val results = runZIO(Pipeline.transform(input, Mime.html))

    // Conversions return a single result
    results.size shouldBe 1
    new String(results.head.toArray, "UTF-8") should include("<p>test</p>")
  }

  // ============================================================================
  // Pipeline.split Tests
  // ============================================================================

  "Pipeline.split" should "use dynamic splitter when available" in {
    val splitter = DynamicSplitter[Mime.Zip] { _ =>
      ZIO.succeed(Chunk(
        DynamicFragment.fromArray("file1".getBytes, Mime.plain, 0, Some("a.txt")),
        DynamicFragment.fromArray("{}".getBytes, Mime.json, 1, Some("b.json"))
      ))
    }
    TransformRegistry.registerDynamicSplitter(Mime.zip, splitter)

    val input: Content[Mime] = Content.empty(Mime.zip)
    val fragments = runZIO(Pipeline.split(input))

    fragments.size shouldBe 2
    fragments(0).mime shouldBe Mime.plain
    fragments(1).mime shouldBe Mime.json
  }

  it should "fall back to typed splitter when no dynamic splitter" in {
    val splitter = Splitter[Mime.Pdf, Mime.Pdf] { _ =>
      ZIO.succeed(Chunk(
        Fragment(Content.fromString("page1", Mime.pdf), 0, Some("page-1")),
        Fragment(Content.fromString("page2", Mime.pdf), 1, Some("page-2"))
      ))
    }
    TransformRegistry.registerSplitter(Mime.pdf, Mime.pdf, splitter)

    val input: Content[Mime] = Content.empty(Mime.pdf)
    val fragments = runZIO(Pipeline.split(input))

    fragments.size shouldBe 2
  }

  it should "fail when no splitter available" in {
    val input: Content[Mime] = Content.empty(Mime.mp4)
    val error = runZIOExpectingError(Pipeline.split(input))

    error shouldBe a[UnsupportedConversion]
  }

  // ============================================================================
  // Pipeline.canConvert Tests
  // ============================================================================

  "Pipeline.canConvert" should "return true for identity" in {
    Pipeline.canConvert(Mime.pdf, Mime.pdf) shouldBe true
  }

  it should "return true for registered conversion" in {
    registerConversion(Mime.docx, Mime.pdf)

    Pipeline.canConvert(Mime.docx, Mime.pdf) shouldBe true
  }

  it should "return true for multi-hop path" in {
    registerConversion(Mime.xlsx, Mime.csv)
    registerConversion(Mime.csv, Mime.plain)

    Pipeline.canConvert(Mime.xlsx, Mime.plain) shouldBe true
  }

  it should "return false when no path" in {
    Pipeline.canConvert(Mime.wav, Mime.png) shouldBe false
  }

  // ============================================================================
  // Pipeline.describePath Tests
  // ============================================================================

  "Pipeline.describePath" should "describe available path" in {
    registerConversion(Mime.html, Mime.pdf)

    val description = Pipeline.describePath(Mime.html, Mime.pdf)

    description should include("html")
    description should include("pdf")
  }

  it should "indicate when no path exists" in {
    val description = Pipeline.describePath(Mime.flac, Mime.xlsx)

    description should include("No path found")
  }

  // ============================================================================
  // Batch Operations Tests
  // ============================================================================

  "Pipeline.convertAll" should "convert multiple inputs in parallel" in {
    registerConversion(Mime.plain, Mime.html, t => s"<p>$t</p>")

    val inputs: Chunk[Content[Mime]] = Chunk(
      Content.fromString("one", Mime.plain),
      Content.fromString("two", Mime.plain),
      Content.fromString("three", Mime.plain)
    )

    val results = runZIO(Pipeline.convertAll(inputs, Mime.html))

    results.size shouldBe 3
    results.foreach(_.mime shouldBe Mime.html)
  }

  "Pipeline.convertAllSeq" should "convert multiple inputs sequentially" in {
    registerConversion(Mime.plain, Mime.json, t => s"""{"v":"$t"}""")

    val inputs: Chunk[Content[Mime]] = Chunk(
      Content.fromString("a", Mime.plain),
      Content.fromString("b", Mime.plain)
    )

    val results = runZIO(Pipeline.convertAllSeq(inputs, Mime.json))

    results.size shouldBe 2
    results.foreach(_.mime shouldBe Mime.json)
  }

  // ============================================================================
  // Pipeline Builder Tests
  // ============================================================================

  "Pipeline.from" should "create a pipeline builder" in {
    registerConversion(Mime.pdf, Mime.html)

    val pipeline = Pipeline.from(Mime.pdf).to(Mime.html)

    pipeline.getInputMime shouldBe Mime.pdf
    pipeline.getOutputMime shouldBe Mime.html
  }

  "PipelineBuilder.via" should "add intermediate format" in {
    registerConversion(Mime.pdf, Mime.html)
    registerConversion(Mime.html, Mime.plain)

    val pipeline = Pipeline
      .from(Mime.pdf)
      .via(Mime.html)
      .to(Mime.plain)

    pipeline.getInputMime shouldBe Mime.pdf
    pipeline.getOutputMime shouldBe Mime.plain
  }

  it should "allow multiple intermediate formats" in {
    registerConversion(Mime.docx, Mime.html)
    registerConversion(Mime.html, Mime.plain)
    registerConversion(Mime.plain, Mime.csv)

    val pipeline = Pipeline
      .from(Mime.docx)
      .via(Mime.html)
      .via(Mime.plain)
      .to(Mime.csv)

    pipeline.getOutputMime shouldBe Mime.csv
  }

  // ============================================================================
  // BuiltPipeline Tests
  // ============================================================================

  "BuiltPipeline.run" should "execute the pipeline" in {
    registerConversion(Mime.plain, Mime.html, t => s"<html>$t</html>")

    val pipeline = Pipeline.from(Mime.plain).to(Mime.html)
    val input = Content.fromString("test", Mime.plain)
    val result = runZIO(pipeline.run(input))

    new String(result.toArray, "UTF-8") shouldBe "<html>test</html>"
  }

  "BuiltPipeline.runAll" should "return all results from pipeline" in {
    // Register a conversion
    val conversion = Conversion.pure[Mime.Plain, Mime.Html] { input =>
      Content.fromString(s"<p>${new String(input.toArray, "UTF-8")}</p>", Mime.html)
    }
    TransformRegistry.registerConversion(Mime.plain, Mime.html, conversion)

    val pipeline = Pipeline.from(Mime.plain).to(Mime.html)
    val input: Content[Mime.Plain] = Content.fromString("test", Mime.plain)
    val results = runZIO(pipeline.runAll(input))

    results.size shouldBe 1
    new String(results.head.toArray, "UTF-8") should include("<p>test</p>")
  }

  "BuiltPipeline.runBatch" should "process multiple inputs" in {
    registerConversion(Mime.json, Mime.plain, _ => "converted")

    val pipeline = Pipeline.from(Mime.json).to(Mime.plain)
    val inputs = Chunk(
      Content.fromString("{}", Mime.json),
      Content.fromString("[]", Mime.json)
    )

    val results = runZIO(pipeline.runBatch(inputs))

    results.size shouldBe 2
    results.foreach(r => new String(r.toArray) shouldBe "converted")
  }

  "BuiltPipeline.getTransform" should "return the underlying transform" in {
    registerConversion(Mime.xml, Mime.json)

    val pipeline = Pipeline.from(Mime.xml).to(Mime.json)
    val transform = pipeline.getTransform

    transform should not be null
  }

  // ============================================================================
  // Error Handling Tests
  // ============================================================================

  "BuiltPipeline" should "fail when via step has no path" in {
    // No conversion from pdf to xlsx registered
    val pipeline = Pipeline.from(Mime.pdf).via(Mime.xlsx).to(Mime.html)

    val input = Content.fromString("test", Mime.pdf)
    val error = runZIOExpectingError(pipeline.run(input))

    error shouldBe a[UnsupportedConversion]
  }

  it should "fail when final step has no path" in {
    registerConversion(Mime.docx, Mime.html)
    // No conversion from html to mp3

    val pipeline = Pipeline.from(Mime.docx).via(Mime.html).to(Mime.mp3)

    val input = Content.fromString("test", Mime.docx)
    val error = runZIOExpectingError(pipeline.run(input))

    error shouldBe a[UnsupportedConversion]
  }

  // ============================================================================
  // Streaming Tests
  // ============================================================================

  "Pipeline.stream" should "produce stream of results" in {
    registerConversion(Mime.plain, Mime.html, t => s"<p>$t</p>")

    val input: Content[Mime] = Content.fromString("test", Mime.plain)
    val stream = Pipeline.stream(input, Mime.html)

    val results = runZIO(stream.runCollect)
    results.size shouldBe 1
  }

  "BuiltPipeline.runStream" should "produce stream of results" in {
    registerConversion(Mime.csv, Mime.json)

    val pipeline = Pipeline.from(Mime.csv).to(Mime.json)
    val input = Content.fromString("a,b,c", Mime.csv)
    val stream = pipeline.runStream(input)

    val results = runZIO(stream.runCollect)
    results.size should be >= 1
  }
