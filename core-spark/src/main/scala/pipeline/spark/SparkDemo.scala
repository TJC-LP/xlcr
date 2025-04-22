package com.tjclp.xlcr
package pipeline.spark

import pipeline.spark.steps._

import org.apache.spark.sql.functions.input_file_name
import org.apache.spark.sql.{DataFrame, SparkSession}

import java.nio.file.{Files, Paths}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.Duration

/** Demonstrates the use of SparkStep for building robust Spark pipelines.
  */
object SparkDemo {

  /** Run the demo pipeline with default splitting behavior.
    */
  def runDemo(inputPath: String, outputPath: String): Unit = {
    implicit val spark: SparkSession = SparkSession
      .builder()
      .appName("XLCR SparkStep Demo")
      .master("local[*]")
      .getOrCreate()

    try {
      // built‑in steps are registered on object initialisation now

      // Create a DataFrame from the input files using spark.read.binaryFiles
      val inputDf = spark.read
        .format("binaryFile")
        .load(inputPath)
        .withColumn("id", input_file_name())
        .withColumnRenamed("path", "file_path")

      // Build a simple pipeline with MIME detection and default splitting
      val pipeline = buildBasicPipeline()

      // Execute the pipeline directly (it returns a DataFrame now, not a Task)
      val result = pipeline(inputDf)

      // Write results
      writeResults(result, outputPath)

      println(s"Processing complete. Results written to $outputPath")

    } finally {
      spark.stop()
    }
  }

  /** Create a simple pipeline with MIME detection and default splitting.
    */
  private def buildBasicPipeline(): SparkPipelineStep = {
    // Detect MIME type first
    val detectStep = DetectMime

    // Then split using default strategy (determined by mime type)
    val splitStep = SplitStep().withTimeout(
      Duration.apply(60, java.util.concurrent.TimeUnit.SECONDS)
    )

    // Complete pipeline
    detectStep.andThen(splitStep)
  }

  /** Demonstrates backward compatibility with standard SparkPipelineStep.
    * This pipeline mixes different steps from the registry and direct references.
    */
  private def buildMixedPipeline()(implicit spark: SparkSession): SparkStep = {
    // Get a step from the registry
    val standardStep = SparkPipelineRegistry.get("detectMime")

    // Create a step directly
    val pdfStep = ToPdf

    // Combine the steps
    standardStep.andThen(pdfStep)
  }

  /** Writes the results of the pipeline to the specified output path.
    */
  private def writeResults(df: DataFrame, outputPath: String): Unit = {
    // Create output directory if it doesn't exist
    val path = Paths.get(outputPath)
    if (!Files.exists(path)) {
      Files.createDirectories(path)
    }

    // Generate timestamp for the output
    val timestamp = LocalDateTime
      .now()
      .format(
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
      )

    // Write results as parquet
    df.write
      .option("compression", "snappy")
      .mode("overwrite")
      .parquet(s"$outputPath/results_$timestamp")

    // Also write metrics separately for analysis
    df.select(
      "id",
      "file_path",
      "mime",
      "start_time_ms",
      "end_time_ms",
      "duration_ms",
      "error",
      "metrics",
      "step_name"
    ).write
      .option("compression", "snappy")
      .mode("overwrite")
      .parquet(s"$outputPath/metrics_$timestamp")
  }

  /** Main entry point for running the demo from command line.
    */
  def main(args: Array[String]): Unit = {
    if (args.length < 2) {
      println("Usage: SparkDemo <inputPath> <outputPath>")
      System.exit(1)
    }

    val inputPath = args(0)
    val outputPath = args(1)

    runDemo(inputPath, outputPath)
  }
}
