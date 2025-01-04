package com.tjclp.xlcr
package base

import bridges.Bridge
import renderers.Renderer
import models.{FileContent, Model}
import types.{MimeType, Mergeable}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalacheck.Gen

trait PropertySpec extends AnyFlatSpec
  with Matchers
  with ScalaCheckPropertyChecks
  with BridgeSpec {

  /**
   * Property test for verifying that a bridge's convert method
   * always produces valid output mime type.
   */
  def testBridgeTypes[I <: MimeType, M <: Model, O <: MimeType](
    bridge: Bridge[M, I, O],
    gen: Gen[FileContent[I]]
  ): Unit = {
    forAll(gen) { input =>
      val result = bridge.convert(input)
      result.mimeType shouldBe bridge.render(bridge.parseInput(input)).mimeType
    }
  }

  /**
   * Property test to check model merging invariants.
   */
  def testMergeableModel[M <: Model with Mergeable[M]](
    gen: Gen[M],
    checkInvariants: M => Unit
  ): Unit = {
    forAll(gen, gen) { (m1, m2) =>
      val merged = m1.merge(m2)
      checkInvariants(merged)
    }
  }
}