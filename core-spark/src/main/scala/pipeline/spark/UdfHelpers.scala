package com.tjclp.xlcr
package pipeline.spark

import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.{DataFrame, Column, functions => F}
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
      name: String,
      implementation: Option[String] = None,  // Added to track the specific implementation used
      params: Option[Map[String, String]] = None  // Added to track additional parameters
  )

  case class StepResult[T](
      data: Option[T],
      lineage: Lineage
  )

  // -----------------------------------------------------------------------
  // Helper to append lineage entry to lineage array
  // -----------------------------------------------------------------------
  
  def appendLineageEntry(df: DataFrame, lineageEntry: Column): DataFrame = {
    import org.apache.spark.sql.functions.{col, array, when, array_union}
    
    // Ensure lineage column exists with proper type
    val withLineageCol = 
      if (df.columns.contains(CoreSchema.Lineage)) df
      else df.withColumn(CoreSchema.Lineage, array().cast(CoreSchema.LineageArrayType))
    
    // Add the lineage entry to the lineage array
    withLineageCol.withColumn(
      CoreSchema.Lineage, 
      when(col(CoreSchema.Lineage).isNull, array(lineageEntry))
        .otherwise(array_union(col(CoreSchema.Lineage), array(lineageEntry)))
    )
  }
  
  // -----------------------------------------------------------------------
  // Single‑arg variant with per‑row timeout
  // -----------------------------------------------------------------------

  def wrapUdf[A: TypeTag, R: TypeTag](
      name: String,
      timeout: ScalaDuration = ScalaDuration.Inf,
      implementation: Option[String] = None,
      params: Option[Map[String, String]] = None
  )(f: A => (R, Option[String], Option[Map[String, String]])): UserDefinedFunction = {
    val safe = (a: A) => executeTimed(timeout, name, implementation, params) { f(a) }
    F.udf(safe)
  }

  // For compatibility with existing code that doesn't track implementation
  def wrapUdf[A: TypeTag, R: TypeTag](
      name: String,
      timeout: ScalaDuration,
      f: A => R
  ): UserDefinedFunction = {
    val wrappedF = (a: A) => (f(a), None: Option[String], None: Option[Map[String, String]])
    wrapUdf(name, timeout, None, None)(wrappedF)
  }

  // -----------------------------------------------------------------------
  // Two‑arg variant
  // -----------------------------------------------------------------------

  def wrapUdf2[A: TypeTag, B: TypeTag, R: TypeTag](
      name: String,
      timeout: ScalaDuration = ScalaDuration.Inf,
      implementation: Option[String] = None,
      params: Option[Map[String, String]] = None
  )(f: (A, B) => (R, Option[String], Option[Map[String, String]])): UserDefinedFunction = {
    val safe = (a: A, b: B) => executeTimed(timeout, name, implementation, params) { f(a, b) }
    F.udf(safe)
  }
  
  // For compatibility with existing code that doesn't track implementation
  def wrapUdf2[A: TypeTag, B: TypeTag, R: TypeTag](
      name: String,
      timeout: ScalaDuration,
      f: (A, B) => R
  ): UserDefinedFunction = {
    val wrappedF = (a: A, b: B) => (f(a, b), None: Option[String], None: Option[Map[String, String]])
    wrapUdf2(name, timeout, None, None)(wrappedF)
  }

  /* ---------------- private helpers ------------------------------------ */

  private def executeTimed[R](
      timeout: ScalaDuration,
      name: String,
      defaultImplementation: Option[String] = None,
      defaultParams: Option[Map[String, String]] = None
  )(thunk: => (R, Option[String], Option[Map[String, String]])): StepResult[R] = {
    val start = Instant.now().toEpochMilli
    def fail(msg: String) = StepResult[R](
      None,
      Lineage(
        start,
        Instant.now().toEpochMilli,
        Instant.now().toEpochMilli - start,
        Some(msg),
        name,
        defaultImplementation,
        defaultParams
      )
    )

    try {
      val (result, implName, params): (R, Option[String], Option[Map[String, String]]) =
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
      // Use provided implementation info or fall back to defaults
      val finalImplName = implName.orElse(defaultImplementation)
      val finalParams = params.orElse(defaultParams)
      
      StepResult(
        Some(result), 
        Lineage(start, end, end - start, None, name, finalImplName, finalParams)
      )
    } catch {
      case t: Throwable => fail(t.getMessage)
    }
  }
}
