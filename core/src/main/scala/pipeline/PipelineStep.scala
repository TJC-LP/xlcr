package com.tjclp.xlcr
package pipeline

/** A minimal, purely functional building block for document‑processing
  * pipelines.  A `PipelineStep` is just a typed transformation `A => B` with
  * an `andThen` combinator so steps can be chained in a natural way:
  *
  *   val pipeline = step1.andThen(step2).andThen(step3)
  *
  * The trait is kept very small on purpose – we will add helpers such as
  * branching or effect handling in separate files so that the core remains
  * dependency‑free and cross‑compilable (Scala 2.12 & 3).
  */
trait PipelineStep[-A, +B] { self =>

  /** Execute this pipeline step. */
  def run(input: A): B

  /** Sequential composition. */
  final def andThen[C](next: PipelineStep[B, C]): PipelineStep[A, C] =
    (a: A) => next.run(self.run(a))
}

object PipelineStep {

  /** Lift a plain function into a PipelineStep. */
  def apply[A, B](f: A => B): PipelineStep[A, B] = (input: A) => f(input)

  /* ------------------------------------------------------------------ */
  /* Extra combinators (branching, fan‑out, etc.)                       */
  /* ------------------------------------------------------------------ */

  implicit final class StepOps[A, B](private val self: PipelineStep[A, B])
      extends AnyVal {

    /** Run *two* downstream steps on the same input value and return their
      * results as a Tuple2.  This gives a simple form of *branching* without
      * introducing additional data structures:
      *
      *   val base   = SplitStep()
      *   val branch = base.fanOut(toPdf, toText)
      */
    def fanOut[C, D](
        left: PipelineStep[B, C],
        right: PipelineStep[B, D]
    ): PipelineStep[A, (C, D)] =
      (a: A) => {
        val b = self.run(a)
        (left.run(b), right.run(b))
      }

    /** Alias for `fanOut` that may read better in some contexts. */
    def split[C, D](
        left: PipelineStep[B, C],
        right: PipelineStep[B, D]
    ): PipelineStep[A, (C, D)] =
      fanOut(left, right)
  }
}
