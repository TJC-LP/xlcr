package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.{ZSparkStep, SparkPipelineRegistry, ZSparkStepRegistry}

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}

import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.sax.BodyContentHandler
import org.apache.tika.io.TikaInputStream

/**
 * Enhanced MIME type detection using Tika with comprehensive error handling and metrics.
 * Detects MIME type and metadata from binary content.
 */
object ZDetectMime extends ZSparkStep {
  override val name: String = "zdetectMime"

  // Wrap Tika detection in a UDF with timing and error handling
  private val detectUdf = wrapUdf { bytes: Array[Byte] =>
    val md = new Metadata()
    new AutoDetectParser().parse(
      TikaInputStream.get(bytes),
      new BodyContentHandler(1), // body truncated, we only need headers
      md
    )
    md.names().map(n => n -> md.get(n)).toMap
  }

  override def doTransform(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
    // Apply the UDF and capture result in a StepResult
    val withResult = df.withColumn("result", detectUdf(F.col("content")))
    
    // Add metrics columns using Unix timestamps
    val withMetrics = withResult
      .withColumn("step_name", F.expr("result.stepName"))
      .withColumn("duration_ms", F.expr("result.durationMs"))
      .withColumn("start_time_ms", F.expr("result.startTimeMs"))
      .withColumn("end_time_ms", F.expr("result.endTimeMs"))
      .withColumn("error", F.when(F.expr("result.isFailure"), F.expr("result.error")).otherwise(F.lit(null)))
    
    // Extract the metadata map from StepResult, with empty map as fallback
    val withMetadata = withMetrics.withColumn(
      "metadata", 
      F.when(F.expr("result.isSuccess"), F.expr("result.data"))
       .otherwise(F.typedLit(Map.empty[String, String]))
    )
    
    // Set MIME type from metadata or use octet-stream as fallback
    val withMime = withMetadata.withColumn(
      "mime",
      F.coalesce(F.expr("metadata['Content-Type']"), F.lit("application/octet-stream"))
    )
    
    // Drop the intermediate result column
    withMime.drop("result")
  }

  // Register with the pipeline registry
  SparkPipelineRegistry.register(this)
}