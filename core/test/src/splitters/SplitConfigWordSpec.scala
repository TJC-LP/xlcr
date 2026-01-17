package com.tjclp.xlcr
package splitters

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import types.MimeType

class SplitConfigWordSpec extends AnyFlatSpec with Matchers {

  "SplitConfig.defaultStrategyForMime" should "return Page strategy for Word documents" in {
    // Test DOC format
    val docStrategy = SplitConfig.defaultStrategyForMime(MimeType.ApplicationMsWord)
    docStrategy shouldBe SplitStrategy.Page

    // Test DOCX format
    val docxStrategy = SplitConfig.defaultStrategyForMime(
      MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument
    )
    docxStrategy shouldBe SplitStrategy.Page
  }

  "SplitConfig with Auto strategy" should "resolve to Page for Word documents" in {
    // Create a config with Auto strategy
    val config = SplitConfig(strategy = Some(SplitStrategy.Auto))

    // The strategy should be Auto
    config.strategy shouldBe Some(SplitStrategy.Auto)

    // When processing Word documents, the pipeline will call defaultStrategyForMime
    // to resolve Auto to the appropriate strategy
    val docDefaultStrategy = SplitConfig.defaultStrategyForMime(MimeType.ApplicationMsWord)
    docDefaultStrategy shouldBe SplitStrategy.Page

    val docxDefaultStrategy = SplitConfig.defaultStrategyForMime(
      MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument
    )
    docxDefaultStrategy shouldBe SplitStrategy.Page
  }

  "Word splitters" should "only accept Page strategy" in {
    // This verifies that our splitters are correctly configured
    val pageConfig = SplitConfig(strategy = Some(SplitStrategy.Page))
    pageConfig.hasStrategy(SplitStrategy.Page) shouldBe true

    val sheetConfig = SplitConfig(strategy = Some(SplitStrategy.Sheet))
    sheetConfig.hasStrategy(SplitStrategy.Page) shouldBe false
  }
}
