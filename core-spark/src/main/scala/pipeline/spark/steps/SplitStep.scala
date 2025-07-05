package com.tjclp.xlcr
package pipeline.spark
package steps

import scala.concurrent.duration.{ Duration => ScalaDuration }

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}

import models.FileContent
import splitters._
import types.MimeType

/**
 * Document splitting step with comprehensive metrics and error handling. Splits documents into
 * chunks according to the specified strategy.
 */
case class SplitStep(
  override val udfTimeout: ScalaDuration =
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
    "strategy"  -> config.strategy.map(_.displayName).getOrElse("auto"),
    "recursive" -> config.recursive.toString,
    "maxDepth"  -> config.maxRecursionDepth.toString
  )

  // ------------------------------------------------------------------
  // Custom UDF that returns a StepResult *and* embeds per-chunk metadata into
  // the lineage element.  We implement the wrapping logic manually instead of
  // re-using `wrapUdf2` because we need access to the per-chunk context which
  // only becomes available **after** the split operation.
  // ------------------------------------------------------------------

  private def createSplitUdf =
    // We only need the content bytes & mime string here – the `sourceId` is
    // added to the lineage *after* exploding the chunks, so we can simply use
    // the two-argument wrapper.

    UdfHelpers.wrapUdf2(name, udfTimeout) {
      (bytes: Array[Byte], mimeStr: String) =>
        val mime =
          MimeType
            .fromStringNoParams(mimeStr)
            .getOrElse(MimeType.ApplicationOctet)
        val content = FileContent(bytes, mime)

        // Initialise variables that we capture for lineage (determined before action)
        val splitterOpt      = DocumentSplitter.forMime(mime)
        val splitterImplName = splitterOpt.map(_.getClass.getSimpleName)

        val effectiveStrategy = config.strategy match {
          case Some(SplitStrategy.Auto) =>
            Some(SplitConfig.defaultStrategyForMime(mime).displayName)
          case Some(strategy) => Some(strategy.displayName)
          case None           => Some(SplitConfig.defaultStrategyForMime(mime).displayName)
        }

        val params: Option[Map[String, String]] = effectiveStrategy.map(s => Map("strategy" -> s))

        UdfHelpers.FoundImplementation[
          Seq[(Array[Byte], String, Int, String, Int)]
        ](
          implementationName = splitterImplName,
          params = params,
          action = () => {
            // Perform the split – exceptions propagate to wrapper
            val chunks = DocumentSplitter.split(content, config)

            // Guard against huge outputs
            val totalBytes = chunks.iterator.map(_.content.data.length.toLong).sum
            if (totalBytes > maxBytesPerRow || chunks.size > maxChunksPerRow)
              throw new IllegalArgumentException(
                s"Split output too large for one Spark row " +
                  s"(bytes=$totalBytes, chunks=${chunks.size}). " +
                  s"Max allowed: ${maxBytesPerRow} bytes / " +
                  s"${maxChunksPerRow} chunks."
              )

            // Transform chunks into serialisable tuples expected downstream
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
        )
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

object SplitStep {

  /**
   * Creates a SplitStep that limits splitting to the first N chunks. Works universally for pages
   * (PDF), sheets (Excel), slides (PowerPoint), etc.
   *
   * @param limit
   *   Maximum number of chunks to extract
   * @param strategy
   *   Optional split strategy (defaults to Auto)
   * @param udfTimeout
   *   Timeout for the splitting operation
   * @return
   *   Configured SplitStep instance
   */
  def withChunkLimit(
    limit: Int,
    strategy: Option[SplitStrategy] = None,
    udfTimeout: ScalaDuration = scala.concurrent.duration.Duration(60, "seconds")
  ): SplitStep =
    SplitStep(
      udfTimeout = udfTimeout,
      config = SplitConfig(
        strategy = strategy.orElse(Some(SplitStrategy.Auto)),
        chunkRange = Some(0 until limit)
      )
    )

  /**
   * Creates a SplitStep that extracts a specific range of chunks. Works universally for any
   * document type.
   *
   * @param start
   *   Start chunk index (0-based)
   * @param end
   *   End chunk index (exclusive, 0-based)
   * @param strategy
   *   Optional split strategy (defaults to Auto)
   * @param udfTimeout
   *   Timeout for the splitting operation
   * @return
   *   Configured SplitStep instance
   */
  def withChunkRange(
    start: Int,
    end: Int,
    strategy: Option[SplitStrategy] = None,
    udfTimeout: ScalaDuration = scala.concurrent.duration.Duration(60, "seconds")
  ): SplitStep =
    SplitStep(
      udfTimeout = udfTimeout,
      config = SplitConfig(
        strategy = strategy.orElse(Some(SplitStrategy.Auto)),
        chunkRange = Some(start until end)
      )
    )

  /**
   * Creates a SplitStep with auto strategy and optional chunk limit.
   *
   * @param chunkLimit
   *   Optional maximum number of chunks to extract
   * @param udfTimeout
   *   Timeout for the splitting operation
   * @return
   *   Configured SplitStep instance
   */
  def auto(
    chunkLimit: Option[Int] = None,
    udfTimeout: ScalaDuration = scala.concurrent.duration.Duration(60, "seconds")
  ): SplitStep =
    SplitStep(
      udfTimeout = udfTimeout,
      config = SplitConfig(
        strategy = Some(SplitStrategy.Auto),
        chunkRange = chunkLimit.map(limit => 0 until limit)
      )
    )

  // Backward compatibility - deprecated methods

  @deprecated("Use withChunkLimit instead", "0.2.0")
  def withPageLimit(
    limit: Int,
    udfTimeout: ScalaDuration = scala.concurrent.duration.Duration(60, "seconds")
  ): SplitStep =
    withChunkLimit(limit, Some(SplitStrategy.Page), udfTimeout)

  @deprecated("Use withChunkRange instead", "0.2.0")
  def withPageRange(
    start: Int,
    end: Int,
    udfTimeout: ScalaDuration = scala.concurrent.duration.Duration(60, "seconds")
  ): SplitStep =
    withChunkRange(start, end, Some(SplitStrategy.Page), udfTimeout)
}
