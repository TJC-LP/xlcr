package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.{SparkPipelineRegistry, SparkStep, UdfHelpers, CoreSchema}

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
  private val convertUdf = wrapUdf2(name, rowTimeout) {
    (bytes: Array[Byte], mimeStr: String) =>
      val inMime =
        MimeType.fromStringNoParams(mimeStr, MimeType.ApplicationOctet)

      // Skip conversion if input and output MIME types match
      if (inMime.matches(to)) {
        // No conversion needed, return original bytes
        bytes
      } else {
        // Create FileContent with the specific MIME type
        val fc = FileContent[inMime.type](bytes, inMime)

        BridgeRegistry
          .findBridge(inMime, to)
          .collect { case b: Bridge[_, inMime.type, to.type] =>
            b.convert(fc).data
          }
          .getOrElse {
            throw UnsupportedConversionException(
              inMime.mimeType,
              to.mimeType
            )
          }
      }
  }

  override def doTransform(
      df: DataFrame
  )(implicit spark: SparkSession): DataFrame = {
    import CoreSchema._
    // Apply conversion and capture results in a StepResult
    val withResult = df.withColumn(Result, convertUdf(F.col(Content), F.col(Mime)))
    
    // Append the lineage entry to the lineage column
    val withLineage = UdfHelpers.appendLineageEntry(
      withResult, 
      F.col(ResultLineage)
    )
    
    // Update content and mime type based on the conversion result
    withLineage
      .withColumn(Content, F.col(ResultData))
      .withColumn(
        Mime,
        F.when(F.col(LineageEntryError).isNull, F.lit(to.mimeType))
          .otherwise(F.col(Mime))
      )
      .drop(Result, LineageEntry)
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

object ToXml extends ConvertStep(MimeType.ApplicationXml) {
  SparkPipelineRegistry.register(this)
}
