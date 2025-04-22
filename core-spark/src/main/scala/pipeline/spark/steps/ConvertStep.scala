package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.{SparkPipelineRegistry, SparkPipelineStep}

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}

import bridges.{Bridge, BridgeRegistry}
import models.FileContent
import types.MimeType

/** Generic binary‑to‑binary conversion step driven by BridgeRegistry. */
case class ConvertStep(to: MimeType) extends SparkPipelineStep {
  override val name: String = s"to${to.mimeType.split('/').last.capitalize}"
  override val meta: Map[String, String] = Map("out" -> to.mimeType)

  private val convertUdf = F.udf { (bytes: Array[Byte], mimeStr: String) =>
    val inMime = MimeType.fromString(mimeStr).getOrElse(MimeType.ApplicationOctet)
    val fc     = FileContent(bytes, inMime)

    BridgeRegistry
      .findBridge(inMime, to)
      .collect { case b: Bridge[_, MimeType, _] =>
        b.convert(fc).data
      }
      .getOrElse(bytes)
  }

  override def transform(df: DataFrame)(implicit spark: SparkSession): DataFrame =
    df.withColumn("content", convertUdf(F.col("content"), F.col("mime")))
      .withColumn("mime", F.lit(to.mimeType))
}

// convenience singletons ------------------------------------------------------------------

object ToPdf extends ConvertStep(MimeType.ApplicationPdf) {
  SparkPipelineRegistry.register(this)
}

object ToPng extends ConvertStep(MimeType.ImagePng) {
  SparkPipelineRegistry.register(this)
}
