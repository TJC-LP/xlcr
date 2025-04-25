package com.tjclp.xlcr
package pipeline.spark.steps

import models.FileContent
import pipeline.spark.{SparkPipelineRegistry, SparkStep, UdfHelpers}
import types.MimeType
import utils.{DocumentSplitter, SplitConfig, SplitStrategy}
import pipeline.spark.CoreSchema

import org.apache.spark.sql.types.{BinaryType, StringType, StructType}
import org.apache.spark.sql.{DataFrame, Row, SparkSession, functions => F}

import scala.concurrent.duration.{Duration => ScalaDuration}

/** Document splitting step with comprehensive metrics and error handling.
  * Splits documents into chunks according to the specified strategy.
  */
case class SplitStep(
    strategy: Option[SplitStrategy] = None,
    recursive: Boolean = false,
    maxRecursionDepth: Int = 3,
    rowTimeout: ScalaDuration =
      scala.concurrent.duration.Duration(60, "seconds"),
    // When provided, this full SplitConfig overrides the ad-hoc params above.
    cfg: Option[SplitConfig] = None
) extends SparkStep {

  override val name: String =
    s"split${strategy.map(_.toString.capitalize).getOrElse("")}"

  override val meta: Map[String, String] = Map(
    "strategy" -> strategy.map(_.toString).getOrElse("auto"),
    "recursive" -> recursive.toString,
    "maxDepth" -> maxRecursionDepth.toString
  )

  // Schema for split results
  private val chunkSchema = new StructType()
    .add("index", org.apache.spark.sql.types.IntegerType)
    .add("label", StringType)
    .add("content", BinaryType)
    .add("mime", StringType)

  import UdfHelpers._

  // UDF that splits a document using the DocumentSplitter
  private val splitUdf = wrapUdf2(rowTimeout) {
    (bytes: Array[Byte], mimeStr: String) =>
      val mime =
        MimeType.fromString(mimeStr).getOrElse(MimeType.ApplicationOctet)
      val content = FileContent(bytes, mime)

      // Either use the explicit config provided by caller or build one from
      // the lightweight parameters still accepted for backward compatibility.
      val config = cfg.getOrElse(
        strategy
          .map(s => SplitConfig(s, recursive = recursive, maxRecursionDepth = maxRecursionDepth))
          .getOrElse(SplitConfig.autoForMime(mime, recursive, maxRecursionDepth))
      )

      val chunks = DocumentSplitter.split(content, config)
      chunks.map {
        chunk => (
          chunk.content.data,
          chunk.content.mimeType.mimeType,
          chunk.index,
          chunk.label,
          chunk.total
        )
      }
  }

  override def doTransform(
      df: DataFrame
  )(implicit spark: SparkSession): DataFrame = {

    // Apply splitting and capture results
    val withResult =
      df.withColumn("result", splitUdf(F.col("content"), F.col("mime")))

    // Unpack result using common helper and extract chunks array
    val withChunks = UdfHelpers
      .unpackResult(withResult, dataCol = "chunks")

    /* ------------------------------------------------------------------ */
    /* Explode chunks while preserving *all* pass-through columns          */
    /* ------------------------------------------------------------------ */

    // Keep every column that isn't the temporary `chunks` array
    val passthroughCols = withChunks.columns.filterNot(_ == "chunks").map(F.col)

    val exploded = withChunks.withColumn("chunk", F.explode_outer(F.col("chunks")))

    // Build final dataframe: all passthrough columns + expanded chunk fields
    val chunkColumns: Seq[org.apache.spark.sql.Column] = Seq(
      F.col("chunk._1").as("content"),
      F.col("chunk._2").as("mime"),
      F.col("chunk._3").cast("long").as("chunk_index"),
      F.col("chunk._4").as("chunk_label"),
      F.col("chunk._5").cast("long").as("chunk_total"),
      // chunk_id = id::chunk:<index>
      F.concat_ws("", F.col(CoreSchema.Id), F.lit("::chunk:"), F.col("chunk._3")).as("chunk_id")
    )

    val chunksDF = exploded.select((passthroughCols ++ chunkColumns): _*)

    // Add a flag to show if the split produced chunks
    // This helps identify documents that couldn't be split
    val flaggedDF = chunksDF.withColumn(
      "split_success",
      F.when(F.col("chunk_index").isNotNull, F.lit(true))
        .otherwise(F.lit(false))
    )

    flaggedDF
  }

}

/* --------------------------------------------------------------------- */
/* Companion object                                                      */
/* --------------------------------------------------------------------- */

object SplitStep {
  /** Build a SplitStep directly from a full SplitConfig. */
  def fromConfig(
      config: SplitConfig,
      rowTimeout: ScalaDuration = scala.concurrent.duration.Duration(60, "seconds")
  ): SplitStep =
    SplitStep(
      strategy = Some(config.strategy),
      recursive = config.recursive,
      maxRecursionDepth = config.maxRecursionDepth,
      rowTimeout = rowTimeout,
      cfg = Some(config)
    )


}

// Convenience singletons for common split strategies
object SplitByPage extends SplitStep(Some(SplitStrategy.Page)) {
  SparkPipelineRegistry.register(this)
}

object SplitBySheet extends SplitStep(Some(SplitStrategy.Sheet)) {
  SparkPipelineRegistry.register(this)
}

object SplitBySlide extends SplitStep(Some(SplitStrategy.Slide)) {
  SparkPipelineRegistry.register(this)
}

object SplitRecursive extends SplitStep(recursive = true) {
  SparkPipelineRegistry.register(this)
}
