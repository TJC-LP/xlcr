package com.tjclp.xlcr
package pipeline.spark
package steps

import scala.concurrent.duration.{ Duration => ScalaDuration }

import org.apache.spark.sql.{ functions => F, Column, DataFrame, SparkSession }

import bridges.{ Bridge, BridgeConfig, BridgeRegistry }
import models.FileContent
import types.MimeType

@deprecated("core-spark will be removed in 0.3.0", "0.2.1")
case class ConvertStep(
  to: MimeType,
  override val udfTimeout: ScalaDuration =
    scala.concurrent.duration.Duration(30, "seconds"),
  config: Option[BridgeConfig] = None
) extends SparkStep {
  override val name: String = s"to${to.mimeType.split('/').last.capitalize}"
  override val meta: Map[String, String] =
    super.meta ++ Map("out" -> to.mimeType)

  // Wrap conversion logic in a UDF that captures timing and errors
  private def createConvertUdf =
    UdfHelpers.wrapUdf2(name, udfTimeout) {
      (bytes: Array[Byte], mimeStr: String) =>
        val inMime =
          MimeType.fromStringNoParams(mimeStr, MimeType.ApplicationOctet)

        // Identity case: no conversion required
        if (inMime.matches(to)) {
          UdfHelpers.FoundImplementation[
            Array[Byte]
          ](
            implementationName = Some("IdentityBridge"),
            params = Some(Map("conversion" -> "identity")),
            action = () => bytes
          )
        } else {
          val fc = FileContent[inMime.type](bytes, inMime)

          BridgeRegistry
            .findBridge(inMime, to)
            .map { b =>
              val bridge = b.asInstanceOf[Bridge[_, inMime.type, to.type]]

              val bridgeImpl = bridge.getClass.getSimpleName

              val params = Map(
                "fromMime" -> inMime.mimeType,
                "toMime"   -> to.mimeType
              )

              UdfHelpers.FoundImplementation[
                Array[Byte]
              ](
                implementationName = Some(bridgeImpl),
                params = Some(params),
                action = () => bridge.convert(fc, config).data
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
