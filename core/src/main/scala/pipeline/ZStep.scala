package com.tjclp.xlcr.pipeline

import zio.{Duration, Task, ZIO}

import java.util.concurrent.TimeoutException

/**
 * An *effectful* pipeline step backed by ZIO `Task`.
 *
 * The motivation is to keep `PipelineStep` 100 % pure / synchronous while
 * giving callers an equally simple abstraction when they need:
 *   • time‑outs
 *   • parallelism and concurrency (`zipPar`, `collectAllPar`, …)
 *   • error capture / aggregation via `Either` or `Cause`
 *
 * Design goals remain the same: small surface, cross‑build compatible, no
 * extra dependencies besides ZIO (already on the class‑path).
 */
trait ZStep[-A, +B] { self =>

  /** Execute the step asynchronously, potentially failing with any Throwable. */
  def run(a: A): Task[B]

  /** Sequential composition (`>>>`). */
  final def andThen[C](next: ZStep[B, C]): ZStep[A, C] =
    (a: A) => self.run(a).flatMap(next.run)

  /** Map over the successful result. */
  final def map[C](f: B => C): ZStep[A, C] =
    (a: A) => self.run(a).map(f)



  /** Apply a timeout to **this** step only. */
  final def withTimeout(d: Duration): ZStep[A, B] =
    (a: A) => self.run(a).timeoutFail(new TimeoutException(s"Step timed out after $d"))(d)

  /* ------------------------------------------------------------------ */
  /* Branching helpers                                                   */
  /* ------------------------------------------------------------------ */

  /**
   * Run two downstream steps in *parallel* on the *same* intermediate value
   * and return their results as a Tuple2.
   */
  final def fanOut[C, D](left: ZStep[B, C], right: ZStep[B, D]): ZStep[A, (C, D)] =
    (a: A) =>
      for (
        b <- self.run(a);
        cd <- left.run(b).zipPar(right.run(b))
      ) yield cd

  /** Alias for `fanOut` that some callers may find more obvious. */
  final def split[C, D](left: ZStep[B, C], right: ZStep[B, D]): ZStep[A, (C, D)] =
    fanOut(left, right)
}

object ZStep {

  /** Lift a plain function returning `Task` into a `ZStep`. */
  def apply[A, B](f: A => Task[B]): ZStep[A, B] = (a: A) => f(a)

  /** Lift a *pure* synchronous `PipelineStep` into `ZStep`. */
  def fromSync[A, B](step: PipelineStep[A, B]): ZStep[A, B] =
    (a: A) => ZIO.attempt(step.run(a))

  /** A `ZStep` that always succeeds with the provided value (identity for Any). */
  def succeed[B](value: B): ZStep[Any, B] = (_: Any) => ZIO.succeed(value)
}
