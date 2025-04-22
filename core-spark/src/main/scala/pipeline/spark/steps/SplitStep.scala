package com.tjclp.xlcr
package pipeline.spark.steps

import models.FileContent
import pipeline.spark.{SparkPipelineRegistry, SparkStep, UdfHelpers}
import types.MimeType
import utils.{DocumentSplitter, SplitConfig, SplitStrategy}

import org.apache.spark.sql.types.{BinaryType, StringType, StructType}
import org.apache.spark.sql.{DataFrame, Row, SparkSession, functions => F}

import scala.concurrent.duration.{Duration => ScalaDuration}

/** Document splitting step with comprehensive metrics and error handling.
  * Splits documents into chunks according to the specified strategy.
  */
case class SplitStep(
    strategy: Option[SplitStrategy] = None,
    recursive: Boolean = false,
    maxRecursionDepth: Int = 3,
    rowTimeout: ScalaDuration =
      scala.concurrent.duration.Duration(60, "seconds")
) extends SparkStep {

  override val name: String =
    s"split${strategy.map(_.toString.capitalize).getOrElse("")}"

  override val meta: Map[String, String] = Map(
    "strategy" -> strategy.map(_.toString).getOrElse("auto"),
    "recursive" -> recursive.toString,
    "maxDepth" -> maxRecursionDepth.toString
  )

  // Schema for split results
  private val chunkSchema = new StructType()
    .add("index", org.apache.spark.sql.types.IntegerType)
    .add("label", StringType)
    .add("content", BinaryType)
    .add("mime", StringType)

  import UdfHelpers._

  // UDF that splits a document using the DocumentSplitter
  private val splitUdf = wrapUdf2(rowTimeout) {
    (bytes: Array[Byte], mimeStr: String) =>
      val mime =
        MimeType.fromString(mimeStr).getOrElse(MimeType.ApplicationOctet)
      val content = FileContent(bytes, mime)
      val config = SplitConfig(
        strategy = strategy.getOrElse(defaultStrategyForMime(mime)),
        recursive = recursive,
        maxRecursionDepth = maxRecursionDepth
      )

      val chunks = DocumentSplitter.split(content, config)

      // Convert to a structure compatible with Spark
      chunks.map { chunk =>
        Row(
          chunk.index,
          chunk.label,
          chunk.content.data,
          chunk.content.mimeType.mimeType
        )
      }.toArray
  }

  override def doTransform(
      df: DataFrame
  )(implicit spark: SparkSession): DataFrame = {

    // Apply splitting and capture results
    val withResult =
      df.withColumn("result", splitUdf(F.col("content"), F.col("mime")))

    // Unpack result using common helper and extract chunks array
    val withChunks = UdfHelpers
      .unpackResult(withResult, dataCol = "chunks", fallbackCol = "chunks")
      .withColumn(
        "chunks",
        F.when(F.col("chunks").isNotNull, F.col("chunks"))
          .otherwise(F.typedLit(Array.empty[Row]))
      )

    // Explode the chunks array into individual rows
    val chunksDF = withChunks
      .select(
        F.col("id"), // Keep document ID for reference
        F.col("file_path"),
        F.explode_outer(F.col("chunks")).as("chunk"),
        F.col("start_time_ms"),
        F.col("end_time_ms"),
        F.col("duration_ms"),
        F.col("error"),
        F.col("metrics"),
        F.col("step_name")
      )
      .select(
        F.col("id"),
        F.col("file_path"),
        F.col("chunk").getItem("index").as("chunk_index"),
        F.col("chunk").getItem("label").as("chunk_label"),
        F.col("chunk").getItem("content").as("content"),
        F.col("chunk").getItem("mime").as("mime"),
        F.col("start_time_ms"),
        F.col("end_time_ms"),
        F.col("duration_ms"),
        F.col("error"),
        F.col("metrics"),
        F.col("step_name")
      )

    // Add a flag to show if the split produced chunks
    // This helps identify documents that couldn't be split
    val flaggedDF = chunksDF.withColumn(
      "split_success",
      F.when(F.col("chunk_index").isNotNull, F.lit(true))
        .otherwise(F.lit(false))
    )

    flaggedDF
  }

  /** Default split strategy if the user hasn't specified one. */
  private def defaultStrategyForMime(mime: MimeType): SplitStrategy =
    mime match {
      case MimeType.ApplicationPdf => SplitStrategy.Page

      // Excel formats
      case MimeType.ApplicationVndMsExcel |
          MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet =>
        SplitStrategy.Sheet

      // PowerPoint formats
      case MimeType.ApplicationVndMsPowerpoint |
          MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation =>
        SplitStrategy.Slide

      // Archive / containers default to embedded entries
      case MimeType.ApplicationZip | MimeType.ApplicationGzip |
          MimeType.ApplicationSevenz | MimeType.ApplicationTar |
          MimeType.ApplicationBzip2 | MimeType.ApplicationXz =>
        SplitStrategy.Embedded

      // Emails default to attachments
      case MimeType.MessageRfc822 | MimeType.ApplicationVndMsOutlook =>
        SplitStrategy.Attachment

      case _ => SplitStrategy.Page // generic fallback
    }
}

// Convenience singletons for common split strategies
object SplitByPage extends SplitStep(Some(SplitStrategy.Page)) {
  SparkPipelineRegistry.register(this)
}

object SplitBySheet extends SplitStep(Some(SplitStrategy.Sheet)) {
  SparkPipelineRegistry.register(this)
}

object SplitBySlide extends SplitStep(Some(SplitStrategy.Slide)) {
  SparkPipelineRegistry.register(this)
}

object SplitRecursive extends SplitStep(recursive = true) {
  SparkPipelineRegistry.register(this)
}
