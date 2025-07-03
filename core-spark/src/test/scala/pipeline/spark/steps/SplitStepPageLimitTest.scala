package com.tjclp.xlcr
package pipeline.spark
package steps

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import splitters.SplitStrategy

class SplitStepPageLimitTest extends AnyFunSuite with Matchers {

  test("withChunkLimit creates correct configuration") {
    val split = SplitStep.withChunkLimit(10)
    
    split.config.strategy shouldBe Some(SplitStrategy.Auto)
    split.config.chunkRange shouldBe Some(0 until 10)
    split.udfTimeout.toSeconds shouldBe 60 // default timeout
  }

  test("withChunkLimit with custom timeout and strategy") {
    import scala.concurrent.duration._
    val split = SplitStep.withChunkLimit(20, Some(SplitStrategy.Page), udfTimeout = 120.seconds)
    
    split.config.strategy shouldBe Some(SplitStrategy.Page)
    split.config.chunkRange shouldBe Some(0 until 20)
    split.udfTimeout.toSeconds shouldBe 120
  }

  test("withChunkRange creates correct configuration") {
    val split = SplitStep.withChunkRange(5, 15)
    
    split.config.strategy shouldBe Some(SplitStrategy.Auto)
    split.config.chunkRange shouldBe Some(5 until 15)
    split.udfTimeout.toSeconds shouldBe 60
  }

  test("withChunkRange with custom timeout and strategy") {
    import scala.concurrent.duration._
    val split = SplitStep.withChunkRange(10, 30, Some(SplitStrategy.Sheet), udfTimeout = 90.seconds)
    
    split.config.strategy shouldBe Some(SplitStrategy.Sheet)
    split.config.chunkRange shouldBe Some(10 until 30)
    split.udfTimeout.toSeconds shouldBe 90
  }

  test("auto with chunk limit creates correct configuration") {
    val split = SplitStep.auto(chunkLimit = Some(50))
    
    split.config.strategy shouldBe Some(SplitStrategy.Auto)
    split.config.chunkRange shouldBe Some(0 until 50)
    split.udfTimeout.toSeconds shouldBe 60
  }

  test("auto without chunk limit creates correct configuration") {
    val split = SplitStep.auto(chunkLimit = None)
    
    split.config.strategy shouldBe Some(SplitStrategy.Auto)
    split.config.chunkRange shouldBe None
    split.udfTimeout.toSeconds shouldBe 60
  }

  test("chunk ranges work with different sizes") {
    // Single chunk
    val singleChunk = SplitStep.withChunkRange(0, 1)
    singleChunk.config.chunkRange.get.size shouldBe 1
    singleChunk.config.chunkRange.get.toList shouldBe List(0)
    
    // Multiple chunks
    val multiChunk = SplitStep.withChunkRange(5, 10)
    multiChunk.config.chunkRange.get.size shouldBe 5
    multiChunk.config.chunkRange.get.toList shouldBe List(5, 6, 7, 8, 9)
    
    // Large range
    val largeRange = SplitStep.withChunkLimit(1000)
    largeRange.config.chunkRange.get.size shouldBe 1000
    largeRange.config.chunkRange.get.head shouldBe 0
    largeRange.config.chunkRange.get.last shouldBe 999
  }

  test("deprecated methods still work") {
    // Test backward compatibility
    val pageLimit = SplitStep.withPageLimit(10)
    pageLimit.config.strategy shouldBe Some(SplitStrategy.Page)
    pageLimit.config.chunkRange shouldBe Some(0 until 10)
    
    val pageRange = SplitStep.withPageRange(5, 15)
    pageRange.config.strategy shouldBe Some(SplitStrategy.Page)
    pageRange.config.chunkRange shouldBe Some(5 until 15)
  }
}