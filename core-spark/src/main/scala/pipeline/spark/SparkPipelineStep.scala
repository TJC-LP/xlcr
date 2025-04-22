package com.tjclp.xlcr
package pipeline.spark

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeoutException

/**
 * Serialisable, lineage‑aware Spark pipeline step.
 */
trait SparkPipelineStep extends Serializable { self =>

  def name: String
  def meta: Map[String, String] = Map.empty

  def transform(df: DataFrame)(implicit spark: SparkSession): DataFrame

  final def apply(df: DataFrame)(implicit spark: SparkSession): DataFrame =
    appendLineage(transform(df))

  final def andThen(next: SparkPipelineStep): SparkPipelineStep = new SparkPipelineStep {
    val name = s"${self.name}>>>${next.name}"
    def transform(df: DataFrame)(implicit spark: SparkSession): DataFrame =
      next.transform(self.transform(df))
  }

  final def withTimeout(timeout: Duration): SparkPipelineStep = new SparkPipelineStep {
    val name = s"${self.name}.timeout(${timeout.toMillis}ms)"
    override val meta: Map[String, String] = self.meta + ("timeout" -> timeout.toString())

    def transform(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
      implicit val ec: ExecutionContext = ExecutionContext.global
      val fut = Future(self.transform(df))
      try Await.result(fut, timeout)
      catch {
        case _: TimeoutException =>
          self.apply(df).withColumn("error", F.lit(s"timeout $timeout"))
      }
    }
  }

  final def fanOut(left: SparkPipelineStep, right: SparkPipelineStep): SparkPipelineStep = new SparkPipelineStep {
    val name = s"fanOut(${left.name},${right.name})"
    def transform(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
      val base = self.transform(df)
      val l = left.transform(base).withColumn("branch", F.lit("left"))
      val r = right.transform(base).withColumn("branch", F.lit("right"))
      l.unionByName(r, allowMissingColumns = true)
    }
  }

  private def appendLineage(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
    val colName = "lineage"
    val init = if (df.columns.contains(colName)) df else df.withColumn(colName, F.array())
    val entry = if (meta.isEmpty) name else s"$name(${meta.map{case(k,v)=>s"$k=$v"}.mkString(";")})"
    init.withColumn(colName, F.array_union(F.col(colName), F.array(F.lit(entry))))
  }
}
