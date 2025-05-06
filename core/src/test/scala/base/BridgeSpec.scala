package com.tjclp.xlcr
package base

import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import bridges.Bridge
import models.{ FileContent, Model }
import parsers.Parser
import renderers.Renderer
import types.MimeType

trait BridgeSpec extends AnyFlatSpec with Matchers with BeforeAndAfter {

  /**
   * Test an entire bridge conversion end-to-end
   */
  def testBridge[I <: MimeType, M <: Model, O <: MimeType](
    bridge: Bridge[M, I, O],
    input: FileContent[I],
    expected: FileContent[O]
  ): Unit = {
    val result = bridge.convert(input)
    compareContent(result, expected)
  }

  /**
   * Compare two FileContent objects for equivalence
   */
  protected def compareContent[T <: MimeType](
    actual: FileContent[T],
    expected: FileContent[T]
  ): Unit = {
    actual.mimeType shouldBe expected.mimeType
    actual.data shouldBe expected.data
  }

  /**
   * Test parsing behavior
   */
  def testParser[I <: MimeType, M <: Model](
    parser: Parser[I, M],
    input: FileContent[I],
    expected: M
  ): Unit = {
    val result = parser.parse(input)
    result shouldBe expected
  }

  /**
   * Test rendering behavior
   */
  def testRenderer[M <: Model, O <: MimeType](
    renderer: Renderer[M, O],
    model: M,
    expected: FileContent[O]
  ): Unit = {
    val result = renderer.render(model)
    compareContent(result, expected)
  }
}
