package com.tjclp.xlcr
package pipeline.spark.steps

import bridges.{Bridge, BridgeRegistry}
import models.FileContent
import pipeline.spark.{SparkPipelineRegistry, SparkPipelineStep}
import types.MimeType

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}

/** Convert any supported mime‑type to PDF. */
object ConvertToPdf extends SparkPipelineStep {
  override val name: String = "toPdf"
  private val targetMime = MimeType.ApplicationPdf
  override val meta: Map[String, String] = Map("out" -> targetMime.mimeType)

  private val convert = F.udf { (bytes: Array[Byte], mimeStr: String) =>
    val inMime =
      MimeType.fromString(mimeStr).getOrElse(MimeType.ApplicationOctet)
    val inFc = FileContent(bytes, inMime)

    BridgeRegistry
      .findBridge(inMime, targetMime)
      .collect { case b: Bridge[_, MimeType, _] =>
        b.convert(inFc).data
      }
      .getOrElse(bytes)
  }

  override def transform(
      df: DataFrame
  )(implicit spark: SparkSession): DataFrame =
    df.withColumn("content", convert(F.col("content"), F.col("mime")))
      .withColumn("mime", F.lit(targetMime.mimeType))

  // auto‑register
  SparkPipelineRegistry.register(this)
}
