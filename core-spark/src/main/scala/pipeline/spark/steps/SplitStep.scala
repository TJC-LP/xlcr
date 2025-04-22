package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.{SparkPipelineRegistry, SparkPipelineStep}

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}

import utils.{DocumentSplitter, SplitConfig, SplitStrategy}
import models.FileContent
import types.MimeType

/** Split documents into chunks (pages, sheets, slides, …). */
object SplitStep extends SparkPipelineStep {
  override val name: String = "splitAuto"

  private val splitUdf = F.udf { (bytes: Array[Byte], mimeS: String) =>
    val mime = MimeType.fromString(mimeS).getOrElse(MimeType.ApplicationOctet)
    val cfg  = SplitConfig(strategy = defaultStrategy(mime))
    DocumentSplitter.splitBytesOnly(FileContent(bytes, mime), cfg)
      .map(fc => (fc.data, fc.mimeType.mimeType))
  }

  private def defaultStrategy(mime: MimeType): SplitStrategy = mime match {
    case MimeType.ApplicationPdf => SplitStrategy.Page
    case MimeType.ApplicationVndMsExcel | MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet => SplitStrategy.Sheet
    case MimeType.ApplicationVndMsPowerpoint | MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation => SplitStrategy.Slide
    case MimeType.MessageRfc822 | MimeType.ApplicationVndMsOutlook => SplitStrategy.Attachment
    case MimeType.ApplicationZip | MimeType.ApplicationGzip | MimeType.ApplicationSevenz |
         MimeType.ApplicationTar | MimeType.ApplicationBzip2 | MimeType.ApplicationXz => SplitStrategy.Embedded
    case _ => SplitStrategy.Page
  }

  override def transform(df: DataFrame)(implicit spark: SparkSession): DataFrame =
    df.withColumn("_chunks", splitUdf(F.col("content"), F.col("mime")))
      .withColumn("_chunk", F.explode(F.col("_chunks")))
      .selectExpr("_chunk._1 as content", "_chunk._2 as mime", "*")
      .drop("_chunks", "_chunk")

  SparkPipelineRegistry.register(this)
}
