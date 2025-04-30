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

  /**
    * Metadata about an individual chunk produced by a `SplitStep` (or another
    * nested-document producing operation).  Placing this information directly
    * on the lineage element allows us to keep track of **arbitrarily-nested**
    * structures – e.g. *zip → email → attachment → page* – without having to
    * promote those fields to top-level dataframe columns that would be
    * overwritten on every subsequent split.
    */
  case class ChunkMeta(
      chunkIndex: Option[Long],      // 0-based position within parent
      chunkTotal: Option[Long],      // total number of chunks produced
      chunkLabel: Option[String]
  )

  /** Captures timing / implementation / error information for a pipeline
    * operation.  A new `Lineage` element is appended by every wrapped UDF.
    *
    * A **nullable** [[ChunkMeta]] field is included so that split-style steps
    * can – optionally – embed their per-chunk context without polluting the
    * root schema.  For non-splitting steps the field is simply left `None`.
    */
  case class Lineage(
      startTimeMs: Long,
      endTimeMs: Long,
      durationMs: Long,
      error: Option[String] = None,
      name: String,
      implementation: Option[String] = None,  // specific implementation used, if any
      params: Option[Map[String, String]] = None,  // additional parameters captured by the step
      sourceId: Option[String] = None,             // id of the (current) source doc/row
      chunk: Option[ChunkMeta] = None           // per-chunk context (nullable)
  )

  case class StepResult[T](
      data: Option[T],
      lineage: Lineage
  )

  // -----------------------------------------------------------------------
  // Helper to append lineage entry to lineage array
  // -----------------------------------------------------------------------
  
  import org.apache.spark.sql.functions.{col, array, when, array_append}

  def appendLineageEntry(df: DataFrame, rawLineageEntry: Column): DataFrame = {
    // Enrich the incoming lineage struct with current row id as sourceId if
    // not already populated.
    val lineageEntry = rawLineageEntry.withField(
      "sourceId",
      when(rawLineageEntry.getField("sourceId").isNull, col(CoreSchema.Id))
        .otherwise(rawLineageEntry.getField("sourceId"))
    )

    // Ensure lineage column exists with proper type
    val withLineageCol =
      if (df.columns.contains(CoreSchema.Lineage)) df
      else df.withColumn(CoreSchema.Lineage, array().cast(CoreSchema.LineageArrayType))

    // Add the lineage entry to the lineage array
    withLineageCol.withColumn(
      CoreSchema.Lineage,
      when(col(CoreSchema.Lineage).isNull, array(lineageEntry))
        .otherwise(array_append(col(CoreSchema.Lineage), lineageEntry))
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
        defaultParams,
        None, // sourceId to be added later
        None  // chunk
      )
    )

    try {
      val (result, implName, params): (R, Option[String], Option[Map[String, String]]) =
        if (timeout.isFinite()) {
          // -------------------------------------------------------------
          // Hard timeout – run `thunk` on its own thread and cancel the
          // Future if we exceed the limit. This sends Thread.interrupt to
          // the running code which is honored by most libraries and avoids
          // the soft-cancellation delay we observed with the ZIO fiber
          // approach.
          // -------------------------------------------------------------

          import java.util.concurrent.{Executors, TimeUnit, TimeoutException => JTimeout}

          val exec = Executors.newSingleThreadExecutor()
          try {
            val fut = exec.submit(() => thunk)
            try fut.get(timeout.toMillis, TimeUnit.MILLISECONDS)
            catch {
              case _: JTimeout =>
                fut.cancel(true) // interrupt running thread
                throw new RuntimeException("timeout")
            }
          } finally {
            exec.shutdownNow()
          }
        } else thunk

      val end = Instant.now().toEpochMilli
      val finalImplName = implName.orElse(defaultImplementation)
      val finalParams = params.orElse(defaultParams)

      StepResult(
        Some(result),
        Lineage(start, end, end - start, None, name, finalImplName, finalParams, None, None)
      )
    } catch {
      case t: Throwable => fail(t.getMessage)
    }
  }
}
