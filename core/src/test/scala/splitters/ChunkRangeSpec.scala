package com.tjclp.xlcr
package splitters

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChunkRangeSpec extends AnyFlatSpec with Matchers {

  "SplitConfig" should "support chunk ranges" in {
    val config = SplitConfig(chunkRange = Some(0 until 10))
    config.chunkRange shouldBe Some(0 until 10)
    config.chunkRange.get.size shouldBe 10
    config.chunkRange.get.toList shouldBe List(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
  }

  it should "maintain backward compatibility with pageRange" in {
    val config = SplitConfig(chunkRange = Some(5 until 15))
    // pageRange should return the same as chunkRange
    config.pageRange shouldBe config.chunkRange
    config.pageRange shouldBe Some(5 until 15)
  }

  it should "handle empty ranges" in {
    val emptyRange = SplitConfig(chunkRange = Some(5 until 5))
    emptyRange.chunkRange.get.isEmpty shouldBe true
    emptyRange.chunkRange.get.size shouldBe 0
  }

  it should "handle single-element ranges" in {
    val singleRange = SplitConfig(chunkRange = Some(7 until 8))
    singleRange.chunkRange.get.size shouldBe 1
    singleRange.chunkRange.get.toList shouldBe List(7)
  }

  it should "handle large ranges efficiently" in {
    val largeRange = SplitConfig(chunkRange = Some(0 until 10000))
    largeRange.chunkRange.get.size shouldBe 10000
    largeRange.chunkRange.get.head shouldBe 0
    largeRange.chunkRange.get.last shouldBe 9999
  }

  it should "handle ranges starting from non-zero" in {
    val offsetRange = SplitConfig(chunkRange = Some(100 until 200))
    offsetRange.chunkRange.get.size shouldBe 100
    offsetRange.chunkRange.get.head shouldBe 100
    offsetRange.chunkRange.get.last shouldBe 199
  }

  it should "work with None chunk range" in {
    val noRange = SplitConfig(chunkRange = None)
    noRange.chunkRange shouldBe None
    noRange.pageRange shouldBe None
  }

  "Chunk range filtering" should "work correctly with valid indices" in {
    // Simulate a splitter's chunk filtering logic
    val totalChunks = 10
    val chunks      = (0 until totalChunks).toList

    // Test various ranges
    val testCases = List(
      (Some(0 until 5), List(0, 1, 2, 3, 4)),
      (Some(5 until 10), List(5, 6, 7, 8, 9)),
      (Some(2 until 7), List(2, 3, 4, 5, 6)),
      (None, chunks) // No range means all chunks
    )

    testCases.foreach { case (range, expected) =>
      val filtered = range match {
        case Some(r) => r.filter(i => i >= 0 && i < totalChunks).toList
        case None    => chunks
      }
      filtered shouldBe expected
    }
  }

  it should "handle out-of-bounds indices gracefully" in {
    val totalChunks = 5
    val chunks      = (0 until totalChunks).toList

    // Range extending beyond available chunks
    val range1    = Some(3 until 10)
    val filtered1 = range1.get.filter(i => i >= 0 && i < totalChunks).toList
    filtered1 shouldBe List(3, 4)

    // Range completely out of bounds
    val range2    = Some(10 until 15)
    val filtered2 = range2.get.filter(i => i >= 0 && i < totalChunks).toList
    filtered2 shouldBe List.empty

    // Range with negative start (should filter out negatives)
    val range3    = Some(-5 until 3)
    val filtered3 = range3.get.filter(i => i >= 0 && i < totalChunks).toList
    filtered3 shouldBe List(0, 1, 2)
  }

  "SplitConfig with strategy and chunk range" should "work together" in {
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page),
      chunkRange = Some(0 until 20)
    )

    config.strategy shouldBe Some(SplitStrategy.Page)
    config.chunkRange shouldBe Some(0 until 20)
    config.hasStrategy(SplitStrategy.Page) shouldBe true
  }

  it should "support all split strategies with chunk ranges" in {
    val strategies = List(
      SplitStrategy.Page,
      SplitStrategy.Sheet,
      SplitStrategy.Slide,
      SplitStrategy.Row,
      SplitStrategy.Chunk,
      SplitStrategy.Attachment,
      SplitStrategy.Embedded,
      SplitStrategy.Heading,
      SplitStrategy.Auto
    )

    strategies.foreach { strategy =>
      val config = SplitConfig(
        strategy = Some(strategy),
        chunkRange = Some(0 until 5)
      )

      config.hasStrategy(strategy) shouldBe true
      config.chunkRange shouldBe Some(0 until 5)
    }
  }
}
