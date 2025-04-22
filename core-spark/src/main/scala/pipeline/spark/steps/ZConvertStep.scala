package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.{SparkPipelineRegistry, ZSparkStep, ZSparkStepRegistry}

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}

import bridges.{Bridge, BridgeRegistry}
import models.FileContent
import types.MimeType

/**
 * Enhanced conversion step using ZSparkStep for better error handling and metrics.
 * This step converts content from one MIME type to another using the BridgeRegistry.
 */
case class ZConvertStep(to: MimeType) extends ZSparkStep {
  override val name: String = s"to${to.mimeType.split('/').last.capitalize}"
  override val meta: Map[String, String] = Map("out" -> to.mimeType)

  // Wrap conversion logic in a UDF that captures timing and errors
  private val convertUdf = wrapUdf2 { (bytes: Array[Byte], mimeStr: String) =>
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
    
      // Add metrics columns using Unix timestamps
    val withMetrics = withResult
      .withColumn("step_name", F.expr("result.stepName"))
      .withColumn("duration_ms", F.expr("result.durationMs"))
      .withColumn("start_time_ms", F.expr("result.startTimeMs"))
      .withColumn("end_time_ms", F.expr("result.endTimeMs"))
      .withColumn("error", F.when(F.expr("result.isFailure"), F.expr("result.error")).otherwise(F.lit(null)))
    
    // Extract the actual data from StepResult, using original content as fallback
    val withContent = withMetrics
      .withColumn("content", 
        F.when(F.expr("result.isSuccess"), F.expr("result.data"))
         .otherwise(F.col("content")))
      .withColumn("mime", F.lit(to.mimeType))
      
    // Drop the intermediate result column and return
    withContent.drop("result")
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