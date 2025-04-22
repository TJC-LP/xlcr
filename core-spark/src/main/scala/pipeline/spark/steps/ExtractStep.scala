package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.{SparkPipelineRegistry, SparkPipelineStep}

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}

import bridges.{Bridge, BridgeRegistry}
import models.FileContent
import types.MimeType

/** Generic binary‑to‑string extraction (e.g. text, XML) via BridgeRegistry. */
case class ExtractStep(to: MimeType, outCol: String) extends SparkPipelineStep {
  override val name: String = s"extract${to.mimeType.split('/').last.capitalize}"
  override val meta: Map[String, String] = Map("out" -> to.mimeType)

  private val extractUdf = F.udf { (bytes: Array[Byte], mimeStr: String) =>
    val inMime = MimeType.fromString(mimeStr).getOrElse(MimeType.ApplicationOctet)
    val fc     = FileContent(bytes, inMime)

    BridgeRegistry
      .findBridge(inMime, to)
      .collect { case b: Bridge[_, MimeType, _] =>
        val out = b.convert(fc)
        new String(out.data, java.nio.charset.StandardCharsets.UTF_8)
      }
      .getOrElse("")
  }

  override def transform(df: DataFrame)(implicit spark: SparkSession): DataFrame =
    df.withColumn(outCol, extractUdf(F.col("content"), F.col("mime")))
}

// convenience singletons -----------------------------------------------------

object ExtractText extends ExtractStep(MimeType.TextPlain, "text") {
  SparkPipelineRegistry.register(this)
}

object ExtractXml extends ExtractStep(MimeType.ApplicationXml, "xml") {
  SparkPipelineRegistry.register(this)
}
