package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.{SparkPipelineRegistry, SparkStep, UdfHelpers}

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}

import bridges.{Bridge, BridgeRegistry}
import models.FileContent
import types.MimeType

/** Conversion step that transforms content from one MIME type to another.
  * Uses the BridgeRegistry to find appropriate converters with error handling and metrics.
  */
import scala.concurrent.duration.{Duration => ScalaDuration}

case class ConvertStep(
    to: MimeType,
    rowTimeout: ScalaDuration =
      scala.concurrent.duration.Duration(30, "seconds")
) extends SparkStep {
  override val name: String = s"to${to.mimeType.split('/').last.capitalize}"
  override val meta: Map[String, String] = Map("out" -> to.mimeType)

  import UdfHelpers._

  // Wrap conversion logic in a UDF that captures timing and errors
  private val convertUdf = wrapUdf2(rowTimeout) {
    (bytes: Array[Byte], mimeStr: String) =>
      val inMime = MimeType.fromStringNoParams(mimeStr, MimeType.ApplicationOctet)
      
      // Skip conversion if input and output MIME types match
      if (inMime.matches(to)) {
        // No conversion needed, return original bytes
        bytes
      } else {
        // Create FileContent with the specific MIME type
        val fc = FileContent[inMime.type](bytes, inMime)

        BridgeRegistry
          .findBridge(inMime, to)
          .collect {
            case b: Bridge[_, inMime.type, to.type] => b.convert(fc).data
          }
          .getOrElse {
            throw new UnsupportedConversionException(inMime.mimeType, to.mimeType)
          }
      }
  }

  override def doTransform(
      df: DataFrame
  )(implicit spark: SparkSession): DataFrame = {
    // Apply conversion and capture results in a StepResult
    val withResult =
      df.withColumn("result", convertUdf(F.col("content"), F.col("mime")))

    // Unpack the result with our helper
    UdfHelpers
      .unpackResult(withResult, fallbackCol = Some("content"))
      .withColumn("mime", F.lit(to.mimeType))
  }
}

// Convenience singletons for common conversions
object ToPdf extends ConvertStep(MimeType.ApplicationPdf) {
  SparkPipelineRegistry.register(this)
}

object ToPng extends ConvertStep(MimeType.ImagePng) {
  SparkPipelineRegistry.register(this)
}

object ToText extends ConvertStep(MimeType.TextPlain) {
  SparkPipelineRegistry.register(this)
}
