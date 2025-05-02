package com.tjclp.xlcr
package pipeline.spark

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}
import org.slf4j.LoggerFactory
import zio.{Runtime, ZIO, Duration => ZDuration}

import java.util.concurrent.TimeoutException
import scala.concurrent.duration.{Duration => ScalaDuration}

/** Unified Spark pipeline step with ZIO-powered execution and license-aware UDFs.
  *
  *  – Concrete steps implement `doTransform`.
  *  – Provides composition (`andThen`, `fanOut`) and timeout helpers.
  *  – Automatically appends lineage tracking for provenance.
  *  – UDF-based operations provide detailed timing metrics.
  *  – License-aware UDFs ensure Aspose licenses are properly initialized across workers.
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

  final def transform(
      df: DataFrame
  )(implicit spark: SparkSession): DataFrame = {
    // Ensure that input DataFrame has the required core schema
    val ensuredDf = CoreSchema.ensure(df)

    // Run the step transformation with lineage tracking directly
    doTransform(ensuredDf).withColumn(
      CoreSchema.Id,
      F.md5(F.col(CoreSchema.Content))
    )
  }

  final def apply(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
    val result = transform(df)
    // Enforce core contract – throws if violated
    CoreSchema.requireCore(result, stepName = name)
    result
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
      val intermediateResult = self.doTransform(df)
      next.doTransform(intermediateResult)
    }
  }

  final def fanOut(left: SparkStep, right: SparkStep): SparkStep =
    new SparkStep {
      val name = s"fanOut(${left.name},${right.name})"
      protected def doTransform(
          df: DataFrame
      )(implicit spark: SparkSession): DataFrame = {
        val base = self.transform(df)
        val l = left.transform(base).withColumn("branch", F.lit("left"))
        val r = right.transform(base).withColumn("branch", F.lit("right"))
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
        .attempt(self.transform(df))
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
}
