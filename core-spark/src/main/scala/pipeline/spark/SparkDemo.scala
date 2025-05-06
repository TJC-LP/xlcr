package com.tjclp.xlcr
package pipeline.spark

import java.nio.file.{ Files, Paths }
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import scala.concurrent.duration.Duration

import org.apache.spark.sql.functions.md5
import org.apache.spark.sql.{ functions => F, DataFrame, SparkSession }

import pipeline.spark.steps._
import pipeline.spark.steps.SparkStepUtils

/**
 * Simple demo that wires together a minimal pipeline and shows how the new CoreSchema
 * initialisation works.
 */
object SparkDemo {

  /**
   * Run the demo pipeline with default splitting behaviour.
   */
  def runDemo(inputPath: String, outputPath: String): Unit = {
    implicit val spark: SparkSession = SparkSession
      .builder()
      .appName("XLCR SparkStep Demo")
      .master("local[*]")
      .getOrCreate()

    try {
      // -------------------------------------------------------------------
      // 1. Ingest
      // -------------------------------------------------------------------

      val ingested = spark.read
        .format("binaryFile")
        .load(inputPath) // path, content, length, modificationTime

      // -------------------------------------------------------------------
      // 2. Conform to core schema
      // -------------------------------------------------------------------

      val coreInit = CoreSchema.ensure(
        ingested
          // id = hash(content) so identical bytes collapse; keep original path
          .withColumn(CoreSchema.Id, md5(F.col("content")))
          .withColumn(CoreSchema.Mime, F.lit("application/octet-stream"))
          .withColumn(CoreSchema.Lineage, F.array())
      )

      // -------------------------------------------------------------------
      // 3. Build pipeline
      // -------------------------------------------------------------------

      val pipeline = buildBasicPipeline()

      // -------------------------------------------------------------------
      // 4. Run & write
      // -------------------------------------------------------------------

      // Run the pipeline with auto-initialization
      val result = SparkStepUtils.runPipeline(coreInit, pipeline)

      writeResults(result, outputPath)

      println(s"Processing complete. Results written to $outputPath")

    } finally
      spark.stop()
  }

  /** Simple pipeline: detect mime then split. */
  private def buildBasicPipeline(): SparkStep = {
    val detect = DetectMime
    val split  = SplitStep().withTimeout(Duration(60, "seconds"))
    SparkStepUtils.buildPipeline(detect, split)
  }

  /** Writes the results of the pipeline to the specified output path. */
  private def writeResults(df: DataFrame, outputPath: String): Unit = {
    val path = Paths.get(outputPath)
    if (!Files.exists(path)) Files.createDirectories(path)

    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

    // Persist full rows
    df.write
      .option("compression", "snappy")
      .mode("overwrite")
      .parquet(s"$outputPath/results_$timestamp")

    // Persist outcome metrics for quick inspection
    import CoreSchema._
    df.select(
      F.col(Id),
      F.col("path"),
      F.col(Mime),
      F.col(Lineage),
      F.col("error"),
      F.col("duration_ms")
    ).write
      .option("compression", "snappy")
      .mode("overwrite")
      .parquet(s"$outputPath/metrics_$timestamp")
  }

  /** CLI entry-point. */
  def main(args: Array[String]): Unit = {
    if (args.length < 2) {
      println("Usage: SparkDemo <inputPath> <outputPath>")
      System.exit(1)
    }
    runDemo(args(0), args(1))
  }
}
