package com.tjclp.xlcr
package splitters

import models.FileContent
import types.{MimeType, Priority}
import utils.PriorityRegistry

import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PrioritizedDocumentSplitterSpec
    extends AnyFlatSpec
    with Matchers
    with BeforeAndAfterEach {
  // Use a unique mime type for testing to avoid conflicts with real splitters
  val testMimeType: MimeType =
    MimeType("test", "x-priority", Map("test" -> "true"))

  // Sample DocumentSplitter implementations with different priorities
  class LowPriorityTestSplitter extends DocumentSplitter[testMimeType.type] {
    override def priority: Priority = Priority.LOW

    override def split(
        content: FileContent[testMimeType.type],
        cfg: SplitConfig
    ): Seq[DocChunk[_ <: MimeType]] = {
      Seq(DocChunk(content, "low-priority-splitter", 0, 1))
    }
  }

  class DefaultPriorityTestSplitter
      extends DocumentSplitter[testMimeType.type] {
    override def priority: Priority = Priority.DEFAULT

    override def split(
        content: FileContent[testMimeType.type],
        cfg: SplitConfig
    ): Seq[DocChunk[_ <: MimeType]] = {
      Seq(DocChunk(content, "default-priority-splitter", 0, 1))
    }
  }

  class HighPriorityTestSplitter extends DocumentSplitter[testMimeType.type] {
    override def priority: Priority = Priority.HIGH

    override def split(
        content: FileContent[testMimeType.type],
        cfg: SplitConfig
    ): Seq[DocChunk[_ <: MimeType]] = {
      Seq(DocChunk(content, "high-priority-splitter", 0, 1))
    }
  }

  class AsposePriorityTestSplitter extends DocumentSplitter[testMimeType.type] {
    override def priority: Priority = Priority.ASPOSE

    override def split(
        content: FileContent[testMimeType.type],
        cfg: SplitConfig
    ): Seq[DocChunk[_ <: MimeType]] = {
      Seq(DocChunk(content, "aspose-priority-splitter", 0, 1))
    }
  }

  // Create a wrapper method that uses a separate PriorityRegistry for isolated testing
  class TestRegistry {
    private val registry =
      new PriorityRegistry[MimeType, DocumentSplitter[_ <: MimeType]]()

    def register[I <: MimeType](
        mime: MimeType,
        splitter: DocumentSplitter[I]
    ): Unit = {
      registry.register(
        mime,
        splitter.asInstanceOf[DocumentSplitter[_ <: MimeType]]
      )
    }

    def getAll(mime: MimeType): List[DocumentSplitter[_ <: MimeType]] = {
      registry.getAll(mime)
    }

    def get(mime: MimeType): Option[DocumentSplitter[_ <: MimeType]] = {
      registry.get(mime)
    }

    def split(
        content: FileContent[_ <: MimeType],
        cfg: SplitConfig
    ): Seq[DocChunk[_ <: MimeType]] = {
      val splitterOpt = registry.get(content.mimeType)
      splitterOpt
        .map(_.asInstanceOf[DocumentSplitter[MimeType]].split(content, cfg))
        .getOrElse(Seq(DocChunk(content, "default", 0, 1)))
    }
  }

  "A PriorityRegistry for DocumentSplitter" should "use the highest priority splitter when multiple are registered" in {
    val registry = new TestRegistry()

    // Create test content
    val testContent = FileContent("test content".getBytes, testMimeType)
      .asInstanceOf[FileContent[testMimeType.type]]

    // Register splitters from lowest to highest priority
    registry.register(testMimeType, new LowPriorityTestSplitter())
    registry.register(testMimeType, new DefaultPriorityTestSplitter())
    registry.register(testMimeType, new HighPriorityTestSplitter())
    registry.register(testMimeType, new AsposePriorityTestSplitter())

    // Split the content using our test registry
    val result = registry.split(testContent, SplitConfig())

    // Should use the highest priority (ASPOSE) splitter
    result.size should be(1)
    result.head.label should be("aspose-priority-splitter")
  }

  it should "use a newly registered higher priority splitter" in {
    val registry = new TestRegistry()

    // Register splitters in random order
    registry.register(testMimeType, new HighPriorityTestSplitter())
    registry.register(testMimeType, new LowPriorityTestSplitter())

    // Create test content
    val testContent = FileContent("test content".getBytes, testMimeType)
      .asInstanceOf[FileContent[testMimeType.type]]

    // Split the content - should use HIGH priority
    val result1 = registry.split(testContent, SplitConfig())
    result1.size should be(1)
    result1.head.label should be("high-priority-splitter")

    // Now register a higher priority splitter
    registry.register(testMimeType, new AsposePriorityTestSplitter())

    // Split again - should now use ASPOSE priority
    val result2 = registry.split(testContent, SplitConfig())
    result2.size should be(1)
    result2.head.label should be("aspose-priority-splitter")
  }

  it should "replace a splitter of the same class with a different priority" in {
    val registry = new TestRegistry()

    // Create splitters of the same class but different priorities
    class ReplacementTestSplitter extends DocumentSplitter[testMimeType.type] {
      private var priorityValue: Priority = Priority.DEFAULT

      // Allow setting priority for testing
      def setPriority(p: Priority): Unit = { priorityValue = p }

      override def priority: Priority = priorityValue

      override def split(
          content: FileContent[testMimeType.type],
          cfg: SplitConfig
      ): Seq[DocChunk[_ <: MimeType]] = {
        Seq(DocChunk(content, s"priority-${priority.value}", 0, 1))
      }
    }

    // Register a default splitter
    val defaultSplitter = new ReplacementTestSplitter()
    registry.register(testMimeType, defaultSplitter)

    // Create test content
    val testContent = FileContent("test content".getBytes, testMimeType)
      .asInstanceOf[FileContent[testMimeType.type]]

    // Check the original
    val result1 = registry.split(testContent, SplitConfig())
    result1.head.label should be("priority-0") // DEFAULT priority value is 0

    // Create a new instance with different priority
    val highSplitter = new ReplacementTestSplitter()
    highSplitter.setPriority(Priority.HIGH)
    registry.register(testMimeType, highSplitter)

    // Check that it has the new priority
    val result2 = registry.split(testContent, SplitConfig())
    result2.head.label should be(s"priority-${Priority.HIGH.value}")
  }

  it should "allow retrieving all registered splitters for a MIME type" in {
    val registry = new TestRegistry()

    // Register multiple splitters
    registry.register(testMimeType, new LowPriorityTestSplitter())
    registry.register(testMimeType, new DefaultPriorityTestSplitter())
    registry.register(testMimeType, new HighPriorityTestSplitter())
    registry.register(testMimeType, new AsposePriorityTestSplitter())

    // Get all splitters
    val allSplitters = registry.getAll(testMimeType)

    // Should have correct number of splitters (one of each class)
    allSplitters.size should be(4)

    // Should be in priority order (highest first)
    val priorities = allSplitters.map(_.priority.value)
    priorities should be(priorities.sorted.reverse)

    // The first one should be ASPOSE priority
    allSplitters.head.priority should be(Priority.ASPOSE)
  }
}
