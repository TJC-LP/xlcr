package com.tjclp.xlcr
package pipeline.spark

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}
import org.slf4j.LoggerFactory
import zio.{Duration => ZDuration, Runtime, ZIO}

import java.time.Instant
import java.util.concurrent.TimeoutException
import scala.concurrent.duration.{Duration => ScalaDuration}

/**
 * Unified Spark pipeline step with ZIO-powered execution.
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

  protected def doTransform(df: DataFrame)(implicit spark: SparkSession): DataFrame

  /* --------------------------------------------------------------------- */
  /* Public façade                                                         */
  /* --------------------------------------------------------------------- */

  private val logger = LoggerFactory.getLogger(getClass)

  final def transform(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
    try {
      doTransform(df)
    } catch {
      case e: Exception =>
        logger.error(s"Step $name failed: ${e.getMessage}", e)
        df.withColumn("error", F.lit(e.getMessage))
    }
  }

  final def apply(df: DataFrame)(implicit spark: SparkSession): DataFrame =
    appendLineage(transform(df))

  /* --------------------------------------------------------------------- */
  /* Composition helpers                                                   */
  /* --------------------------------------------------------------------- */

  final def andThen(next: SparkStep): SparkStep = new SparkStep {
    val name = s"${self.name}>>>${next.name}"
    override def meta: Map[String, String] = self.meta ++ next.meta
    protected def doTransform(df: DataFrame)(implicit s: SparkSession): DataFrame =
      next.doTransform(self.doTransform(df))
  }

  final def fanOut(left: SparkStep, right: SparkStep): SparkStep = new SparkStep {
    val name = s"fanOut(${left.name},${right.name})"
    protected def doTransform(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
      val base = self.doTransform(df)
      val l    = left.doTransform(base).withColumn("branch", F.lit("left"))
      val r    = right.doTransform(base).withColumn("branch", F.lit("right"))
      l.unionByName(r, allowMissingColumns = true)
    }
  }

  /* --------------------------------------------------------------------- */
  /* Timeout helper (ZIO)                                                  */
  /* --------------------------------------------------------------------- */

  final def withTimeout(timeout: ScalaDuration): SparkStep = new SparkStep {
    val name = s"${self.name}.timeout(${timeout.toMillis}ms)"
    override val meta: Map[String, String] = self.meta + ("timeout" -> timeout.toString())

    protected def doTransform(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
      val task = ZIO.attempt(self.transform(df))
        .timeoutFail(new TimeoutException("timeout"))(ZDuration.fromScala(timeout))

      import zio.Unsafe
      Unsafe.unsafe { implicit u =>
        Runtime.default.unsafe.run(task).getOrThrowFiberFailure()
      }
    }
  }

  /* --------------------------------------------------------------------- */
  /* Helpers                                                               */
  /* --------------------------------------------------------------------- */

  private def appendLineage(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
    val col = "lineage"
    val init = if (df.columns.contains(col)) df else df.withColumn(col, F.array())
    val entry = if (meta.isEmpty) name else s"$name(${meta.map{case(k,v)=>s"$k=$v"}.mkString(";")})"
    init.withColumn(col, F.array_union(F.col(col), F.array(F.lit(entry))))
  }

}
