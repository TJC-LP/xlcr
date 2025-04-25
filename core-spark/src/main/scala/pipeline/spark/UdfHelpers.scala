package com.tjclp.xlcr
package pipeline.spark

import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.{DataFrame, functions => F}
import zio.{Runtime, Unsafe, ZIO, Duration => ZDuration}

import java.time.Instant
import scala.concurrent.duration.{Duration => ScalaDuration}
import scala.reflect.runtime.universe.TypeTag

/** Utilities for wrapping arbitrary JVM functions into Spark UDFs that return a
  * StepResult[T] envelope with timing / error information.
  */
object UdfHelpers {

  // -----------------------------------------------------------------------
  // Model captured by all wrapped UDFs
  // -----------------------------------------------------------------------

  case class Lineage(
      startTimeMs: Long,
      endTimeMs: Long,
      durationMs: Long,
      error: Option[String] = None,
      name: String
  )

  case class StepResult[T](
      data: Option[T],
      lineage: Lineage
  )

  // -----------------------------------------------------------------------
  // Single‑arg variant with per‑row timeout
  // -----------------------------------------------------------------------

  def wrapUdf[A: TypeTag, R: TypeTag](
      name: String,
      timeout: ScalaDuration = ScalaDuration.Inf
  )(f: A => R): UserDefinedFunction = {
    val safe = (a: A) => executeTimed(timeout, name) { f(a) }
    F.udf(safe)
  }

  // -----------------------------------------------------------------------
  // Two‑arg variant
  // -----------------------------------------------------------------------

  def wrapUdf2[A: TypeTag, B: TypeTag, R: TypeTag](
      name: String,
      timeout: ScalaDuration = ScalaDuration.Inf
  )(f: (A, B) => R): UserDefinedFunction = {
    val safe = (a: A, b: B) => executeTimed(timeout, name) { f(a, b) }
    F.udf(safe)
  }

  /* ---------------- private helpers ------------------------------------ */

  private def executeTimed[R](
      timeout: ScalaDuration,
      name: String
  )(thunk: => R): StepResult[R] = {
    val start = Instant.now().toEpochMilli
    def fail(msg: String) = StepResult[R](
      None,
      Lineage(
        start,
        Instant.now().toEpochMilli,
        Instant.now().toEpochMilli - start,
        Some(msg),
        name = name
      )
    )

    try {
      val r: R =
        if (timeout.isFinite()) {
          val task = ZIO
            .attempt(thunk)
            .timeoutFail(new RuntimeException("timeout"))(
              ZDuration.fromScala(timeout)
            )
          Unsafe.unsafe { implicit u =>
            Runtime.default.unsafe.run(task).getOrThrowFiberFailure()
          }
        } else thunk

      val end = Instant.now().toEpochMilli
      StepResult(Some(r), Lineage(start, end, end - start, None, name))
    } catch {
      case t: Throwable => fail(t.getMessage)
    }
  }
}
