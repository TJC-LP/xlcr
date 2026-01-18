package com.tjclp.xlcr.v2.transform

import zio.ZIO

import com.tjclp.xlcr.v2.base.V2TestSupport
import com.tjclp.xlcr.v2.types.{Content, Mime}

/**
 * Tests for the Conversion transform type.
 */
class ConversionSpec extends V2TestSupport:

  // ============================================================================
  // Basic Conversion Tests
  // ============================================================================

  "Conversion" should "produce exactly one output" in {
    val conversion = Conversion[Mime.Plain, Mime.Html] { input =>
      val text = new String(input.toArray, "UTF-8")
      ZIO.succeed(Content.fromString(s"<html><body>$text</body></html>", Mime.html))
    }

    val input = Content.fromString("Hello", Mime.plain)
    val result = runZIO(conversion.apply(input))

    result.size shouldBe 1
    new String(result.head.toArray, "UTF-8") should include("Hello")
  }

  it should "use convert method for implementation" in {
    val conversion = Conversion[Mime.Plain, Mime.Json] { input =>
      val text = new String(input.toArray, "UTF-8")
      ZIO.succeed(Content.fromString(s"""{"text":"$text"}""", Mime.json))
    }

    val input = Content.fromString("test", Mime.plain)
    val result = runZIO(conversion.convert(input))

    new String(result.toArray, "UTF-8") shouldBe """{"text":"test"}"""
    result.mime shouldBe Mime.json
  }

  // ============================================================================
  // Factory Method Tests
  // ============================================================================

  "Conversion.pure" should "wrap synchronous function" in {
    val conversion = Conversion.pure[Mime.Plain, Mime.Plain] { input =>
      val text = new String(input.toArray, "UTF-8").toUpperCase
      Content.fromString(text, Mime.plain)
    }

    val input = Content.fromString("hello", Mime.plain)
    val result = runZIO(conversion.convert(input))

    new String(result.toArray, "UTF-8") shouldBe "HELLO"
  }

  it should "not catch exceptions (propagates as FiberFailure)" in {
    val conversion = Conversion.pure[Mime.Plain, Mime.Plain] { _ =>
      throw new RuntimeException("test error")
    }

    val input = Content.fromString("test", Mime.plain)

    // pure wraps with ZIO.succeed, so exception propagates as FiberFailure
    val thrown = the[zio.FiberFailure] thrownBy runZIO(conversion.convert(input))
    thrown.getCause shouldBe a[RuntimeException]
  }

  "Conversion.fromThrowing" should "catch exceptions and convert to TransformError" in {
    val conversion = Conversion.fromThrowing[Mime.Plain, Mime.Plain] { _ =>
      throw new RuntimeException("test error")
    }

    val input = Content.fromString("test", Mime.plain)
    val error = runZIOExpectingError(conversion.convert(input))

    error shouldBe a[GeneralError]
    error.message shouldBe "test error"
  }

  it should "succeed when no exception thrown" in {
    val conversion = Conversion.fromThrowing[Mime.Plain, Mime.Plain] { input =>
      Content.fromString("success", Mime.plain)
    }

    val input = Content.fromString("test", Mime.plain)
    val result = runZIO(conversion.convert(input))

    new String(result.toArray, "UTF-8") shouldBe "success"
  }

  "Conversion.attempt" should "handle Either correctly" in {
    val successConversion = Conversion.attempt[Mime.Plain, Mime.Plain] { input =>
      Right(Content.fromString("success", Mime.plain))
    }

    val failConversion = Conversion.attempt[Mime.Plain, Mime.Plain] { _ =>
      Left(ParseError("parse failed"))
    }

    val input = Content.fromString("test", Mime.plain)

    new String(runZIO(successConversion.convert(input)).toArray, "UTF-8") shouldBe "success"

    val error = runZIOExpectingError(failConversion.convert(input))
    error shouldBe a[ParseError]
    error.message shouldBe "parse failed"
  }

  "Conversion.identity" should "return same content unchanged" in {
    val identity = Conversion.identity[Mime.Plain]

    val input = Content.fromString("test data", Mime.plain)
      .withMetadata("key", "value")

    val result = runZIO(identity.convert(input))

    result shouldBe input
    result.get("key") shouldBe Some("value")
  }

  it should "have name 'identity'" in {
    val identity = Conversion.identity[Mime.Pdf]
    identity.name shouldBe "identity"
  }

  // ============================================================================
  // Priority Tests
  // ============================================================================

  "Conversion.withPriority" should "set custom priority" in {
    val lowPriority = Conversion.withPriority[Mime.Plain, Mime.Html](10) { input =>
      ZIO.succeed(Content.fromString("<html>low</html>", Mime.html))
    }

    val highPriority = Conversion.withPriority[Mime.Plain, Mime.Html](100) { input =>
      ZIO.succeed(Content.fromString("<html>high</html>", Mime.html))
    }

    lowPriority.priority shouldBe 10
    highPriority.priority shouldBe 100
  }

  "Conversion" should "have default priority 0" in {
    val conversion = Conversion[Mime.Plain, Mime.Html] { input =>
      ZIO.succeed(Content.fromString("<html></html>", Mime.html))
    }

    conversion.priority shouldBe 0
  }

  // ============================================================================
  // Named Conversion Tests
  // ============================================================================

  "Conversion.named" should "set name and priority" in {
    val conversion = Conversion.named[Mime.Plain, Mime.Html]("text-to-html", prio = 50) { input =>
      ZIO.succeed(Content.fromString("<html></html>", Mime.html))
    }

    conversion.name shouldBe "text-to-html"
    conversion.priority shouldBe 50
  }

  it should "default priority to 0" in {
    val conversion = Conversion.named[Mime.Plain, Mime.Html]("my-conversion") { input =>
      ZIO.succeed(Content.fromString("<html></html>", Mime.html))
    }

    conversion.priority shouldBe 0
  }

  // ============================================================================
  // Composition Tests
  // ============================================================================

  "Conversion" should "compose with andThen" in {
    val toUpper = Conversion.pure[Mime.Plain, Mime.Plain] { input =>
      Content.fromString(new String(input.toArray, "UTF-8").toUpperCase, Mime.plain)
    }

    val toJson = Conversion.pure[Mime.Plain, Mime.Json] { input =>
      val text = new String(input.toArray, "UTF-8")
      Content.fromString(s"""{"text":"$text"}""", Mime.json)
    }

    val composed = toUpper.andThen(toJson)
    val input = Content.fromString("hello", Mime.plain)
    val results = runZIO(composed.apply(input))

    results.size shouldBe 1
    new String(results.head.toArray, "UTF-8") shouldBe """{"text":"HELLO"}"""
  }

  it should "compose with >>> operator" in {
    val step1 = Conversion.pure[Mime.Plain, Mime.Plain] { input =>
      Content.fromString("step1", Mime.plain)
    }

    val step2 = Conversion.pure[Mime.Plain, Mime.Html] { input =>
      Content.fromString("<html>step2</html>", Mime.html)
    }

    val composed = step1 >>> step2
    val input = Content.fromString("start", Mime.plain)
    val results = runZIO(composed.apply(input))

    results.size shouldBe 1
    results.head.mime shouldBe Mime.html
  }

  // ============================================================================
  // Error Handling Tests
  // ============================================================================

  "Conversion" should "propagate errors correctly" in {
    val failingConversion = Conversion[Mime.Plain, Mime.Html] { _ =>
      ZIO.fail(RenderError("rendering failed"))
    }

    val input = Content.fromString("test", Mime.plain)
    val error = runZIOExpectingError(failingConversion.convert(input))

    error shouldBe a[RenderError]
    error.message shouldBe "rendering failed"
  }

  it should "preserve metadata through conversion" in {
    val conversion = Conversion.pure[Mime.Plain, Mime.Html] { input =>
      // Conversion that preserves filename metadata
      val text = new String(input.toArray, "UTF-8")
      Content.fromString(s"<html>$text</html>", Mime.html)
        .withMetadata("original-filename", input.filename.getOrElse("unknown"))
    }

    val input = Content.fromString("test", Mime.plain)
      .withMetadata(Content.MetadataKeys.Filename, "test.txt")

    val result = runZIO(conversion.convert(input))

    result.get("original-filename") shouldBe Some("test.txt")
  }
