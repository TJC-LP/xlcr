package com.tjclp.xlcr
package pipeline.spark

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}
import org.slf4j.LoggerFactory
import zio.{Runtime, ZIO, Duration => ZDuration}

import java.util.concurrent.TimeoutException
import scala.concurrent.duration.{Duration => ScalaDuration}

/** Unified Spark pipeline step with ZIO-powered execution.
  *
  *  – Concrete steps implement `doTransform`.
  *  – Provides composition (`andThen`, `fanOut`) and timeout helpers.
  *  – Automatically appends lineage tracking for provenance.
  *  – UDF-based operations provide detailed timing metrics.
  */
trait SparkStep extends Serializable { self =>

  /* --------------------------------------------------------------------- */
  /* Required by concrete steps                                            */
  /* --------------------------------------------------------------------- */

  def name: String
  def meta: Map[String, String] = Map.empty

  protected def doTransform(df: DataFrame)(implicit
      spark: SparkSession
  ): DataFrame

  /* --------------------------------------------------------------------- */
  /* Public façade                                                         */
  /* --------------------------------------------------------------------- */

  private val logger = LoggerFactory.getLogger(getClass)

  final def transform(
      df: DataFrame
  )(implicit spark: SparkSession): DataFrame = {
    try {
      // Ensure that input DataFrame has the required core schema
      CoreSchema.ensure(df)
      
      // Run the step transformation
      val transformedDf = doTransform(df)
      
      // Ensure the transformed DataFrame maintains the core schema
      val ensuredDf = CoreSchema.ensure(transformedDf)
      
      // Return the validated DataFrame
      ensuredDf
    } catch {
      case e: Exception =>
        logger.error(s"Step $name failed: ${e.getMessage}", e)
        df.withColumn("error", F.lit(e.getMessage))
    }
  }

  final def apply(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
    val out = appendLineage(transform(df))
    // Enforce core contract – throws if violated
    CoreSchema.requireCore(out, stepName = name)
    out
  }

  /* --------------------------------------------------------------------- */
  /* Composition helpers                                                   */
  /* --------------------------------------------------------------------- */

  final def andThen(next: SparkStep): SparkStep = new SparkStep {
    val name = s"${self.name}>>>${next.name}"
    override def meta: Map[String, String] = self.meta ++ next.meta
    protected def doTransform(df: DataFrame)(implicit
        s: SparkSession
    ): DataFrame = {
      // Apply the first step through its full pipeline with lineage tracking
      val intermediateResult = self.apply(df)
      
      // Apply the second step, also with lineage tracking
      next.transform(intermediateResult)
    }
  }

  final def fanOut(left: SparkStep, right: SparkStep): SparkStep =
    new SparkStep {
      val name = s"fanOut(${left.name},${right.name})"
      protected def doTransform(
          df: DataFrame
      )(implicit spark: SparkSession): DataFrame = {
        // Apply the base step with full lineage tracking
        val base = self.apply(df)
        
        // Apply both branches with full lineage tracking
        val l = left.apply(base).withColumn("branch", F.lit("left"))
        val r = right.apply(base).withColumn("branch", F.lit("right"))
        
        // Union the results
        l.unionByName(r, allowMissingColumns = true)
      }
    }

  /* --------------------------------------------------------------------- */
  /* Timeout helper (ZIO)                                                  */
  /* --------------------------------------------------------------------- */

  final def withTimeout(timeout: ScalaDuration): SparkStep = new SparkStep {
    val name = s"${self.name}.timeout(${timeout.toMillis}ms)"
    override val meta: Map[String, String] =
      self.meta + ("timeout" -> timeout.toString)

    protected def doTransform(
        df: DataFrame
    )(implicit spark: SparkSession): DataFrame = {
      val task = ZIO
        .attempt(self.apply(df))  // Use apply to ensure lineage is properly tracked
        .timeoutFail(new TimeoutException("timeout"))(
          ZDuration.fromScala(timeout)
        )

      import zio.Unsafe
      Unsafe.unsafe { implicit u =>
        Runtime.default.unsafe.run(task).getOrThrowFiberFailure()
      }
    }
  }

  /* --------------------------------------------------------------------- */
  /* Helpers                                                               */
  /* --------------------------------------------------------------------- */

  private def appendLineage(
      df: DataFrame
  ): DataFrame = {

    import CoreSchema.{Lineage, LineageEntry, LineageArrayType}

    // Ensure lineage array column exists
    val withArray =
      if (df.columns.contains(Lineage)) df
      else df.withColumn(Lineage, F.array().cast(LineageArrayType))

    // Append to lineage and materialise last_step struct for convenience
    withArray
      .withColumn(Lineage, F.array_union(F.col(Lineage), F.array(LineageEntry)))
      .drop(LineageEntry)
  }
}
