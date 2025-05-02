package com.tjclp.xlcr
package pipeline.spark
package steps

import models.FileContent
import splitters._
import types.MimeType

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}

import scala.concurrent.duration.{Duration => ScalaDuration}

/** Document splitting step with comprehensive metrics and error handling.
  * Splits documents into chunks according to the specified strategy.
  */
case class SplitStep(
    rowTimeout: ScalaDuration =
      scala.concurrent.duration.Duration(60, "seconds"),
    // When provided this exact config wins.
    config: SplitConfig = SplitConfig(strategy = Some(SplitStrategy.Auto)),
    // 1.8 GiB gives ~15 % head-room for struct/array metadata
    maxBytesPerRow: Long = 1800L * 1024 * 1024,
    // Optional: cap number of chunks so we don’t hit JVM array limits
    maxChunksPerRow: Int = 100000
) extends SparkStep {

  override val name: String =
    s"split${config.strategy.getOrElse(SplitStrategy.Auto).displayName.capitalize}"

  override val meta: Map[String, String] = super.meta ++ Map(
    "strategy" -> config.strategy.map(_.displayName).getOrElse("auto"),
    "recursive" -> config.recursive.toString,
    "maxDepth" -> config.maxRecursionDepth.toString
  )

  // ------------------------------------------------------------------
  // Custom UDF that returns a StepResult *and* embeds per-chunk metadata into
  // the lineage element.  We implement the wrapping logic manually instead of
  // re-using `wrapUdf2` because we need access to the per-chunk context which
  // only becomes available **after** the split operation.
  // ------------------------------------------------------------------

  private def createSplitUdf = {
    // We only need the content bytes & mime string here – the `sourceId` is
    // added to the lineage *after* exploding the chunks, so we can simply use
    // the two-argument wrapper.

    UdfHelpers.wrapUdf2(name, rowTimeout) {
      (bytes: Array[Byte], mimeStr: String) =>
        val mime =
          MimeType
            .fromStringNoParams(mimeStr)
            .getOrElse(MimeType.ApplicationOctet)
        val content = FileContent(bytes, mime)

        // Perform the split – any exception will be captured by the wrapper and
        // converted into a proper `StepResult` with the error field populated.
        val chunks = DocumentSplitter.split(content, config)

        /* ---------- NEW GUARD ------------------------------------------- */
        val totalBytes = chunks.iterator.map(_.content.data.length.toLong).sum
        if (totalBytes > maxBytesPerRow || chunks.size > maxChunksPerRow)
          throw new IllegalArgumentException(
            s"Split output too large for one Spark row " +
              s"(bytes=$totalBytes, chunks=${chunks.size}). " +
              s"Max allowed: ${maxBytesPerRow} bytes / " +
              s"${maxChunksPerRow} chunks."
          )
        /* ---------------------------------------------------------------- */

        // Determine splitter implementation & strategy used (for lineage meta)
        val splitterImpl = DocumentSplitter
          .forMime(mime)
          .map(_.getClass.getSimpleName)

        val effectiveStrategy = config.strategy match {
          case Some(SplitStrategy.Auto) =>
            Some(SplitConfig.defaultStrategyForMime(mime).displayName)
          case Some(strategy) => Some(strategy.displayName)
          case None =>
            Some(SplitConfig.defaultStrategyForMime(mime).displayName)
        }

        val paramsMap = scala.collection.mutable.Map[String, String]()
        effectiveStrategy.foreach(s => paramsMap.put("strategy", s))

        val params = if (paramsMap.isEmpty) None else Some(paramsMap.toMap)

        // Convert chunks into a serialisable representation expected further
        // downstream. We include index / label / total so that the caller can
        // explode them and attach the information to the lineage element of
        // each resulting row.
        val chunkTuples = chunks.map { chunk =>
          (
            chunk.content.data,
            chunk.content.mimeType.mimeType,
            chunk.index,
            chunk.label,
            chunk.total
          )
        }

        (chunkTuples, splitterImpl, params)
    }
  }

  override def doTransform(
      df: DataFrame
  )(implicit spark: SparkSession): DataFrame = {
    import CoreSchema._

    // ------------------------------------------------------------------
    // 1. Run the splitter – The UDF returns a StepResult containing the list
    //    of chunks.  We *don't* append the lineage here because we first need
    //    to explode the chunks so we can attach the per-chunk metadata.
    // ------------------------------------------------------------------

    val splitUdf = createSplitUdf

    val withResult =
      df.withColumn(Result, splitUdf(F.col(Content), F.col(Mime)))

    // Unpack result and extract the chunks array
    val withChunks = withResult
      .withColumn(Chunks, F.col(ResultData))

    /* ------------------------------------------------------------------ */
    /* Explode chunks while preserving *all* pass-through columns          */
    /* ------------------------------------------------------------------ */

    // Explode the chunks array
    val exploded = withChunks.withColumn(Chunk, F.explode_outer(F.col(Chunks)))

    // Replace content & mime columns with the chunk's data if split succeeded.
    val withChunkData = exploded
      .withColumn(
        Content,
        F.when(F.col(Chunk).isNull, F.col(Content))
          .otherwise(F.col(s"$Chunk._1"))
      )
      .withColumn(
        Mime,
        F.when(F.col(Chunk).isNull, F.col(Mime))
          .otherwise(F.col(s"$Chunk._2"))
      )

    /* ------------------------------------------------------------------ */
    /* Build lineage entry with per-chunk metadata                         */
    /* ------------------------------------------------------------------ */
    // Construct nested chunk struct using Spark primitives
    val rawChunkStruct = F.struct(
      F.col(s"$Chunk._3").cast("long").as("chunkIndex"),
      F.col(s"$Chunk._5").cast("long").as("chunkTotal"),
      F.col(s"$Chunk._4").as("chunkLabel")
    )

    val chunkNull = F
      .lit(null)
      .cast("struct<chunkIndex:bigint,chunkTotal:bigint,chunkLabel:string>")
    val chunkStruct =
      F.when(F.col(Chunk).isNull, chunkNull).otherwise(rawChunkStruct)

    val lineageEntryCol = F
      .col(ResultLineage)
      .withField("sourceId", F.col(Id))
      .withField("chunk", chunkStruct)

    val withLineageEntry =
      withChunkData.withColumn(LineageEntry, lineageEntryCol)

    // Append lineage entry
    val withLineage =
      UdfHelpers.appendLineageEntry(withLineageEntry, F.col(LineageEntry))

    // Final cleanup – drop temporary columns (+ legacy chunk columns)
    withLineage
      .drop(Chunks, Chunk, LineageEntry, ResultLineage, Result)
  }
}
