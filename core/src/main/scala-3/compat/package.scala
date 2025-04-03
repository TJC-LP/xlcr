package com.tjclp.xlcr

import scala.jdk.CollectionConverters._

package object compat {
  // Compatibility layer for Scala 3
  object CollectionConverters {
    // Just use the modern scala.jdk.CollectionConverters
    object Implicits {
      // Import all the conversion methods
      export scala.jdk.CollectionConverters._
    }
  }

  // In Scala 3, Using is already available in scala.util
  export scala.util.Using

  // Add StringOps implicit class to match Scala 2.12 imports
  // These already exist in Scala 3, just for compatibility with Scala 2.12 imports
  implicit class StringOps(private val s: String) extends AnyVal {
    def toIntOption: Option[Int] = scala.util.Try(s.toInt).toOption
    def toDoubleOption: Option[Double] = scala.util.Try(s.toDouble).toOption
    def toBooleanOption: Option[Boolean] = 
      if (s.equalsIgnoreCase("true")) Some(true)
      else if (s.equalsIgnoreCase("false")) Some(false)
      else None
  }
}