package com.tjclp.xlcr.v2.transform

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import com.tjclp.xlcr.v2.types.Mime

/**
 * Tests for the TransformError hierarchy.
 */
class ErrorsSpec extends AnyFlatSpec with Matchers:

  // ============================================================================
  // ParseError Tests
  // ============================================================================

  "ParseError" should "be created with message only" in {
    val error = ParseError("Failed to parse document")

    error.message shouldBe "Failed to parse document"
    error.cause shouldBe None
    error.getMessage shouldBe "Failed to parse document"
    error.getCause shouldBe null
  }

  it should "be created with message and cause" in {
    val cause = new RuntimeException("underlying error")
    val error = ParseError("Failed to parse", cause)

    error.message shouldBe "Failed to parse"
    error.cause shouldBe Some(cause)
    error.getCause shouldBe cause
  }

  it should "be created from throwable" in {
    val cause = new IllegalArgumentException("invalid input")
    val error = ParseError.fromThrowable(cause)

    error.message shouldBe "invalid input"
    error.cause shouldBe Some(cause)
  }

  // ============================================================================
  // RenderError Tests
  // ============================================================================

  "RenderError" should "be created with message only" in {
    val error = RenderError("Failed to render output")

    error.message shouldBe "Failed to render output"
    error.cause shouldBe None
  }

  it should "be created with message and cause" in {
    val cause = new java.io.IOException("write error")
    val error = RenderError("Failed to render", cause)

    error.message shouldBe "Failed to render"
    error.cause shouldBe Some(cause)
  }

  it should "be created from throwable" in {
    val cause = new java.io.IOException("io failure")
    val error = RenderError.fromThrowable(cause)

    error.message shouldBe "io failure"
    error.cause shouldBe Some(cause)
  }

  // ============================================================================
  // ValidationError Tests
  // ============================================================================

  "ValidationError" should "be created with message only" in {
    val error = ValidationError("Invalid data format")

    error.message shouldBe "Invalid data format"
    error.cause shouldBe None
  }

  it should "be created with message and cause" in {
    val cause = new IllegalArgumentException("bad data")
    val error = ValidationError("Invalid data", cause)

    error.message shouldBe "Invalid data"
    error.cause shouldBe Some(cause)
  }

  // ============================================================================
  // UnsupportedConversion Tests
  // ============================================================================

  "UnsupportedConversion" should "format message with MIME types" in {
    val error = UnsupportedConversion(Mime.pdf, Mime.mp3)

    error.message shouldBe "No conversion path from application/pdf to audio/mpeg"
    error.from shouldBe Mime.pdf
    error.to shouldBe Mime.mp3
    error.cause shouldBe None
  }

  it should "work with various MIME type combinations" in {
    val error1 = UnsupportedConversion(Mime.xlsx, Mime.jpeg)
    error1.message should include("spreadsheetml.sheet")
    error1.message should include("image/jpeg")

    val error2 = UnsupportedConversion(Mime.docx, Mime.mp4)
    error2.message should include("wordprocessingml.document")
    error2.message should include("video/mp4")
  }

  // ============================================================================
  // TimeoutError Tests
  // ============================================================================

  "TimeoutError" should "include duration in message" in {
    val error = TimeoutError("Operation timed out", 5000)

    error.message shouldBe "Operation timed out"
    error.durationMs shouldBe 5000
    error.cause shouldBe None
  }

  // ============================================================================
  // CancellationError Tests
  // ============================================================================

  "CancellationError" should "be created with message" in {
    val error = CancellationError("User cancelled operation")

    error.message shouldBe "User cancelled operation"
    error.cause shouldBe None
  }

  // ============================================================================
  // ResourceError Tests
  // ============================================================================

  "ResourceError" should "be created with message and resource type" in {
    val error = ResourceError("Resource not found", "file")

    error.message shouldBe "Resource not found"
    error.resourceType shouldBe "file"
    error.cause shouldBe None
  }

  it should "create missing file error" in {
    val error = ResourceError.missingFile("/path/to/file.pdf")

    error.message shouldBe "File not found: /path/to/file.pdf"
    error.resourceType shouldBe "file"
  }

  it should "create missing library error" in {
    val error = ResourceError.missingLibrary("LibreOffice")

    error.message shouldBe "Required library not available: LibreOffice"
    error.resourceType shouldBe "library"
  }

  it should "create missing license error" in {
    val error = ResourceError.missingLicense("Aspose.Slides")

    error.message shouldBe "License not found for: Aspose.Slides"
    error.resourceType shouldBe "license"
  }

  // ============================================================================
  // SplitError Tests
  // ============================================================================

  "SplitError" should "include input MIME type" in {
    val error = SplitError("Could not split document", Mime.pdf)

    error.message shouldBe "Could not split document"
    error.inputMime shouldBe Mime.pdf
    error.cause shouldBe None
  }

  it should "be created with cause" in {
    val cause = new RuntimeException("internal error")
    val error = SplitError("Split failed", Mime.xlsx, cause)

    error.message shouldBe "Split failed"
    error.inputMime shouldBe Mime.xlsx
    error.cause shouldBe Some(cause)
  }

  // ============================================================================
  // CompositeError Tests
  // ============================================================================

  "CompositeError" should "aggregate multiple errors" in {
    val errors = List(
      ParseError("Parse error 1"),
      RenderError("Render error 1"),
      ValidationError("Validation error 1")
    )
    val composite = CompositeError(errors)

    composite.errors should have size 3
    composite.message should include("3 errors occurred")
    composite.message should include("Parse error 1")
    composite.message should include("Render error 1")
    composite.message should include("Validation error 1")
  }

  it should "use first error as cause" in {
    val first = ParseError("first error")
    val second = RenderError("second error")
    val composite = CompositeError(List(first, second))

    composite.cause shouldBe Some(first)
  }

  it should "be created with of helper" in {
    val first = ParseError("first")
    val second = RenderError("second")
    val third = ValidationError("third")

    val composite = CompositeError.of(first, second, third)

    composite.errors should have size 3
  }

  it should "handle single error" in {
    val single = ParseError("only error")
    val composite = CompositeError(List(single))

    composite.errors should have size 1
    composite.message should include("1 errors occurred")
  }

  it should "handle empty list" in {
    val composite = CompositeError(List.empty)

    composite.errors shouldBe empty
    composite.message should include("0 errors occurred")
    composite.cause shouldBe None
  }

  // ============================================================================
  // GeneralError Tests
  // ============================================================================

  "GeneralError" should "be created with message only" in {
    val error = GeneralError("Something went wrong")

    error.message shouldBe "Something went wrong"
    error.cause shouldBe None
  }

  it should "be created with message and cause" in {
    val cause = new Exception("root cause")
    val error = GeneralError("Something went wrong", cause)

    error.message shouldBe "Something went wrong"
    error.cause shouldBe Some(cause)
  }

  it should "be created from throwable" in {
    val cause = new Exception("exception message")
    val error = GeneralError.fromThrowable(cause)

    error.message shouldBe "exception message"
    error.cause shouldBe Some(cause)
  }

  // ============================================================================
  // TransformError Utility Tests
  // ============================================================================

  "TransformError.fromThrowable" should "return TransformError unchanged" in {
    val parseError = ParseError("already a transform error")
    val result = TransformError.fromThrowable(parseError)

    result shouldBe parseError
  }

  it should "wrap other throwables in GeneralError" in {
    val cause = new RuntimeException("runtime exception")
    val result = TransformError.fromThrowable(cause)

    result shouldBe a[GeneralError]
    result.message shouldBe "runtime exception"
  }

  "TransformError.parse" should "create ParseError" in {
    val error = TransformError.parse("parse message")

    error shouldBe a[ParseError]
    error.message shouldBe "parse message"
  }

  "TransformError.render" should "create RenderError" in {
    val error = TransformError.render("render message")

    error shouldBe a[RenderError]
    error.message shouldBe "render message"
  }

  "TransformError.validation" should "create ValidationError" in {
    val error = TransformError.validation("validation message")

    error shouldBe a[ValidationError]
    error.message shouldBe "validation message"
  }

  "TransformError.unsupported" should "create UnsupportedConversion" in {
    val error = TransformError.unsupported(Mime.pdf, Mime.xlsx)

    error shouldBe a[UnsupportedConversion]
    error.asInstanceOf[UnsupportedConversion].from shouldBe Mime.pdf
    error.asInstanceOf[UnsupportedConversion].to shouldBe Mime.xlsx
  }

  // ============================================================================
  // Exception Compatibility Tests
  // ============================================================================

  "TransformError" should "be throwable as Exception" in {
    val error: Exception = ParseError("test error")

    error.getMessage shouldBe "test error"
  }

  it should "work with try-catch" in {
    def throwingMethod(): Unit = throw RenderError("render failed")

    try
      throwingMethod()
      fail("Should have thrown")
    catch
      case e: TransformError =>
        e.message shouldBe "render failed"
        e shouldBe a[RenderError]
  }
