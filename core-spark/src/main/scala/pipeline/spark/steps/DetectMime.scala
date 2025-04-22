package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.{SparkPipelineStep, SparkPipelineRegistry}

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}

import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.sax.BodyContentHandler
import org.apache.tika.io.TikaInputStream

/**
 * Detect MIME type and full metadata via Apache Tika.
 *
 * Adds / overwrites two columns:
 *   metadata : map<string,string>
 *   mime     : string (taken from metadata["Content-Type"] or fallback)
 */
object DetectMime extends SparkPipelineStep {
  override val name: String = "detectMime"

  private val detectUdf = F.udf { bytes: Array[Byte] =>
    try {
      val md = new Metadata()
      new AutoDetectParser().parse(
        TikaInputStream.get(bytes),
        new BodyContentHandler(1), // body truncated, we only need headers
        md
      )
      md.names().map(n => n -> md.get(n)).toMap
    } catch {
      case _: Throwable => Map.empty[String, String]
    }
  }

  override def transform(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
    val withMeta = df.withColumn("metadata", detectUdf(F.col("content")))
    withMeta.withColumn(
      "mime",
      F.coalesce(F.expr("metadata['Content-Type']"), F.lit("application/octet-stream"))
    )
  }

  SparkPipelineRegistry.register(this)
}
