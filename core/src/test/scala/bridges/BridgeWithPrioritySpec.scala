package com.tjclp.xlcr
package bridges

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import models.FileContent
import parsers.Parser
import renderers.Renderer
import types.{ MimeType, Priority }

class BridgeWithPrioritySpec extends AnyFlatSpec with Matchers {

  // Define test MIME types using MimeType.Custom
  val TestMimeType: MimeType  = MimeType.Custom("application", "x-test")
  val OtherMimeType: MimeType = MimeType.Custom("application", "x-other")

  // Define a simple test parser
  class TestParser extends Parser[MimeType, TestModel] {
    override def parse(
      input: FileContent[MimeType],
      config: Option[parsers.ParserConfig] = None
    ): TestModel =
      TestModel(new String(input.data))
  }

  // Define a simple test renderer
  class TestRenderer extends Renderer[TestModel, MimeType] {
    override def render(
      model: TestModel,
      config: Option[renderers.RendererConfig] = None
    ): FileContent[MimeType] =
      new FileContent[MimeType](model.content.getBytes, OtherMimeType)
  }

  // Create a test bridge implementation
  class TestBridge extends Bridge[TestModel, MimeType, MimeType] {
    private val testParser   = new TestParser()
    private val testRenderer = new TestRenderer()

    override def inputParser: Parser[MimeType, TestModel]      = testParser
    override def outputRenderer: Renderer[TestModel, MimeType] = testRenderer
  }

  "A bridge" should "have default priority when created" in {
    val bridge = new TestBridge()
    bridge.priority shouldBe Priority.DEFAULT
  }

  it should "create a new instance with different priority when withPriority is called" in {
    val bridge             = new TestBridge()
    val highPriorityBridge = bridge.withPriority(Priority.HIGH)

    // Check it's a different instance
    highPriorityBridge should not be theSameInstanceAs(bridge)

    // Check priority was updated
    highPriorityBridge.priority shouldBe Priority.HIGH
    bridge.priority shouldBe Priority.DEFAULT
  }

  it should "keep the original functionality after withPriority" in {
    val bridge             = new TestBridge()
    val highPriorityBridge = bridge.withPriority(Priority.HIGH)

    // Test input
    val input = new FileContent[MimeType]("test content".getBytes, TestMimeType)

    // Convert with both bridges
    val originalResult = bridge.convert(input)
    val priorityResult = highPriorityBridge.convert(input)

    // Results should be identical
    originalResult.mimeType shouldBe priorityResult.mimeType
    originalResult.data shouldBe priorityResult.data

    // But bridges should have different priorities
    bridge.priority shouldBe Priority.DEFAULT
    highPriorityBridge.priority shouldBe Priority.HIGH
  }

  it should "support custom priority values" in {
    val bridge               = new TestBridge()
    val customPriorityBridge = bridge.withPriority(Priority.Custom(42))

    customPriorityBridge.priority shouldBe Priority.Custom(42)
    customPriorityBridge.priority.value shouldBe 42
  }

//  it should "maintain the runtime type information" in {
//    val bridge             = new TestBridge()
//    val highPriorityBridge = bridge.withPriority(Priority.HIGH)
//
//    // ClassTags should be preserved
//    highPriorityBridge.mTag.runtimeClass shouldBe bridge.mTag.runtimeClass
//    highPriorityBridge.iTag.runtimeClass shouldBe bridge.iTag.runtimeClass
//    highPriorityBridge.oTag.runtimeClass shouldBe bridge.oTag.runtimeClass
//  }
}
