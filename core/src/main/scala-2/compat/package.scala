package com.tjclp.xlcr

package object compat {
  // Keep only Using for Scala 2.12 since it's not available in the standard library
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
