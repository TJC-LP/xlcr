package com.tjclp.xlcr.v2.registry

import zio.ZIO

import com.tjclp.xlcr.v2.base.V2TestSupport
import com.tjclp.xlcr.v2.transform.{Conversion, UnsupportedConversion}
import com.tjclp.xlcr.v2.types.{Content, Mime}

/**
 * Tests for the PathFinder BFS path discovery.
 */
class PathFinderSpec extends V2TestSupport:

  // Helper to widen Content type for path operations
  private def widen[M <: Mime](c: Content[M]): Content[Mime] =
    Content.fromChunk(c.data, c.mime, c.metadata)

  // Helper to register a simple conversion
  private def registerConversion(from: Mime, to: Mime, priority: Int = 0): Unit =
    val conversion = Conversion.withPriority[Mime, Mime](priority) { input =>
      ZIO.succeed(Content.fromChunk(input.data, to))
    }
    TransformRegistry.registerConversion(from, to, conversion)

  // ============================================================================
  // Identity Path Tests
  // ============================================================================

  "PathFinder.findPath" should "return identity transform for same type" in {
    val path = PathFinder.findPath(Mime.pdf, Mime.pdf)

    path shouldBe defined
    val input = widen(Content.fromString("test", Mime.pdf))
    val result = runZIO(path.get.apply(input))

    result should have size 1
    result.head shouldBe input
  }

  it should "return identity for any same type without registration" in {
    // No conversions registered
    val path1 = PathFinder.findPath(Mime.xlsx, Mime.xlsx)
    val path2 = PathFinder.findPath(Mime.html, Mime.html)
    val path3 = PathFinder.findPath(Mime.jpeg, Mime.jpeg)

    path1 shouldBe defined
    path2 shouldBe defined
    path3 shouldBe defined
  }

  // ============================================================================
  // Direct Conversion Tests
  // ============================================================================

  "PathFinder" should "find direct conversion" in {
    registerConversion(Mime.pdf, Mime.html)

    val path = PathFinder.findPath(Mime.pdf, Mime.html)

    path shouldBe defined
  }

  it should "use direct conversion when available" in {
    registerConversion(Mime.docx, Mime.pdf)

    val path = PathFinder.findPath(Mime.docx, Mime.pdf)
    path shouldBe defined

    val input = widen(Content.fromString("test", Mime.docx))
    val result = runZIO(path.get.apply(input))

    result should have size 1
    result.head.mime shouldBe Mime.pdf
  }

  // ============================================================================
  // Multi-hop Path Tests
  // ============================================================================

  "PathFinder" should "find multi-hop path via BFS" in {
    // PDF -> HTML -> Plain (2 hops)
    registerConversion(Mime.pdf, Mime.html)
    registerConversion(Mime.html, Mime.plain)

    val path = PathFinder.findPath(Mime.pdf, Mime.plain)

    path shouldBe defined
  }

  it should "execute multi-hop path correctly" in {
    registerConversion(Mime.xlsx, Mime.csv)
    registerConversion(Mime.csv, Mime.plain)

    val path = PathFinder.findPath(Mime.xlsx, Mime.plain)
    path shouldBe defined

    val input = widen(Content.fromString("spreadsheet", Mime.xlsx))
    val result = runZIO(path.get.apply(input))

    result should have size 1
    result.head.mime shouldBe Mime.plain
  }

  it should "find shortest path with BFS" in {
    // Create two paths:
    // Short: A -> B (1 hop)
    // Long: A -> C -> D -> B (3 hops)
    val a = Mime("type/a")
    val b = Mime("type/b")
    val c = Mime("type/c")
    val d = Mime("type/d")

    // Direct path (short)
    registerConversion(a, b)

    // Longer path
    registerConversion(a, c)
    registerConversion(c, d)
    registerConversion(d, b)

    // Should find the direct path (1 hop)
    val path = PathFinder.findPath(a, b)
    path shouldBe defined

    // Describe path should show direct
    val description = PathFinder.describePath(a, b)
    description should include("type/a")
    description should include("type/b")
    description should not include "type/c"
  }

  // ============================================================================
  // No Path Tests
  // ============================================================================

  "PathFinder" should "return None when no path exists" in {
    // No conversions registered between these types
    val path = PathFinder.findPath(Mime.mp3, Mime.jpeg)

    path shouldBe None
  }

  it should "return None for disconnected graph" in {
    // Create two disconnected subgraphs
    registerConversion(Mime.pdf, Mime.html)
    registerConversion(Mime.xlsx, Mime.csv)

    // No path between subgraphs
    val path = PathFinder.findPath(Mime.pdf, Mime.csv)

    path shouldBe None
  }

  // ============================================================================
  // getPath Tests
  // ============================================================================

  "PathFinder.getPath" should "return Right for existing path" in {
    registerConversion(Mime.docx, Mime.html)

    val result = PathFinder.getPath(Mime.docx, Mime.html)

    result.isRight shouldBe true
  }

  it should "return Left(UnsupportedConversion) for no path" in {
    val result = PathFinder.getPath(Mime.wav, Mime.png)

    result.isLeft shouldBe true
    result.left.getOrElse(fail("Expected Left")) shouldBe a[UnsupportedConversion]
    result.left.getOrElse(fail("Expected Left")).from shouldBe Mime.wav
    result.left.getOrElse(fail("Expected Left")).to shouldBe Mime.png
  }

  // ============================================================================
  // Typed Path Tests
  // ============================================================================

  "PathFinder.findPathTyped" should "cast to specific types" in {
    registerConversion(Mime.xml, Mime.json)

    val path = PathFinder.findPathTyped[Mime.Xml, Mime.Json](Mime.xml, Mime.json)

    path shouldBe defined
  }

  "PathFinder.getPathTyped" should "return typed Right for existing path" in {
    registerConversion(Mime.csv, Mime.xlsx)

    val result = PathFinder.getPathTyped[Mime.Csv, Mime.Xlsx](Mime.csv, Mime.xlsx)

    result.isRight shouldBe true
  }

  // ============================================================================
  // pathExists Tests
  // ============================================================================

  "PathFinder.pathExists" should "return true for identity" in {
    PathFinder.pathExists(Mime.pdf, Mime.pdf) shouldBe true
  }

  it should "return true for direct conversion" in {
    registerConversion(Mime.html, Mime.pdf)

    PathFinder.pathExists(Mime.html, Mime.pdf) shouldBe true
  }

  it should "return true for multi-hop path" in {
    registerConversion(Mime.markdown, Mime.html)
    registerConversion(Mime.html, Mime.pdf)

    PathFinder.pathExists(Mime.markdown, Mime.pdf) shouldBe true
  }

  it should "return false when no path" in {
    PathFinder.pathExists(Mime.flac, Mime.xlsx) shouldBe false
  }

  // ============================================================================
  // reachableFrom Tests
  // ============================================================================

  "PathFinder.reachableFrom" should "include starting type with 0 hops" in {
    val reachable = PathFinder.reachableFrom(Mime.pdf)

    reachable should contain(Mime.pdf -> 0)
  }

  it should "find directly reachable types with 1 hop" in {
    registerConversion(Mime.docx, Mime.pdf)
    registerConversion(Mime.docx, Mime.html)

    val reachable = PathFinder.reachableFrom(Mime.docx)

    reachable(Mime.docx) shouldBe 0
    reachable(Mime.pdf) shouldBe 1
    reachable(Mime.html) shouldBe 1
  }

  it should "find multi-hop reachable types" in {
    registerConversion(Mime.pptx, Mime.html)
    registerConversion(Mime.html, Mime.plain)
    registerConversion(Mime.plain, Mime.csv)

    val reachable = PathFinder.reachableFrom(Mime.pptx)

    reachable(Mime.pptx) shouldBe 0
    reachable(Mime.html) shouldBe 1
    reachable(Mime.plain) shouldBe 2
    reachable(Mime.csv) shouldBe 3
  }

  it should "respect maxHops limit" in {
    registerConversion(Mime.odt, Mime.pdf)
    registerConversion(Mime.pdf, Mime.html)
    registerConversion(Mime.html, Mime.plain)

    val reachable = PathFinder.reachableFrom(Mime.odt, maxHops = 2)

    reachable should contain(Mime.odt -> 0)
    reachable should contain(Mime.pdf -> 1)
    reachable should contain(Mime.html -> 2)
    reachable should not contain key(Mime.plain)
  }

  // ============================================================================
  // describePath Tests
  // ============================================================================

  "PathFinder.describePath" should "describe identity path" in {
    val description = PathFinder.describePath(Mime.pdf, Mime.pdf)

    description should include("identity")
  }

  it should "describe direct conversion" in {
    registerConversion(Mime.docx, Mime.pdf)

    val description = PathFinder.describePath(Mime.docx, Mime.pdf)

    description should include("wordprocessingml")
    description should include("pdf")
    description should include("->")
  }

  it should "describe multi-hop path" in {
    registerConversion(Mime.xlsx, Mime.csv)
    registerConversion(Mime.csv, Mime.plain)

    val description = PathFinder.describePath(Mime.xlsx, Mime.plain)

    description should include("spreadsheetml.sheet")
    description should include("csv")
    description should include("plain")
    description should include(">>>")
  }

  it should "indicate when no path found" in {
    val description = PathFinder.describePath(Mime.mp4, Mime.doc)

    description should include("No path found")
  }

  // ============================================================================
  // Cache Tests
  // ============================================================================

  "PathFinder.clearCache" should "clear cached paths" in {
    registerConversion(Mime.rtf, Mime.pdf)

    // Warm up cache
    PathFinder.findPath(Mime.rtf, Mime.pdf) shouldBe defined

    // Clear registry and cache
    TransformRegistry.clear()
    PathFinder.clearCache()

    // Should now return None (no conversions)
    PathFinder.findPath(Mime.rtf, Mime.pdf) shouldBe None
  }

  it should "allow re-computation after clear" in {
    // First path
    registerConversion(Mime.odt, Mime.pdf)
    PathFinder.findPath(Mime.odt, Mime.pdf) shouldBe defined

    // Clear and register different path
    TransformRegistry.clear()
    PathFinder.clearCache()

    registerConversion(Mime.odt, Mime.html)
    registerConversion(Mime.html, Mime.pdf)

    // Should find new multi-hop path
    val path = PathFinder.findPath(Mime.odt, Mime.pdf)
    path shouldBe defined

    val description = PathFinder.describePath(Mime.odt, Mime.pdf)
    description should include("html")
  }

  // ============================================================================
  // Edge Cases
  // ============================================================================

  "PathFinder" should "handle cycles in the graph" in {
    // Create a cycle: A -> B -> C -> A
    val a = Mime("cycle/a")
    val b = Mime("cycle/b")
    val c = Mime("cycle/c")
    val d = Mime("cycle/d")

    registerConversion(a, b)
    registerConversion(b, c)
    registerConversion(c, a) // Cycle back
    registerConversion(c, d)

    // Should still find path to d without infinite loop
    val path = PathFinder.findPath(a, d)
    path shouldBe defined
  }

  it should "handle large graphs efficiently" in {
    // Create a chain: t0 -> t1 -> t2 -> ... -> t19
    val types = (0 until 20).map(i => Mime(s"large/type-$i"))
    types.sliding(2).foreach { pair =>
      registerConversion(pair(0), pair(1))
    }

    val path = PathFinder.findPath(types.head, types.last)
    path shouldBe defined

    // Verify reachability with explicit maxHops
    val reachable = PathFinder.reachableFrom(types.head, maxHops = 20)
    reachable.size shouldBe 20
    reachable(types.last) shouldBe 19
  }

  it should "find shortest even with multiple paths of different lengths" in {
    val start = Mime("multi/start")
    val end = Mime("multi/end")
    val mid1 = Mime("multi/mid1")
    val mid2 = Mime("multi/mid2")
    val mid3 = Mime("multi/mid3")

    // Short path: start -> end (1 hop)
    registerConversion(start, end)

    // Longer path: start -> mid1 -> mid2 -> mid3 -> end (4 hops)
    registerConversion(start, mid1)
    registerConversion(mid1, mid2)
    registerConversion(mid2, mid3)
    registerConversion(mid3, end)

    // BFS should find the 1-hop path
    val description = PathFinder.describePath(start, end)
    description should include("multi/start")
    description should include("multi/end")
    description should not include "mid"
  }
