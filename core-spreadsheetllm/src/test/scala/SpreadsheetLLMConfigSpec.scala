package com.tjclp.xlcr

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SpreadsheetLLMConfigSpec extends AnyFlatSpec with Matchers {

  "SpreadsheetLLMConfig" should "use default values when not specified" in {
    val config = SpreadsheetLLMConfig()

    config.input shouldBe ""
    config.output shouldBe ""
    config.diffMode shouldBe false
    config.anchorThreshold shouldBe 1
    config.disableAnchorExtraction shouldBe false
    config.disableFormatAggregation shouldBe false
    config.preserveOriginalCoordinates shouldBe true
    config.threads should be > 0
    config.verbose shouldBe false
  }

  it should "allow custom values to be set" in {
    val config = SpreadsheetLLMConfig(
      input = "input.xlsx",
      output = "output.json",
      diffMode = true,
      anchorThreshold = 3,
      disableAnchorExtraction = true,
      disableFormatAggregation = true,
      preserveOriginalCoordinates = false,
      threads = 4,
      verbose = true
    )

    config.input shouldBe "input.xlsx"
    config.output shouldBe "output.json"
    config.diffMode shouldBe true
    config.anchorThreshold shouldBe 3
    config.disableAnchorExtraction shouldBe true
    config.disableFormatAggregation shouldBe true
    config.preserveOriginalCoordinates shouldBe false
    config.threads shouldBe 4
    config.verbose shouldBe true
  }

  it should "allow partial updates via copy" in {
    val baseConfig = SpreadsheetLLMConfig()

    // Update just the coordinate preservation setting
    val updatedConfig = baseConfig.copy(
      preserveOriginalCoordinates = false
    )

    // Original config should be unchanged
    baseConfig.preserveOriginalCoordinates shouldBe true

    // Updated config should have new values only for specified fields
    updatedConfig.preserveOriginalCoordinates shouldBe false

    // Other fields should be unchanged
    updatedConfig.anchorThreshold shouldBe baseConfig.anchorThreshold
    updatedConfig.disableAnchorExtraction shouldBe baseConfig.disableAnchorExtraction
  }
}
