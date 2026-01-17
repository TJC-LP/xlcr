package com.tjclp.xlcr.v2.registry

import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}

import zio.{Chunk, ZIO}

import com.tjclp.xlcr.v2.base.V2TestSupport
import com.tjclp.xlcr.v2.transform.{Conversion, DynamicSplitter, Splitter, Transform}
import com.tjclp.xlcr.v2.types.{Content, DynamicFragment, Fragment, Mime}

/**
 * Tests for the TransformRegistry singleton.
 */
class TransformRegistrySpec extends V2TestSupport:

  // ============================================================================
  // Registration Tests
  // ============================================================================

  "TransformRegistry.registerConversion" should "register a conversion" in {
    val conversion = Conversion.pure[Mime.Pdf, Mime.Html] { input =>
      Content.fromString("<html>converted</html>", Mime.html)
    }

    TransformRegistry.registerConversion(Mime.pdf, Mime.html, conversion)

    TransformRegistry.hasDirectConversion(Mime.pdf, Mime.html) shouldBe true
    TransformRegistry.conversionCount should be >= 1
  }

  it should "allow multiple conversions for same MIME pair" in {
    val lowPriority = Conversion.withPriority[Mime.Pdf, Mime.Html](10) { _ =>
      ZIO.succeed(Content.fromString("low", Mime.html))
    }

    val highPriority = Conversion.withPriority[Mime.Pdf, Mime.Html](100) { _ =>
      ZIO.succeed(Content.fromString("high", Mime.html))
    }

    TransformRegistry.registerConversion(Mime.pdf, Mime.html, lowPriority)
    TransformRegistry.registerConversion(Mime.pdf, Mime.html, highPriority)

    val allConversions = TransformRegistry.findAllConversions(Mime.pdf, Mime.html)
    allConversions should have size 2
  }

  it should "order conversions by priority (highest first)" in {
    val low = Conversion.withPriority[Mime.Docx, Mime.Pdf](10) { _ =>
      ZIO.succeed(Content.empty(Mime.pdf))
    }
    val high = Conversion.withPriority[Mime.Docx, Mime.Pdf](100) { _ =>
      ZIO.succeed(Content.empty(Mime.pdf))
    }
    val medium = Conversion.withPriority[Mime.Docx, Mime.Pdf](50) { _ =>
      ZIO.succeed(Content.empty(Mime.pdf))
    }

    // Register in non-sorted order
    TransformRegistry.registerConversion(Mime.docx, Mime.pdf, low)
    TransformRegistry.registerConversion(Mime.docx, Mime.pdf, high)
    TransformRegistry.registerConversion(Mime.docx, Mime.pdf, medium)

    val all = TransformRegistry.findAllConversions(Mime.docx, Mime.pdf)
    all.head.priority shouldBe 100
    all(1).priority shouldBe 50
    all(2).priority shouldBe 10
  }

  // ============================================================================
  // Splitter Registration Tests
  // ============================================================================

  "TransformRegistry.registerSplitter" should "register a splitter" in {
    val splitter = Splitter[Mime.Xlsx, Mime.Xlsx] { _ =>
      ZIO.succeed(Chunk.empty)
    }

    TransformRegistry.registerSplitter(Mime.xlsx, Mime.xlsx, splitter)

    TransformRegistry.findSplitter(Mime.xlsx) shouldBe defined
    TransformRegistry.splitterCount should be >= 1
  }

  it should "also register as a conversion for path finding" in {
    val splitter = Splitter[Mime.Pptx, Mime.Pptx] { _ =>
      ZIO.succeed(Chunk.empty)
    }

    TransformRegistry.registerSplitter(Mime.pptx, Mime.pptx, splitter)

    // Should be findable both as splitter and conversion
    TransformRegistry.findSplitter(Mime.pptx) shouldBe defined
    TransformRegistry.hasDirectConversion(Mime.pptx, Mime.pptx) shouldBe true
  }

  // ============================================================================
  // Dynamic Splitter Registration Tests
  // ============================================================================

  "TransformRegistry.registerDynamicSplitter" should "register a dynamic splitter" in {
    val splitter = DynamicSplitter[Mime.Zip] { _ =>
      ZIO.succeed(Chunk.empty)
    }

    TransformRegistry.registerDynamicSplitter(Mime.zip, splitter)

    TransformRegistry.findDynamicSplitter(Mime.zip) shouldBe defined
    TransformRegistry.dynamicSplitterCount should be >= 1
  }

  // ============================================================================
  // Lookup Tests
  // ============================================================================

  "TransformRegistry.findConversion" should "return highest priority conversion" in {
    val low = Conversion.withPriority[Mime.Html, Mime.Pdf](10) { _ =>
      ZIO.succeed(Content.empty(Mime.pdf))
    }
    val high = Conversion.withPriority[Mime.Html, Mime.Pdf](100) { _ =>
      ZIO.succeed(Content.empty(Mime.pdf))
    }

    TransformRegistry.registerConversion(Mime.html, Mime.pdf, low)
    TransformRegistry.registerConversion(Mime.html, Mime.pdf, high)

    val found = TransformRegistry.findConversion(Mime.html, Mime.pdf)
    found shouldBe defined
    found.get.priority shouldBe 100
  }

  it should "return None when no conversion registered" in {
    TransformRegistry.findConversion(Mime.mp3, Mime.jpeg) shouldBe None
  }

  "TransformRegistry.findConversionTyped" should "cast to specific types" in {
    val conversion = Conversion.pure[Mime.Xml, Mime.Json] { _ =>
      Content.fromString("{}", Mime.json)
    }

    TransformRegistry.registerConversion(Mime.xml, Mime.json, conversion)

    val found = TransformRegistry.findConversionTyped[Mime.Xml, Mime.Json](Mime.xml, Mime.json)
    found shouldBe defined
  }

  "TransformRegistry.findSplitter" should "return highest priority splitter" in {
    val low = Splitter.withPriority[Mime.Pdf, Mime.Pdf](10) { _ =>
      ZIO.succeed(Chunk.empty)
    }
    val high = Splitter.withPriority[Mime.Pdf, Mime.Pdf](100) { _ =>
      ZIO.succeed(Chunk.empty)
    }

    TransformRegistry.registerSplitter(Mime.pdf, Mime.pdf, low)
    TransformRegistry.registerSplitter(Mime.pdf, Mime.pdf, high)

    val found = TransformRegistry.findSplitter(Mime.pdf)
    found shouldBe defined
    found.get.priority shouldBe 100
  }

  it should "return None when no splitter registered" in {
    TransformRegistry.findSplitter(Mime.mp4) shouldBe None
  }

  "TransformRegistry.findDynamicSplitter" should "return dynamic splitter" in {
    val splitter = DynamicSplitter.withPriority[Mime.Eml](50) { _ =>
      ZIO.succeed(Chunk.empty)
    }

    TransformRegistry.registerDynamicSplitter(Mime.eml, splitter)

    val found = TransformRegistry.findDynamicSplitter(Mime.eml)
    found shouldBe defined
    found.get.priority shouldBe 50
  }

  // ============================================================================
  // Graph Query Tests
  // ============================================================================

  "TransformRegistry.conversionEdges" should "return all registered edges" in {
    TransformRegistry.registerConversion(
      Mime.pdf,
      Mime.html,
      Conversion.pure[Mime.Pdf, Mime.Html](_ => Content.empty(Mime.html))
    )
    TransformRegistry.registerConversion(
      Mime.html,
      Mime.plain,
      Conversion.pure[Mime.Html, Mime.Plain](_ => Content.empty(Mime.plain))
    )

    val edges = TransformRegistry.conversionEdges

    edges should contain((Mime.pdf, Mime.html))
    edges should contain((Mime.html, Mime.plain))
  }

  "TransformRegistry.registeredInputMimes" should "return all input types" in {
    TransformRegistry.registerConversion(
      Mime.docx,
      Mime.pdf,
      Conversion.pure[Mime.Docx, Mime.Pdf](_ => Content.empty(Mime.pdf))
    )

    val inputs = TransformRegistry.registeredInputMimes
    inputs should contain(Mime.docx)
  }

  "TransformRegistry.registeredOutputMimes" should "return all output types" in {
    TransformRegistry.registerConversion(
      Mime.docx,
      Mime.pdf,
      Conversion.pure[Mime.Docx, Mime.Pdf](_ => Content.empty(Mime.pdf))
    )

    val outputs = TransformRegistry.registeredOutputMimes
    outputs should contain(Mime.pdf)
  }

  "TransformRegistry.hasDirectConversion" should "return true for registered conversions" in {
    TransformRegistry.registerConversion(
      Mime.xlsx,
      Mime.csv,
      Conversion.pure[Mime.Xlsx, Mime.Csv](_ => Content.empty(Mime.csv))
    )

    TransformRegistry.hasDirectConversion(Mime.xlsx, Mime.csv) shouldBe true
    TransformRegistry.hasDirectConversion(Mime.csv, Mime.xlsx) shouldBe false
  }

  // ============================================================================
  // Statistics Tests
  // ============================================================================

  "TransformRegistry.conversionCount" should "return total number of registered conversions" in {
    val initialCount = TransformRegistry.conversionCount

    TransformRegistry.registerConversion(
      Mime.odt,
      Mime.pdf,
      Conversion.pure[Mime.Odt, Mime.Pdf](_ => Content.empty(Mime.pdf))
    )

    TransformRegistry.conversionCount shouldBe (initialCount + 1)
  }

  "TransformRegistry.diagnostics" should "return status information" in {
    TransformRegistry.registerConversion(
      Mime.rtf,
      Mime.pdf,
      Conversion.pure[Mime.Rtf, Mime.Pdf](_ => Content.empty(Mime.pdf))
    )

    val diagnostics = TransformRegistry.diagnostics

    diagnostics should include("TransformRegistry Status")
    diagnostics should include("Conversions:")
  }

  // ============================================================================
  // Clear Tests
  // ============================================================================

  "TransformRegistry.clear" should "remove all registered transforms" in {
    TransformRegistry.registerConversion(
      Mime.bmp,
      Mime.png,
      Conversion.pure[Mime.Bmp, Mime.Png](_ => Content.empty(Mime.png))
    )
    TransformRegistry.registerSplitter(
      Mime.ods,
      Mime.ods,
      Splitter[Mime.Ods, Mime.Ods] { _ => ZIO.succeed(Chunk.empty) }
    )
    TransformRegistry.registerDynamicSplitter(
      Mime.tar,
      DynamicSplitter[Mime.Tar] { _ => ZIO.succeed(Chunk.empty) }
    )

    TransformRegistry.conversionCount should be > 0

    TransformRegistry.clear()

    TransformRegistry.conversionCount shouldBe 0
    TransformRegistry.splitterCount shouldBe 0
    TransformRegistry.dynamicSplitterCount shouldBe 0
  }

  // ============================================================================
  // Thread Safety Tests
  // ============================================================================

  "TransformRegistry" should "be thread-safe under concurrent access" in {
    val executor = Executors.newFixedThreadPool(10)
    val latch = new CountDownLatch(100)

    try
      // Concurrently register and query
      (0 until 100).foreach { i =>
        executor.submit(new Runnable {
          def run(): Unit =
            try
              val inputMime = Mime(s"application/type-$i")
              val outputMime = Mime(s"application/output-$i")
              val conversion = Conversion.withPriority[Mime, Mime](i) { _ =>
                ZIO.succeed(Content.empty(outputMime))
              }
              TransformRegistry.registerConversion(inputMime, outputMime, conversion)

              // Also do some reads
              TransformRegistry.conversionEdges
              TransformRegistry.conversionCount
              TransformRegistry.findConversion(inputMime, outputMime)
            finally
              latch.countDown()
        })
      }

      latch.await(10, TimeUnit.SECONDS) shouldBe true

      // Verify some registrations succeeded
      TransformRegistry.conversionCount should be >= 50
    finally
      executor.shutdown()
  }

  it should "maintain consistency under concurrent registration" in {
    val executor = Executors.newFixedThreadPool(5)
    val startLatch = new CountDownLatch(1)
    val doneLatch = new CountDownLatch(5)

    // All threads register to the same MIME pair with different priorities
    val priorities = (1 to 5).toList

    try
      priorities.foreach { prio =>
        executor.submit(new Runnable {
          def run(): Unit =
            try
              startLatch.await()
              val conversion = Conversion.withPriority[Mime.Gif, Mime.Png](prio * 10) { _ =>
                ZIO.succeed(Content.empty(Mime.png))
              }
              TransformRegistry.registerConversion(Mime.gif, Mime.png, conversion)
            finally
              doneLatch.countDown()
        })
      }

      // Start all threads at once
      startLatch.countDown()
      doneLatch.await(5, TimeUnit.SECONDS) shouldBe true

      // All should be registered
      val all = TransformRegistry.findAllConversions(Mime.gif, Mime.png)
      all should have size 5

      // Should be sorted by priority (highest first)
      all.map(_.priority) shouldBe List(50, 40, 30, 20, 10)
    finally
      executor.shutdown()
  }
