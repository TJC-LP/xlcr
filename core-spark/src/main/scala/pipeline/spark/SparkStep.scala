package com.tjclp.xlcr
package pipeline.spark

import java.util.concurrent.TimeoutException

import scala.concurrent.duration.{ Duration => ScalaDuration }

import org.apache.spark.sql.{ functions => F, DataFrame, SparkSession }
import zio.{ Duration => ZDuration, Runtime, ZIO }

import pipeline.spark.steps._

/**
 * Unified Spark pipeline step with ZIO-powered execution and license-aware UDFs.
 *
 * – Concrete steps implement `doTransform`. – Provides composition (`andThen`, `fanOut`) and
 * timeout helpers. – Automatically appends lineage tracking for provenance. – UDF-based operations
 * provide detailed timing metrics. – License-aware UDFs ensure Aspose licenses are properly
 * initialized across workers.
 */
@deprecated("core-spark will be removed in 0.3.0", "0.2.1")
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
  /* Optional configuration for UDF-based steps                            */
  /* --------------------------------------------------------------------- */

  /**
   * Timeout duration for UDF operations. This timeout applies per row/file, not to the entire
   * DataFrame operation. Steps that use UDFs should pass this value to wrapUdf/wrapUdf2. Default is
   * 30 seconds.
   *
   * When a timeout occurs, the error will be captured in the lineage with message "timeout",
   * allowing for easy filtering of timed-out rows.
   */
  def udfTimeout: ScalaDuration = ScalaDuration(30, "seconds")

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
    val name                               = s"${self.name}>>>${next.name}"
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
        val l    = left.transform(base).withColumn("branch", F.lit("left"))
        val r    = right.transform(base).withColumn("branch", F.lit("right"))
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

@deprecated("core-spark will be removed in 0.3.0", "0.2.1")
object SparkStep {

  /**
   * Creates a new SparkStep that wraps an existing step with a custom UDF timeout. This is useful
   * for overriding the default UDF timeout without modifying the step itself.
   *
   * Note: This is different from the instance method `withTimeout` which sets a timeout for the
   * entire step transformation. This method specifically configures the timeout for individual UDF
   * operations within the step.
   *
   * Example:
   * ```scala
   * val slowStep = SparkStep.withUdfTimeout(DetectMime.default, Duration(120, "seconds"))
   * ```
   */
  def withUdfTimeout(step: SparkStep, timeout: ScalaDuration): SparkStep =
    step match {
      // For case classes that have a copy method with udfTimeout parameter
      case s: SplitStep   => s.copy(udfTimeout = timeout)
      case s: DetectMime  => s.copy(udfTimeout = timeout)
      case s: ConvertStep => s.copy(udfTimeout = timeout)
      case s: ExtractStep => s.copy(udfTimeout = timeout)
      // For other steps, create a wrapper (though this won't work properly if they create UDFs in constructors)
      case _ => new SparkStep {
          override val name: String              = step.name
          override val meta: Map[String, String] = step.meta + ("udfTimeout" -> timeout.toString)
          override val udfTimeout: ScalaDuration = timeout

          protected def doTransform(df: DataFrame)(implicit spark: SparkSession): DataFrame =
            step.doTransform(df)
        }
    }
}
