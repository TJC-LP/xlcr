package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.{SparkPipelineRegistry, SparkStep, UdfHelpers}

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}
import org.apache.tika.io.TikaInputStream
import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.sax.BodyContentHandler

/** MIME type detection using Apache Tika with comprehensive error handling and metrics.
  * Detects MIME type and metadata from binary content.
  */
object DetectMime extends SparkStep {
  override val name: String = "detectMime"

  // Wrap Tika detection in a UDF with timing and error handling
  import UdfHelpers._

  private val detectUdf = wrapUdf(
    scala.concurrent.duration.Duration(30, "seconds")
  ) { bytes: Array[Byte] =>
    val md = new Metadata()
    new AutoDetectParser().parse(
      TikaInputStream.get(bytes),
      new BodyContentHandler(1), // body truncated, we only need headers
      md
    )
    md.names().map(n => n -> md.get(n)).toMap
  }

  override def doTransform(
      df: DataFrame
  )(implicit spark: SparkSession): DataFrame = {
    // Apply the UDF and capture result in a StepResult
    val withResult = df.withColumn("result", detectUdf(F.col("content")))

    // Unpack result and extract metadata into its own column
    val withMetadata = UdfHelpers
      .unpackResult(withResult, dataCol = "metadata", fallbackCol = "metadata")
      .withColumn(
        "metadata",
        F.when(F.col("metadata").isNotNull, F.col("metadata"))
          .otherwise(F.typedLit(Map.empty[String, String]))
      )

    // Set MIME type from metadata or use octet-stream as fallback
    withMetadata.withColumn(
      "mime",
      F.coalesce(
        F.expr("metadata['Content-Type']"),
        F.lit("application/octet-stream")
      )
    )
  }

  // Register with the pipeline registry
  SparkPipelineRegistry.register(this)
}
