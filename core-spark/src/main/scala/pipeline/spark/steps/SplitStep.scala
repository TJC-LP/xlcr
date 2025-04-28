package com.tjclp.xlcr
package pipeline.spark.steps

import models.FileContent
import pipeline.spark.{CoreSchema, SparkPipelineRegistry, SparkStep, UdfHelpers, AsposeBroadcastManager}
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

  private def createSplitUdf(implicit spark: SparkSession) = {
    import spark.implicits._
    import UdfHelpers._

    // Ensure Aspose licenses are broadcast & available on the workers (if
    // enabled) – mirrors behaviour of `licenseAwareUdf2`.
    AsposeBroadcastManager.initBroadcast(spark)

    val udfF = (bytes: Array[Byte], mimeStr: String, sourceId: String) => {
      // One-time per-executor license initialisation.
      AsposeBroadcastManager.ensureInitialized()

      val mime =
        MimeType.fromStringNoParams(mimeStr).getOrElse(MimeType.ApplicationOctet)
      val content = FileContent(bytes, mime)

      val start = java.time.Instant.now().toEpochMilli

      try {
        val chunks = DocumentSplitter.split(content, config)

        val end = java.time.Instant.now().toEpochMilli

        // Determine splitter implementation & strategy used (for lineage meta)
        val splitterImpl = DocumentSplitter
          .forMime(mime)
          .map(_.getClass.getSimpleName)

        val effectiveStrategy = config.strategy match {
          case Some(SplitStrategy.Auto) =>
            Some(SplitConfig.defaultStrategyForMime(mime).displayName)
          case Some(strategy) => Some(strategy.displayName)
          case None => Some(SplitConfig.defaultStrategyForMime(mime).displayName)
        }

        val paramsMap = scala.collection.mutable.Map[String, String]()
        effectiveStrategy.foreach(s => paramsMap.put("strategy", s))

        val params = if (paramsMap.isEmpty) None else Some(paramsMap.toMap)

        // Convert chunks into a serialisable representation expected further
        // down-stream.  We include index / label / total so that the caller can
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

        val lineageNoChunk = UdfHelpers.Lineage(
          start,
          end,
          end - start,
          None,
          name,
          splitterImpl,
          params,
          None, // sourceId – added later
          None // chunk meta populated later per-row after explode
        )

        StepResult(Some(chunkTuples), lineageNoChunk)
      } catch {
        case t: Throwable =>
          val end = java.time.Instant.now().toEpochMilli
          val lineageErr = UdfHelpers.Lineage(
            start,
            end,
            end - start,
            Some(t.getMessage),
            name,
            None,
            None,
            None, // sourceId
            None
          )
          StepResult(Some(Seq.empty[(Array[Byte], String, Int, String, Int)]), lineageErr)
      }
    }

    // 3-argument UDF: (content bytes, mime string, source id)
    org.apache.spark.sql.functions.udf(udfF)
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
      df.withColumn(Result, splitUdf(F.col(Content), F.col(Mime), F.col(Id)))

    // Unpack result and extract the chunks array
    val withChunks = withResult
      .withColumn(Chunks, F.col(ResultData))

    /* ------------------------------------------------------------------ */
    /* Explode chunks while preserving *all* pass-through columns          */
    /* ------------------------------------------------------------------ */

    // Explode the chunks array
    val exploded = withChunks.withColumn(Chunk, F.explode_outer(F.col(Chunks)))

    // Replace content & mime columns with the chunk's data
    val withChunkData = exploded
      .withColumn(Content, F.col(s"$Chunk._1"))
      .withColumn(Mime, F.col(s"$Chunk._2"))

    /* ------------------------------------------------------------------ */
    /* Build lineage entry with per-chunk metadata                         */
    /* ------------------------------------------------------------------ */

    import UdfHelpers._
    import org.apache.spark.sql.Row

    // Helper UDF to enrich the lineage element with chunk context
    val enrichLineageUdf = org.apache.spark.sql.functions.udf(
      (lineageRow: Row, sourceId: String, idx: Long, tot: Long, lbl: String) => {
        if (lineageRow == null) {
          null
        } else {
          // Reconstruct Lineage from Row – field order must match definition
          val l = UdfHelpers.Lineage(
            lineageRow.getAs[Long]("startTimeMs"),
            lineageRow.getAs[Long]("endTimeMs"),
            lineageRow.getAs[Long]("durationMs"),
            Option(lineageRow.getAs[String]("error")),
            lineageRow.getAs[String]("name"),
            Option(lineageRow.getAs[String]("implementation")),
            Option(lineageRow.getAs[Map[String, String]]("params")),
            Some(sourceId),
            Some(UdfHelpers.ChunkMeta(sourceId, Some(idx), Some(tot), Some(lbl)))
          )
          l
        }
      }
    )

    val withLineageEntry = withChunkData.withColumn(
      LineageEntry,
      enrichLineageUdf(
        F.col(ResultLineage),
        F.col(Id),
        F.col(s"$Chunk._3").cast("long"),
        F.col(s"$Chunk._5").cast("long"),
        F.col(s"$Chunk._4")
      )
    )

    // Append lineage entry
    val withLineage = UdfHelpers.appendLineageEntry(withLineageEntry, F.col(LineageEntry))

    // Final cleanup – drop temporary columns (+ legacy chunk columns)
    withLineage
      .drop(Chunks, Chunk, LineageEntry, ResultLineage, Result)
  }
}
