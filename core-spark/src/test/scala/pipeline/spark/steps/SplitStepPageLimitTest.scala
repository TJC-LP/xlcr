package com.tjclp.xlcr
package pipeline.spark
package steps

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import splitters.SplitStrategy

class SplitStepPageLimitTest extends AnyFunSuite with Matchers {

  test("withPageLimit creates correct configuration") {
    val split = SplitStep.withPageLimit(10)
    
    split.config.strategy shouldBe Some(SplitStrategy.Page)
    split.config.pageRange shouldBe Some(0 until 10)
    split.udfTimeout.toSeconds shouldBe 60 // default timeout
  }

  test("withPageLimit with custom timeout") {
    import scala.concurrent.duration._
    val split = SplitStep.withPageLimit(20, udfTimeout = 120.seconds)
    
    split.config.strategy shouldBe Some(SplitStrategy.Page)
    split.config.pageRange shouldBe Some(0 until 20)
    split.udfTimeout.toSeconds shouldBe 120
  }

  test("withPageRange creates correct configuration") {
    val split = SplitStep.withPageRange(5, 15)
    
    split.config.strategy shouldBe Some(SplitStrategy.Page)
    split.config.pageRange shouldBe Some(5 until 15)
    split.udfTimeout.toSeconds shouldBe 60
  }

  test("withPageRange with custom timeout") {
    import scala.concurrent.duration._
    val split = SplitStep.withPageRange(10, 30, udfTimeout = 90.seconds)
    
    split.config.strategy shouldBe Some(SplitStrategy.Page)
    split.config.pageRange shouldBe Some(10 until 30)
    split.udfTimeout.toSeconds shouldBe 90
  }

  test("auto with page limit creates correct configuration") {
    val split = SplitStep.auto(pageLimit = Some(50))
    
    split.config.strategy shouldBe Some(SplitStrategy.Auto)
    split.config.pageRange shouldBe Some(0 until 50)
    split.udfTimeout.toSeconds shouldBe 60
  }

  test("auto without page limit creates correct configuration") {
    val split = SplitStep.auto(pageLimit = None)
    
    split.config.strategy shouldBe Some(SplitStrategy.Auto)
    split.config.pageRange shouldBe None
    split.udfTimeout.toSeconds shouldBe 60
  }

  test("page ranges work with different sizes") {
    // Single page
    val singlePage = SplitStep.withPageRange(0, 1)
    singlePage.config.pageRange.get.size shouldBe 1
    singlePage.config.pageRange.get.toList shouldBe List(0)
    
    // Multiple pages
    val multiPage = SplitStep.withPageRange(5, 10)
    multiPage.config.pageRange.get.size shouldBe 5
    multiPage.config.pageRange.get.toList shouldBe List(5, 6, 7, 8, 9)
    
    // Large range
    val largeRange = SplitStep.withPageLimit(1000)
    largeRange.config.pageRange.get.size shouldBe 1000
    largeRange.config.pageRange.get.head shouldBe 0
    largeRange.config.pageRange.get.last shouldBe 999
  }
}