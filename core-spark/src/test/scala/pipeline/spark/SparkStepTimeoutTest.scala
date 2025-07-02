package com.tjclp.xlcr
package pipeline.spark

import scala.concurrent.duration._

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import pipeline.spark.steps._
import types.MimeType

class SparkStepTimeoutTest extends AnyFunSuite with Matchers {

  test("SparkStep.withUdfTimeout should create a new step with the specified timeout") {
    // Test with SplitStep
    val originalSplit = SplitStep()
    originalSplit.udfTimeout shouldBe 60.seconds

    val modifiedSplit = SparkStep.withUdfTimeout(originalSplit, 120.seconds)
    modifiedSplit.udfTimeout shouldBe 120.seconds
    modifiedSplit.name shouldBe originalSplit.name

    // Ensure original is unchanged
    originalSplit.udfTimeout shouldBe 60.seconds
  }

  test("SparkStep.withUdfTimeout should work with all step types") {
    // DetectMime
    val detectMime     = DetectMime()
    val modifiedDetect = SparkStep.withUdfTimeout(detectMime, 45.seconds)
    modifiedDetect.udfTimeout shouldBe 45.seconds

    // ConvertStep
    val convertStep     = ConvertStep(to = MimeType.ApplicationPdf)
    val modifiedConvert = SparkStep.withUdfTimeout(convertStep, 90.seconds)
    modifiedConvert.udfTimeout shouldBe 90.seconds

    // ExtractStep
    val extractStep     = ExtractStep(to = MimeType.TextPlain, outCol = "extracted_text")
    val modifiedExtract = SparkStep.withUdfTimeout(extractStep, 180.seconds)
    modifiedExtract.udfTimeout shouldBe 180.seconds
  }

  test("Modified steps should be proper instances of their types") {
    val originalSplit = SplitStep()
    val modifiedSplit = SparkStep.withUdfTimeout(originalSplit, 120.seconds)

    // The modified step should still be a SplitStep
    modifiedSplit shouldBe a[SplitStep]
    modifiedSplit.asInstanceOf[SplitStep].udfTimeout shouldBe 120.seconds
  }
}
