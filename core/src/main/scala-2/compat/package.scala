package com.tjclp.xlcr

import scala.collection.JavaConverters._

package object compat {
  // Compatibility layer for Scala 2.12 which doesn't have scala.jdk.CollectionConverters
  object CollectionConverters {
    import scala.collection.convert.{DecorateAsJava, DecorateAsScala}
    
    object Implicits extends DecorateAsJava with DecorateAsScala
  }

  // Add toIntOption, toDoubleOption, and toBooleanOption to String in Scala 2.12
  implicit class StringOps(private val s: String) extends AnyVal {
    def toIntOption: Option[Int] = 
      try {
        Some(s.toInt)
      } catch {
        case _: NumberFormatException => None
      }
      
    def toDoubleOption: Option[Double] = 
      try {
        Some(s.toDouble)
      } catch {
        case _: NumberFormatException => None
      }
      
    def toBooleanOption: Option[Boolean] = 
      if (s.equalsIgnoreCase("true")) Some(true)
      else if (s.equalsIgnoreCase("false")) Some(false)
      else None
  }

  // Simple implementation of Using for Scala 2.12
  object Using {
    def resource[R <: AutoCloseable, A](resource: => R)(f: R => A): A = {
      val r = resource
      try {
        f(r)
      } finally {
        if (r != null) {
          r.close()
        }
      }
    }
  }
}