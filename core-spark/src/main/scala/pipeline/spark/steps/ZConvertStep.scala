package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.{SparkPipelineRegistry, ZSparkStep, UdfHelpers}

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}

import bridges.{Bridge, BridgeRegistry}
import models.FileContent
import types.MimeType

/**
 * Enhanced conversion step using ZSparkStep for better error handling and metrics.
 * This step converts content from one MIME type to another using the BridgeRegistry.
 */
import scala.concurrent.duration.{Duration => ScalaDuration}

case class ZConvertStep(to: MimeType, rowTimeout: ScalaDuration = scala.concurrent.duration.Duration(30, "seconds")) extends ZSparkStep {
  override val name: String = s"to${to.mimeType.split('/').last.capitalize}"
  override val meta: Map[String, String] = Map("out" -> to.mimeType)

  import UdfHelpers._

  // Wrap conversion logic in a UDF that captures timing and errors
  private val convertUdf = wrapUdf2(rowTimeout) { (bytes: Array[Byte], mimeStr: String) =>
    val inMime = MimeType.fromString(mimeStr).getOrElse(MimeType.ApplicationOctet)
    val fc = FileContent(bytes, inMime)

    BridgeRegistry
      .findBridge(inMime, to)
      .collect { case b: Bridge[_, MimeType, _] =>
        b.convert(fc).data
      }
      .getOrElse {
        throw new UnsupportedConversionException(inMime.mimeType, to.mimeType)
      }
  }

  override def doTransform(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
    // Apply conversion and capture results in a StepResult
    val withResult = df.withColumn("result", 
      convertUdf(F.col("content"), F.col("mime"))
    )
    
    // Unpack the result with our helper
    UdfHelpers.unpackResult(withResult)
      .withColumn("mime", F.lit(to.mimeType))
  }
}

// Convenience singletons for common conversions
object ZToPdf extends ZConvertStep(MimeType.ApplicationPdf) {
  SparkPipelineRegistry.register(this)
}

object ZToPng extends ZConvertStep(MimeType.ImagePng) {
  SparkPipelineRegistry.register(this)
}

object ZToText extends ZConvertStep(MimeType.TextPlain) {
  SparkPipelineRegistry.register(this)
}