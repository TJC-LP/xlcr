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
    config.enableCoordinateCorrection shouldBe true
    config.coordinateCorrectionValue shouldBe 2
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
      enableCoordinateCorrection = false,
      coordinateCorrectionValue = 5,
      threads = 4,
      verbose = true
    )
    
    config.input shouldBe "input.xlsx"
    config.output shouldBe "output.json"
    config.diffMode shouldBe true
    config.anchorThreshold shouldBe 3
    config.disableAnchorExtraction shouldBe true
    config.disableFormatAggregation shouldBe true
    config.enableCoordinateCorrection shouldBe false
    config.coordinateCorrectionValue shouldBe 5
    config.threads shouldBe 4
    config.verbose shouldBe true
  }
  
  it should "allow partial updates via copy" in {
    val baseConfig = SpreadsheetLLMConfig()
    
    // Update just the coordinate correction settings
    val updatedConfig = baseConfig.copy(
      enableCoordinateCorrection = false,
      coordinateCorrectionValue = 3
    )
    
    // Original config should be unchanged
    baseConfig.enableCoordinateCorrection shouldBe true
    baseConfig.coordinateCorrectionValue shouldBe 2
    
    // Updated config should have new values only for specified fields
    updatedConfig.enableCoordinateCorrection shouldBe false
    updatedConfig.coordinateCorrectionValue shouldBe 3
    
    // Other fields should be unchanged
    updatedConfig.anchorThreshold shouldBe baseConfig.anchorThreshold
    updatedConfig.disableAnchorExtraction shouldBe baseConfig.disableAnchorExtraction
  }
}