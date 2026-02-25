package com.tjclp.xlcr.pipeline

import zio.{Chunk, ZIO}

import com.tjclp.xlcr.base.V2TestSupport
import com.tjclp.xlcr.transform.{Conversion, DynamicSplitter, Splitter}
import com.tjclp.xlcr.types.{Content, DynamicFragment, Fragment, Mime}

/**
 * Tests for the Pipeline high-level API using compile-time transform evidence.
 */
class PipelineSpec extends V2TestSupport:

  private def stringConversion[I <: Mime, O <: Mime](
      out: O
  )(f: String => String): Conversion[I, O] =
    Conversion.pure[I, O] { input =>
      val text = new String(input.toArray, "UTF-8")
      Content.fromString(f(text), out)
    }

  // ==========================================================================
  // Pipeline.convert Tests
  // ==========================================================================

  "Pipeline.convert" should "convert content with compile-time evidence" in {
    given Conversion[Mime.Plain, Mime.Html] =
      stringConversion(Mime.html)(t => s"<html>$t</html>")

    val input = Content.fromString("hello", Mime.plain)
    val result = runZIO(Pipeline.convert[Mime.Plain, Mime.Html](input))

    new String(result.toArray, "UTF-8") shouldBe "<html>hello</html>"
    result.mime shouldBe Mime.html
  }

  it should "compose multi-hop conversions with explicit chaining" in {
    given c1: Conversion[Mime.Plain, Mime.Json] =
      stringConversion(Mime.json)(t => s"""{"text":"$t"}""")
    given c2: Conversion[Mime.Json, Mime.Xml] =
      stringConversion(Mime.xml)(t => s"<root>$t</root>")

    // Multi-hop requires explicit composition - compiler can't infer intermediate M
    val input = Content.fromString("data", Mime.plain)
    val result = runZIO(
      for
        json <- Pipeline.convert[Mime.Plain, Mime.Json](input)
        xml  <- Pipeline.convert[Mime.Json, Mime.Xml](json)
      yield xml
    )

    val text = new String(result.toArray, "UTF-8")
    text should include("<root>")
    text should include("data")
  }

  // ==========================================================================
  // Pipeline.transform Tests
  // ==========================================================================

  "Pipeline.transform" should "return identity output when I == O" in {
    // When I == O, identity transform takes precedence - use Pipeline.split for splitting
    val input = Content.fromString("pdf data", Mime.pdf)
    val results = runZIO(Pipeline.transform[Mime.Pdf, Mime.Pdf](input))

    results.size shouldBe 1
    results.head.mime shouldBe Mime.pdf
    new String(results.head.toArray, "UTF-8") shouldBe "pdf data"
  }

  // ==========================================================================
  // Pipeline.split Tests
  // ==========================================================================

  "Pipeline.split" should "preserve fragment names from typed splitters" in {
    given Splitter[Mime.Pptx, Mime.Pptx] = Splitter { _ =>
      ZIO.succeed(Chunk(
        Fragment(Content.fromString("slide1", Mime.pptx), 0, Some("Title Slide")),
        Fragment(Content.fromString("slide2", Mime.pptx), 1, Some("Agenda"))
      ))
    }

    val input = Content.fromString("pptx", Mime.pptx)
    val fragments = runZIO(Pipeline.split[Mime.Pptx, Mime.Pptx](input))

    fragments.map(_.name.getOrElse("")) shouldBe Chunk("Title Slide", "Agenda")
  }

  "Pipeline.splitDynamic" should "return dynamic fragments when a DynamicSplitter is in scope" in {
    given DynamicSplitter[Mime.Zip] = DynamicSplitter { _ =>
      ZIO.succeed(Chunk(
        DynamicFragment.fromArray("file1".getBytes("UTF-8"), Mime.plain, 0, Some("a.txt")),
        DynamicFragment.fromArray("{}".getBytes("UTF-8"), Mime.json, 1, Some("b.json"))
      ))
    }

    val input = Content.fromString("zip", Mime.zip)
    val fragments = runZIO(Pipeline.splitDynamic[Mime.Zip](input))

    fragments.size shouldBe 2
    fragments(0).mime shouldBe Mime.plain
    fragments(1).mime shouldBe Mime.json
  }

  // ==========================================================================
  // Streaming Tests
  // ==========================================================================

  "Pipeline.stream" should "stream conversion results" in {
    given Conversion[Mime.Plain, Mime.Html] =
      stringConversion(Mime.html)(t => s"<p>$t</p>")

    val input = Content.fromString("test", Mime.plain)
    val stream = Pipeline.stream[Mime.Plain, Mime.Html](input)

    val results = runZIO(stream.runCollect)
    results.size shouldBe 1
  }

  // ==========================================================================
  // Batch Operations Tests
  // ==========================================================================

  "Pipeline.convertAll" should "convert multiple inputs in parallel" in {
    given Conversion[Mime.Plain, Mime.Html] =
      stringConversion(Mime.html)(t => s"<p>$t</p>")

    val inputs = Chunk(
      Content.fromString("one", Mime.plain),
      Content.fromString("two", Mime.plain),
      Content.fromString("three", Mime.plain)
    )

    val results = runZIO(Pipeline.convertAll[Mime.Plain, Mime.Html](inputs))

    results.size shouldBe 3
    results.foreach(_.mime shouldBe Mime.html)
  }

  "Pipeline.convertAllSeq" should "convert multiple inputs sequentially" in {
    given Conversion[Mime.Plain, Mime.Json] =
      stringConversion(Mime.json)(t => s"""{"v":"$t"}""")

    val inputs = Chunk(
      Content.fromString("a", Mime.plain),
      Content.fromString("b", Mime.plain)
    )

    val results = runZIO(Pipeline.convertAllSeq[Mime.Plain, Mime.Json](inputs))

    results.size shouldBe 2
    results.foreach(_.mime shouldBe Mime.json)
  }

  // ==========================================================================
  // Pipeline Builder Tests
  // ==========================================================================

  "Pipeline.from" should "create a pipeline builder" in {
    given Conversion[Mime.Pdf, Mime.Html] =
      stringConversion(Mime.html)(t => s"<html>$t</html>")

    val pipeline = Pipeline.from(Mime.pdf).to(Mime.html)

    pipeline.getInputMime shouldBe Mime.pdf
    pipeline.getOutputMime shouldBe Mime.html
  }

  "BuiltPipeline.run" should "execute the pipeline" in {
    given Conversion[Mime.Plain, Mime.Html] =
      stringConversion(Mime.html)(t => s"<html>$t</html>")

    val pipeline = Pipeline.from(Mime.plain).to(Mime.html)
    val input = Content.fromString("test", Mime.plain)
    val result = runZIO(pipeline.run(input))

    new String(result.toArray, "UTF-8") shouldBe "<html>test</html>"
  }

  "BuiltPipeline.runAll" should "return all results from pipeline" in {
    given Conversion[Mime.Plain, Mime.Html] =
      stringConversion(Mime.html)(t => s"<p>$t</p>")

    val pipeline = Pipeline.from(Mime.plain).to(Mime.html)
    val input = Content.fromString("test", Mime.plain)
    val results = runZIO(pipeline.runAll(input))

    results.size shouldBe 1
    new String(results.head.toArray, "UTF-8") should include("<p>test</p>")
  }

  "BuiltPipeline.runBatch" should "process multiple inputs" in {
    given Conversion[Mime.Json, Mime.Plain] =
      stringConversion(Mime.plain)(_ => "converted")

    val pipeline = Pipeline.from(Mime.json).to(Mime.plain)
    val inputs = Chunk(
      Content.fromString("{}", Mime.json),
      Content.fromString("[]", Mime.json)
    )

    val results = runZIO(pipeline.runBatch(inputs))

    results.size shouldBe 2
    results.foreach(r => new String(r.toArray, "UTF-8") shouldBe "converted")
  }

  "BuiltPipeline.getTransform" should "return the underlying transform" in {
    given Conversion[Mime.Xml, Mime.Json] =
      stringConversion(Mime.json)(identity)

    val pipeline = Pipeline.from(Mime.xml).to(Mime.json)
    val transform = pipeline.getTransform

    transform should not be null
  }

  "BuiltPipeline.runStream" should "produce stream of results" in {
    given Conversion[Mime.Csv, Mime.Json] =
      stringConversion(Mime.json)(t => s"""{"data":"$t"}""")

    val pipeline = Pipeline.from(Mime.csv).to(Mime.json)
    val input = Content.fromString("a,b,c", Mime.csv)
    val stream = pipeline.runStream(input)

    val results = runZIO(stream.runCollect)
    results.size should be >= 1
  }
