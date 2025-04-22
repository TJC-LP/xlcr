package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.{ZSparkStep, SparkPipelineRegistry, UdfHelpers}

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}

import bridges.{Bridge, BridgeRegistry}
import models.FileContent
import types.MimeType

/** Binary‑to‑string extraction (e.g. text, XML) using BridgeRegistry. */
import scala.concurrent.duration.{Duration => ScalaDuration}

case class ExtractStep(to: MimeType, outCol: String, rowTimeout: ScalaDuration = scala.concurrent.duration.Duration(30, "seconds")) extends ZSparkStep {

  override val name: String = s"extract${to.mimeType.split('/').last.capitalize}"
  override val meta: Map[String, String] = Map("out" -> to.mimeType)

  import UdfHelpers._

  private val extractUdf = wrapUdf2(rowTimeout) { (bytes: Array[Byte], mimeStr: String) =>
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

  override protected def doTransform(df: DataFrame)(implicit spark: SparkSession): DataFrame =
    df.withColumn(outCol, extractUdf(F.col("content"), F.col("mime")))
}

// convenience singletons ---------------------------------------------------

object ExtractText extends ExtractStep(MimeType.TextPlain, "text") {
  SparkPipelineRegistry.register(this)
}

object ExtractXml extends ExtractStep(MimeType.ApplicationXml, "xml") {
  SparkPipelineRegistry.register(this)
}
