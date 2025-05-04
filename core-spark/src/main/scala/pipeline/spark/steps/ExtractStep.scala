package com.tjclp.xlcr
package pipeline.spark
package steps

import bridges.{Bridge, BridgeRegistry}
import models.FileContent
import types.MimeType

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}

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
  override val meta: Map[String, String] =
    super.meta ++ Map("out" -> to.mimeType)

  import UdfHelpers._

  private val extractUdf = wrapUdf2(name, rowTimeout) {
    (bytes: Array[Byte], mimeStr: String) =>
      val inMime =
        MimeType.fromStringNoParams(mimeStr, MimeType.ApplicationOctet)
      val fc = FileContent[inMime.type](bytes, inMime)

      BridgeRegistry
        .findBridge(inMime, to)
        .map { b =>
          // We know the bridge works with our mime types even though we can't enforce it at compile time
          // due to type erasure, so we can safely cast here
          val bridge = b.asInstanceOf[Bridge[_, inMime.type, to.type]]

          val out = bridge.convert(fc)
          val extractedText =
            new String(out.data, java.nio.charset.StandardCharsets.UTF_8)

          // Get the bridge implementation info
          val bridgeImpl = bridge.getClass.getSimpleName

          // Create parameters map with extraction info
          val paramsBuilder = scala.collection.mutable.Map[String, String](
            "fromMime" -> inMime.mimeType,
            "toMime" -> to.mimeType,
            "outputColumn" -> outCol
          )

          // Aspose licensing is handled automatically via the component initialization
          // No need to check or add license status metadata

          (extractedText, Some(bridgeImpl), Some(paramsBuilder.toMap))
        }
        .getOrElse {
          throw UnsupportedConversionException(
            inMime.mimeType,
            to.mimeType
          )
        }
  }

  override protected def doTransform(
      df: DataFrame
  )(implicit spark: SparkSession): DataFrame = {
    import CoreSchema._
    // Apply extraction UDF and capture result in a StepResult
    val withResult =
      df.withColumn(Result, extractUdf(F.col(Content), F.col(Mime)))

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
