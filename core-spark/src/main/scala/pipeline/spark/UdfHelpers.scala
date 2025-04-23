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

  case class StepResult[T](
      data: Option[T],
      startTimeMs: Long,
      endTimeMs: Long,
      durationMs: Long,
      error: Option[String] = None,
      stepName: String = ""
  )

  // -----------------------------------------------------------------------
  // Single‑arg variant with per‑row timeout
  // -----------------------------------------------------------------------

  def wrapUdf[A: TypeTag, R: TypeTag](
      timeout: ScalaDuration = ScalaDuration.Inf
  )(f: A => R): UserDefinedFunction = {
    val safe = (a: A) => executeTimed(timeout) { f(a) }
    F.udf(safe)
  }

  // -----------------------------------------------------------------------
  // Two‑arg variant
  // -----------------------------------------------------------------------

  def wrapUdf2[A: TypeTag, B: TypeTag, R: TypeTag](
      timeout: ScalaDuration = ScalaDuration.Inf
  )(f: (A, B) => R): UserDefinedFunction = {
    val safe = (a: A, b: B) => executeTimed(timeout) { f(a, b) }
    F.udf(safe)
  }

  // -----------------------------------------------------------------------
  // Helper to unpack StepResult in DataFrame
  // -----------------------------------------------------------------------

  /** Unpacks a DataFrame column containing a StepResult into separate metrics columns
    * and extracts the data, using a fallback value if the operation failed.
    *
    * @param df The DataFrame with a result column containing a StepResult
    * @param resultCol The name of the column containing the StepResult
    * @param dataCol The name of the column to store the extracted data
    * @param fallbackCol The name of the column to use as fallback if the operation failed
    * @return A DataFrame with unpacked metrics and data
    */
  def unpackResult(
                    df: DataFrame,
                    resultCol: String = "result",
                    dataCol: String = "content",
                    fallbackCol: Option[String] = None
                  ): DataFrame = {
    // Add metrics columns using Unix timestamps
    val withMetrics = df
      .withColumn("step_name", F.expr(s"$resultCol.stepName"))
      .withColumn("duration_ms", F.expr(s"$resultCol.durationMs"))
      .withColumn("start_time_ms", F.expr(s"$resultCol.startTimeMs"))
      .withColumn("end_time_ms", F.expr(s"$resultCol.endTimeMs"))
      .withColumn("error", F.expr(s"$resultCol.error"))

    // Extract the actual data from StepResult, using fallback as backup if provided
    val withData = fallbackCol match {
      case Some(fallback) =>
        withMetrics.withColumn(
          dataCol,
          F.when(F.col("error").isNull, F.expr(s"$resultCol.data"))
            .otherwise(F.col(fallback))
        )
      case None =>
        withMetrics.withColumn(dataCol, F.expr(s"$resultCol.data"))
    }

    // Drop the intermediate result column and return
    withData.drop(resultCol)
  }

  /* ---------------- private helpers ------------------------------------ */

  private def executeTimed[R](
      timeout: ScalaDuration
  )(thunk: => R): StepResult[R] = {
    val start = Instant.now().toEpochMilli
    def fail(msg: String) = StepResult[R](
      None,
      start,
      Instant.now().toEpochMilli,
      Instant.now().toEpochMilli - start,
      Some(msg)
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
      StepResult(Some(r), start, end, end - start, None)
    } catch {
      case t: Throwable => fail(t.getMessage)
    }
  }
}
