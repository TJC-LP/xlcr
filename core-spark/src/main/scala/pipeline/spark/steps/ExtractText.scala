package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.{SparkPipelineRegistry, SparkPipelineStep}

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}

import com.tjclp.xlcr.types.MimeType
import com.tjclp.xlcr.models.FileContent
import com.tjclp.xlcr.bridges.{Bridge, BridgeRegistry}

/** Extract plain text from any supported document using Tika bridge. */
object ExtractText extends SparkPipelineStep {
  override val name: String = "extractText"
  private val targetMime = MimeType.TextPlain
  override val meta: Map[String, String] = Map("out" -> targetMime.mimeType)

  private val extract = F.udf { (bytes: Array[Byte], mimeStr: String) =>
    val inMime = MimeType.fromString(mimeStr).getOrElse(MimeType.ApplicationOctet)
    val fc     = FileContent(bytes, inMime)

    BridgeRegistry
      .findBridge(inMime, targetMime)
      .collect { case b: Bridge[_, MimeType, _] =>
        val out = b.convert(fc)
        new String(out.data, java.nio.charset.StandardCharsets.UTF_8)
      }
      .getOrElse("")
  }

  override def transform(df: DataFrame)(implicit spark: SparkSession): DataFrame =
    df.withColumn("text", extract(F.col("content"), F.col("mime")))

  SparkPipelineRegistry.register(this)
}
