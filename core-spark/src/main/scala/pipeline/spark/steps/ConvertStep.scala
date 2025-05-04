package com.tjclp.xlcr
package pipeline.spark
package steps

import bridges.{Bridge, BridgeRegistry}
import models.FileContent
import types.MimeType

import org.apache.spark.sql.{Column, DataFrame, SparkSession, functions => F}

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
  override val meta: Map[String, String] =
    super.meta ++ Map("out" -> to.mimeType)

  // Wrap conversion logic in a UDF that captures timing and errors
  private def createConvertUdf =
    UdfHelpers.wrapUdf2(name, rowTimeout) {
      (bytes: Array[Byte], mimeStr: String) =>
        val inMime =
          MimeType.fromStringNoParams(mimeStr, MimeType.ApplicationOctet)

        // Skip conversion if input and output MIME types match
        if (inMime.matches(to)) {
          // No conversion needed, return original bytes with identity bridge info
          (bytes, Some("IdentityBridge"), Some(Map("conversion" -> "identity")))
        } else {
          // Create FileContent with the specific MIME type
          val fc = FileContent[inMime.type](bytes, inMime)

          BridgeRegistry
            .findBridge(inMime, to)
            .map { b =>
              // We know the bridge works with our mime types even though we can't enforce it at compile time
              // due to type erasure, so we can safely cast here
              val bridge = b.asInstanceOf[Bridge[_, inMime.type, to.type]]

              // Return both the converted data and the bridge implementation name
              val bridgeImpl = bridge.getClass.getSimpleName

              // Create parameters map with conversion info
              val paramsBuilder = scala.collection.mutable.Map[String, String](
                "fromMime" -> inMime.mimeType,
                "toMime" -> to.mimeType
              )

              (
                bridge.convert(fc).data,
                Some(bridgeImpl),
                Some(paramsBuilder.toMap)
              )
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
    // Create the UDF
    val convertUdf = createConvertUdf

    // Apply conversion and capture results in a StepResult
    val withResult =
      df.withColumn(Result, convertUdf(F.col(Content), F.col(Mime)))

    val failSafe = (x: Column, y: Column, z: Column) =>
      F.when(x.isNull, y).otherwise(z)

    // Append the lineage entry to the lineage column
    val withLineage = UdfHelpers.appendLineageEntry(
      withResult,
      F.col(ResultLineage)
    )

    // Update content and mime type based on the conversion result
    withLineage
      // Keep original content column if conversion fails (helps with retries)
      .withColumn(
        Content,
        failSafe(F.col(LineageEntryError), F.col(ResultData), F.col(Content))
      )
      .withColumn(
        Mime,
        failSafe(F.col(LineageEntryError), F.lit(to.mimeType), F.col(Mime))
      )
      .drop(Result, LineageEntry)
  }
}
