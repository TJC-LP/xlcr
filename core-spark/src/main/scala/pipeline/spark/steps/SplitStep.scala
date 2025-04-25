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

  override val name: String = s"split${config.strategy.getOrElse(SplitStrategy.Auto).displayName.capitalize}"

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
        MimeType.fromStringNoParams(mimeStr).getOrElse(MimeType.ApplicationOctet)
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

    // Append the lineage entry to the lineage column
    val withLineage = UdfHelpers.appendLineageEntry(
      withResult,
      F.col(ResultLineage)
    )

    // Unpack result using common helper and extract chunks array
    val withChunks = withLineage
      .withColumn(Chunks, F.col(ResultData))
      .drop(Result, LineageEntry)

    /* ------------------------------------------------------------------ */
    /* Explode chunks while preserving *all* pass-through columns          */
    /* ------------------------------------------------------------------ */

    // Explode the chunks array
    val exploded = withChunks.withColumn(Chunk, F.explode_outer(F.col(Chunks)))

    // Update columns with chunk data
    val result = exploded
      // Replace content and mime columns with the chunk's content and mime
      .withColumn(Content, F.col(s"$Chunk._1"))
      .withColumn(Mime, F.col(s"$Chunk._2"))
      // Set chunk index from the chunk data
      .withColumn(ChunkIndex, F.col(s"$Chunk._3").cast("long"))
      // Set chunk label from the chunk data
      .withColumn(ChunkLabel, F.col(s"$Chunk._4"))
      // Set chunk total from the chunk data
      .withColumn(ChunkTotal, F.col(s"$Chunk._5").cast("long"))
      // Drop temporary columns
      .drop(Chunks, Chunk)

    result
  }
}
