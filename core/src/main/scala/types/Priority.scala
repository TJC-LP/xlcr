package com.tjclp.xlcr
package types

/**
 * Priority levels for implementations to be used in registration systems.
 * Higher values represent higher priority implementations.
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
   * Highest priority reserved for special cases
   */
  case object HIGHEST extends Priority { val value = 1000 }

  /**
   * Commercial implementations like Aspose
   */
  case object ASPOSE extends Priority { val value = 100 }

  /**
   * High-quality implementations
   */
  case object HIGH extends Priority { val value = 75 }

  /**
   * Core implementations provided by the base library
   */
  case object CORE extends Priority { val value = 50 }

  /**
   * Default implementations
   */
  case object DEFAULT extends Priority { val value = 0 }

  /**
   * Low priority implementations (fallbacks)
   */
  case object LOW extends Priority { val value = -50 }

  /**
   * Lowest priority implementations (last resort)
   */
  case object LOWEST extends Priority { val value = -100 }

  /**
   * Custom priority with a specific value
   */
  case class Custom(value: Int) extends Priority

  /**
   * Implicit conversion from Int to Priority.Custom
   */
  implicit def intToPriority(value: Int): Priority = Custom(value)
}