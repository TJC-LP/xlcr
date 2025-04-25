package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.{AsposeBroadcastManager, CoreSchema, SparkStep, SparkPipelineRegistry, UdfHelpers}

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}

import bridges.{Bridge, BridgeRegistry}
import models.FileContent
import types.MimeType

/** Binary‑to‑string extraction (e.g. text, XML) using BridgeRegistry. */
import scala.concurrent.duration.{Duration => ScalaDuration}

case class ExtractStep(
    to: MimeType,
    outCol: String,
    rowTimeout: ScalaDuration =
      scala.concurrent.duration.Duration(30, "seconds")
) extends SparkStep {

  override val name: String =
    s"extract${to.mimeType.split('/').last.capitalize}"
  override val meta: Map[String, String] = super.meta ++ Map("out" -> to.mimeType)

  import UdfHelpers._

  private val extractUdf = wrapUdf2(name, rowTimeout) {
    (bytes: Array[Byte], mimeStr: String) =>
      val inMime = MimeType.fromStringNoParams(mimeStr, MimeType.ApplicationOctet)
      val fc = FileContent[inMime.type](bytes, inMime)

      BridgeRegistry
        .findBridge(inMime, to)
        .collect { case b: Bridge[_, inMime.type, to.type] =>
          val out = b.convert(fc)
          val extractedText = new String(out.data, java.nio.charset.StandardCharsets.UTF_8)
          
          // Get the bridge implementation info
          val bridgeImpl = b.getClass.getSimpleName
          
          // Create parameters map with extraction info
          val paramsBuilder = scala.collection.mutable.Map[String, String](
            "fromMime" -> inMime.mimeType,
            "toMime" -> to.mimeType,
            "outputColumn" -> outCol
          )
          
          // Check if we're using an Aspose bridge implementation
          val isAsposeBridge = bridgeImpl.toLowerCase.contains("aspose")
          
          // Add Aspose license info if applicable
          if (isAsposeBridge) {
            // Check if Aspose is enabled and get license status
            if (AsposeBroadcastManager.isEnabled) {
              val licenseStatus = AsposeBroadcastManager.getLicenseStatus
              licenseStatus.foreach { case (k, v) => paramsBuilder.put(k, v) }
            } else {
              paramsBuilder.put("asposeStatus", "disabled")
            }
          }
          
          (extractedText, Some(bridgeImpl), Some(paramsBuilder.toMap))
        }
        .getOrElse {
          // If no bridge found, return empty string with error info
          ("", Some("NoBridgeFound"), Some(Map("error" -> s"No bridge from ${inMime.mimeType} to ${to.mimeType}")))
        }
  }

  override protected def doTransform(
      df: DataFrame
  )(implicit spark: SparkSession): DataFrame = {
    import CoreSchema._
    // Apply extraction UDF and capture result in a StepResult
    val withResult = df.withColumn(Result, extractUdf(F.col(Content), F.col(Mime)))
    
    // Append the lineage entry to the lineage column
    val withLineage = UdfHelpers.appendLineageEntry(
      withResult, 
      F.col(ResultLineage)
    )
    
    // Update content and mime type for the extracted data
    withLineage
      .withColumn(outCol, F.col(ResultData))
      .drop(Result, LineageEntry)
  }
}

// convenience singletons ---------------------------------------------------

object ExtractText extends ExtractStep(MimeType.TextPlain, "text") {
  SparkPipelineRegistry.register(this)
}

object ExtractXml extends ExtractStep(MimeType.ApplicationXml, "xml") {
  SparkPipelineRegistry.register(this)
}
