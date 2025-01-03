package com.tjclp.xlcr

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConfigSpec extends AnyFlatSpec with Matchers {

  "Config" should "parse valid command line arguments" in {
    val args = Array("input.xlsx", "output.json")
    val config = Config.parse(args)

    config.input shouldBe "input.xlsx"
    config.output shouldBe "output.json"
  }

  it should "throw IllegalArgumentException for insufficient arguments" in {
    val args = Array("input.xlsx") // Missing output argument

    val ex = intercept[IllegalArgumentException] {
      Config.parse(args)
    }
    ex.getMessage should include("Expected at least 2 args")
  }

  it should "throw IllegalArgumentException for too many arguments" in {
    val args = Array("input.xlsx", "output.json", "true")

    val config = Config.parse(args)
    config.diffMode shouldBe true
  }
}