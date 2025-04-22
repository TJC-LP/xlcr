package com.tjclp.xlcr
package pipeline.spark.steps

import models.FileContent
import pipeline.spark.{SparkPipelineRegistry, SparkPipelineStep}
import types.MimeType
import utils.{DocumentSplitter, SplitConfig, SplitStrategy}

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}

/** Split binary documents (one row → many rows) using the existing
  * DocumentSplitter infrastructure.
  *
  * Expected input schema:
  *   content : binary
  *   mime    : string  (IANA mime‑type string)
  */
object SplitStep extends SparkPipelineStep {
  override val name: String = "splitAuto"

  private val splitUdf = F.udf { (bytes: Array[Byte], mimeStr: String) =>
    val mime = MimeType.fromString(mimeStr).getOrElse(MimeType.ApplicationOctet)
    val cfg = SplitConfig(strategy = defaultStrategy(mime))
    val chunks = DocumentSplitter.splitBytesOnly(FileContent(bytes, mime), cfg)
    // Return Seq[(Array[Byte], String)]  for content + mime columns
    chunks.map(fc => (fc.data, fc.mimeType.mimeType))
  }

  private def defaultStrategy(mime: MimeType): SplitStrategy = mime match {
    case MimeType.ApplicationPdf => SplitStrategy.Page
    case MimeType.ApplicationVndMsExcel |
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet =>
      SplitStrategy.Sheet
    case MimeType.ApplicationVndMsPowerpoint |
        MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation =>
      SplitStrategy.Slide
    case MimeType.MessageRfc822 | MimeType.ApplicationVndMsOutlook =>
      SplitStrategy.Attachment
    case MimeType.ApplicationZip | MimeType.ApplicationGzip |
        MimeType.ApplicationSevenz | MimeType.ApplicationTar |
        MimeType.ApplicationBzip2 | MimeType.ApplicationXz =>
      SplitStrategy.Embedded
    case _ => SplitStrategy.Page
  }

  override def transform(
      df: DataFrame
  )(implicit spark: SparkSession): DataFrame = {
    df
      .withColumn("_chunks", splitUdf(F.col("content"), F.col("mime")))
      .withColumn("_chunk", F.explode(F.col("_chunks")))
      .selectExpr("_chunk._1 as content", "_chunk._2 as mime", "*")
      .drop("_chunks", "_chunk")
  }

  // Auto‑register
  SparkPipelineRegistry.register(this)
}
