package com.tjclp.xlcr
package registration

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import types.{ Prioritized, Priority }

class PriorityRegistrySpec extends AnyFlatSpec with Matchers {

  // Mock implementation of Prioritized trait for testing
  case class MockImpl(name: String, override val priority: Priority)
      extends Prioritized

  "A PriorityRegistry" should "register implementations and return them by priority" in {
    val registry = new PriorityRegistry[String, MockImpl]()

    // Register multiple implementations with different priorities
    registry.register("key1", MockImpl("low", Priority.LOW))
    registry.register("key1", MockImpl("default", Priority.DEFAULT))
    registry.register("key1", MockImpl("high", Priority.HIGH))
    registry.register("key1", MockImpl("aspose", Priority.ASPOSE))

    // The highest priority implementation should be returned
    registry.get("key1").map(_.name) should be(Some("aspose"))

    // All implementations should be returned in descending priority order
    val allImpls = registry.getAll("key1").map(_.name)
    // Note: Our implementation replaces the same-class implementations
    // so all registrations of MockImpl will be replaced
    allImpls.size should be(1)
    allImpls should be(List("aspose"))
  }

  it should "replace implementations of the same class" in {
    val registry = new PriorityRegistry[String, MockImpl]()

    // Create class that extends MockImpl to test class-based replacement
    class SpecialMockImpl(name: String, p: Priority) extends MockImpl(name, p)

    // Register implementations of different classes
    registry.register("key1", MockImpl("impl1", Priority.DEFAULT))
    registry.register("key1", new SpecialMockImpl("impl2", Priority.LOW))

    // Both should be kept since they're different classes
    registry.getAll("key1").map(_.name) should be(List("impl1", "impl2"))

    // Register new implementation of existing class
    registry.register("key1", MockImpl("updated", Priority.HIGH))

    // The new implementation should replace the old one of the same class
    val result = registry.getAll("key1").map(_.name)
    result.size should be(2)
    result should be(List("updated", "impl2"))
  }

  it should "handle multiple keys" in {
    val registry = new PriorityRegistry[String, MockImpl]()

    registry.register("key1", MockImpl("impl1", Priority.DEFAULT))
    registry.register("key2", MockImpl("impl2", Priority.HIGH))
    registry.register("key3", MockImpl("impl3", Priority.ASPOSE))

    registry.get("key1").map(_.name) should be(Some("impl1"))
    registry.get("key2").map(_.name) should be(Some("impl2"))
    registry.get("key3").map(_.name) should be(Some("impl3"))

    (registry.keys should contain).allOf("key1", "key2", "key3")
  }

  it should "return None for non-existent keys" in {
    val registry = new PriorityRegistry[String, MockImpl]()

    registry.get("nonexistent") should be(None)
    registry.getAll("nonexistent") should be(List.empty)
  }

  it should "support custom priority values" in {
    val registry = new PriorityRegistry[String, MockImpl]()

    // We need to create different class instances for each priority
    // since our implementation replaces same-class implementations
    class Custom1 extends MockImpl("custom1", Priority.Custom(42))
    class Custom2 extends MockImpl("custom2", Priority.Custom(99))
    class Custom3 extends MockImpl("custom3", Priority.Custom(10))

    registry.register("key1", new Custom1)
    registry.register("key1", new Custom2)
    registry.register("key1", new Custom3)

    registry.get("key1").map(_.name) should be(Some("custom2"))

    // Check all implementations (we need different classes now)
    val allImpls = registry.getAll("key1").map(_.name)
    allImpls should be(List("custom2", "custom1", "custom3"))
  }

  it should "support int to priority implicit conversion" in {
    val registry = new PriorityRegistry[String, MockImpl]()

    // Use implicit conversion from Int to Priority.Custom
    val implWithIntPriority = MockImpl("implicit", 200)
    registry.register("key1", implWithIntPriority)

    registry.get("key1").map(_.name) should be(Some("implicit"))
    registry.get("key1").get.priority.value should be(200)
  }

  it should "clear all entries" in {
    val registry = new PriorityRegistry[String, MockImpl]()

    registry.register("key1", MockImpl("impl1", Priority.DEFAULT))
    registry.register("key2", MockImpl("impl2", Priority.HIGH))

    registry.size should be(2)

    registry.clear()

    registry.size should be(0)
    registry.get("key1") should be(None)
    registry.get("key2") should be(None)
  }

  it should "return entries as a map" in {
    val registry = new PriorityRegistry[String, MockImpl]()

    // To test this with multiple items, we need different classes
    class Impl1 extends MockImpl("impl1", Priority.DEFAULT)
    class Impl2 extends MockImpl("impl2", Priority.HIGH)
    class Impl3 extends MockImpl("impl3", Priority.LOW)

    registry.register("key1", new Impl1)
    registry.register("key1", new Impl2)
    registry.register("key2", new Impl3)

    val entries = registry.entries
    entries.size should be(2)
    entries("key1").name should be("impl2")
    entries("key2").name should be("impl3")
  }
}
