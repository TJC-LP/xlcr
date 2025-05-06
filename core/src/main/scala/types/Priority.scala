package com.tjclp.xlcr
package types

/**
 * Priority levels for implementations to be used in registration systems. Higher values represent
 * higher priority implementations.
 */
sealed trait Priority extends Ordered[Priority] {
  def value: Int

  override def compare(that: Priority): Int = this.value.compare(that.value)
}

/**
 * Standard priority levels for implementation registration.
 */
object Priority {

  /**
   * High priority implementations (commercial solutions like Aspose)
   */
  case object HIGH extends Priority { val value = 100 }

  /**
   * Default priority for most implementations
   */
  case object DEFAULT extends Priority { val value = 0 }

  /**
   * Low priority implementations (fallbacks)
   */
  case object LOW extends Priority { val value = -100 }

  /**
   * Custom priority with a specific value - for backward compatibility
   */
  case class Custom(value: Int) extends Priority

  /**
   * Implicit conversion from Int to Priority.Custom - for backward compatibility
   */
  implicit def intToPriority(value: Int): Priority = Custom(value)

  /**
   * Mapping from legacy priority values to new simplified system Used for backward compatibility in
   * existing code
   */
  @deprecated("Use Priority.HIGH instead", "2.0.0")
  val HIGHEST: Priority = HIGH

  @deprecated("Use Priority.HIGH instead", "2.0.0")
  val ASPOSE: Priority = HIGH

  @deprecated("Use Priority.DEFAULT instead", "2.0.0")
  val CORE: Priority = DEFAULT

  @deprecated("Use Priority.LOW instead", "2.0.0")
  val LOWEST: Priority = LOW
}
