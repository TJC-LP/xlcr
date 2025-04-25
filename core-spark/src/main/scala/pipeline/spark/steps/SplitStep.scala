package com.tjclp.xlcr
package pipeline.spark.steps

import models.FileContent
import pipeline.spark.{CoreSchema, SparkPipelineRegistry, SparkStep, UdfHelpers}
import types.MimeType
import utils.{DocumentSplitter, SplitConfig, SplitPolicy, SplitStrategy}

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}

import scala.concurrent.duration.{Duration => ScalaDuration}

/** Document splitting step with comprehensive metrics and error handling.
  * Splits documents into chunks according to the specified strategy.
  */
case class SplitStep(
    rowTimeout: ScalaDuration =
      scala.concurrent.duration.Duration(60, "seconds"),
    // When provided this exact config wins.
    config: SplitConfig = SplitConfig(strategy = Some(SplitStrategy.Auto))
) extends SparkStep {

  override val name: String = "split"

  override val meta: Map[String, String] = Map(
    "strategy" -> config.strategy.map(_.toString).getOrElse("auto"),
    "recursive" -> config.recursive.toString,
    "maxDepth" -> config.maxRecursionDepth.toString
  )

  import UdfHelpers._

  // UDF that splits a document using the DocumentSplitter
  private val splitUdf = wrapUdf2(name, rowTimeout) {
    (bytes: Array[Byte], mimeStr: String) =>
      val mime =
        MimeType.fromString(mimeStr).getOrElse(MimeType.ApplicationOctet)
      val content = FileContent(bytes, mime)

      val chunks = DocumentSplitter.split(content, config)
      chunks.map { chunk =>
        (
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
    import CoreSchema._
    // Apply splitting and capture results
    val withResult =
      df.withColumn(Result, splitUdf(F.col(Content), F.col(Mime)))

    // Unpack result using common helper and extract chunks array
    val withChunks = withResult
      .withColumn(Chunks, F.col(ResultData))
      .withColumn(LineageEntry, F.col(ResultLineage))
      .drop(Result)

    /* ------------------------------------------------------------------ */
    /* Explode chunks while preserving *all* pass-through columns          */
    /* ------------------------------------------------------------------ */

    // Keep every column that isn't the temporary `chunks` array
    val passthroughCols = withChunks.columns.filterNot(_ == Chunks).map(F.col)

    val exploded =
      withChunks.withColumn(Chunk, F.explode_outer(F.col(Chunks)))

    // Build final dataframe: all passthrough columns + expanded chunk fields
    val chunkColumns: Seq[org.apache.spark.sql.Column] = Seq(
      // Always replace content and mime columns with the chunk's content and mime
      F.col(s"$Chunk._1").as(Content),
      F.col(s"$Chunk._2").as(Mime),
      
      // Only set chunk columns if they're not already set or if we're exploding multiple chunks
      // This prevents overwriting existing chunk information when nested splitting occurs
      F.when(
        F.col(CoreSchema.ChunkIndex).isNull || F.size(F.col(Chunks)) > 1,
        F.col(s"$Chunk._3").cast("long")
      ).otherwise(F.col(CoreSchema.ChunkIndex)).as(CoreSchema.ChunkIndex),
      
      F.when(
        F.col(CoreSchema.ChunkLabel).isNull || F.size(F.col(Chunks)) > 1, 
        F.col(s"$Chunk._4")
      ).otherwise(F.col(CoreSchema.ChunkLabel)).as(CoreSchema.ChunkLabel),
      
      F.when(
        F.col(CoreSchema.ChunkTotal).isNull || F.size(F.col(Chunks)) > 1,
        F.col(s"$Chunk._5").cast("long")
      ).otherwise(F.col(CoreSchema.ChunkTotal)).as(CoreSchema.ChunkTotal),
      
      // Only create a new chunk ID if it doesn't exist already or if we're exploding multiple chunks
      F.when(
        F.col(CoreSchema.ChunkId).isNull || F.size(F.col(Chunks)) > 1,
        F.concat_ws(
          "",
          F.col(CoreSchema.Id),
          F.lit(s"::$Chunk:"),
          F.col(s"$Chunk._3")
        )
      ).otherwise(F.col(CoreSchema.ChunkId)).as(CoreSchema.ChunkId)
    )

    exploded.select(passthroughCols ++ chunkColumns: _*)
  }
}
