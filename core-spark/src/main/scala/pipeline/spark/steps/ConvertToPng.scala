package com.tjclp.xlcr
package pipeline.spark.steps

import bridges.{Bridge, BridgeRegistry}
import models.FileContent
import pipeline.spark.{SparkPipelineRegistry, SparkPipelineStep}
import types.MimeType

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}

/** Convert documents (e.g. PDF pages, SVG) into PNG images. */
object ConvertToPng extends SparkPipelineStep {
  override val name: String = "toPng"
  private val targetMime = MimeType.ImagePng
  override val meta: Map[String, String] = Map("out" -> targetMime.mimeType)

  private val convert = F.udf { (bytes: Array[Byte], mimeStr: String) =>
    val inMime =
      MimeType.fromString(mimeStr).getOrElse(MimeType.ApplicationOctet)
    val inFc = FileContent(bytes, inMime)

    BridgeRegistry
      .findBridge(inMime, targetMime)
      .collect { case b: Bridge[_, MimeType, _] =>
        b.convert(inFc)
          .data
      }
      .getOrElse(bytes)
  }

  override def transform(
      df: DataFrame
  )(implicit spark: SparkSession): DataFrame =
    df.withColumn("content", convert(F.col("content"), F.col("mime")))
      .withColumn("mime", F.lit(targetMime.mimeType))

  SparkPipelineRegistry.register(this)
}
