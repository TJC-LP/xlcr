package com.tjclp.xlcr
package pipeline.spark
package steps

import scala.concurrent.duration.{ Duration => ScalaDuration }

import org.apache.spark.sql.{ functions => F, DataFrame, SparkSession }

import bridges.{ Bridge, BridgeRegistry }
import models.FileContent
import types.MimeType

case class ExtractStep(
  to: MimeType,
  outCol: String,
  override val udfTimeout: ScalaDuration =
    scala.concurrent.duration.Duration(30, "seconds")
) extends SparkStep {

  override val name: String =
    s"extract${to.mimeType.split('/').last.capitalize}"
  override val meta: Map[String, String] =
    super.meta ++ Map("out" -> to.mimeType)

  import UdfHelpers._

  private val extractUdf = wrapUdf2(name, udfTimeout) {
    (bytes: Array[Byte], mimeStr: String) =>
      val inMime =
        MimeType.fromStringNoParams(mimeStr, MimeType.ApplicationOctet)
      val fc = FileContent[inMime.type](bytes, inMime)

      BridgeRegistry
        .findBridge(inMime, to)
        .map { b =>
          val bridge     = b.asInstanceOf[Bridge[_, inMime.type, to.type]]
          val bridgeImpl = bridge.getClass.getSimpleName

          val params = Map(
            "fromMime"     -> inMime.mimeType,
            "toMime"       -> to.mimeType,
            "outputColumn" -> outCol
          )

          UdfHelpers.FoundImplementation[String](
            implementationName = Some(bridgeImpl),
            params = Some(params),
            action = () => {
              val out = bridge.convert(fc)
              new String(out.data, java.nio.charset.StandardCharsets.UTF_8)
            }
          )
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
