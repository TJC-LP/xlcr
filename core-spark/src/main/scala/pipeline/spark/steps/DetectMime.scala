package com.tjclp.xlcr
package pipeline.spark
package steps

import java.io.IOException

import scala.concurrent.duration.{ Duration => ScalaDuration }

import org.apache.spark.sql.{ functions => F, DataFrame, SparkSession }
import org.apache.tika.exception.TikaException
import org.apache.tika.io.TikaInputStream
import org.apache.tika.metadata.{ Metadata => TikaMetadata }
import org.apache.tika.parser.{ AutoDetectParser, ParseContext }
import org.apache.tika.sax.BodyContentHandler
import org.xml.sax.SAXException

/**
 * MIME type detection using Apache Tika with comprehensive error handling and metrics. Detects MIME
 * type and metadata from binary content.
 *
 * This is the Scala 3 version with correct lambda syntax.
 *
 * @param udfTimeout
 *   Duration for UDF timeout per file. Default is 30 seconds.
 */
@deprecated("core-spark will be removed in 0.3.0", "0.2.1")
case class DetectMime(
  override val udfTimeout: ScalaDuration = ScalaDuration(30, "seconds")
) extends SparkStep {
  override val name: String = "detectMime"

  // Wrap Tika detection in a UDF with timing and error handling
  import UdfHelpers._

  private val detectUdf = wrapUdf(
    name,
    udfTimeout
  ) { (bytes: Array[Byte]) => // Note the parentheses around lambda parameter for Scala 3
    // *Finding* phase: We already know we will use Tika's AutoDetectParser.
    val implName = "TikaAutoDetectParser"

    // Action encapsulated to allow error capture
    FoundImplementation[Map[String, String]](
      implementationName = Some(implName),
      params = None,
      action = () => {
        val md = new TikaMetadata()
        try {
          val parser  = new AutoDetectParser()
          val handler = new BodyContentHandler(1)
          val stream  = TikaInputStream.get(bytes)
          parser.parse(stream, handler, md, new ParseContext())
        } catch {
          case _: IOException | _: TikaException | _: SAXException =>
        }

        val metadataMap = md.names().map(n => n -> md.get(n)).toMap
        metadataMap
      }
    )
  }

  override def doTransform(
    df: DataFrame
  )(implicit spark: SparkSession): DataFrame = {
    // Apply the UDF and capture result in a StepResult
    import CoreSchema._
    val withResult = df.withColumn(Result, detectUdf(F.col(Content)))

    // Append the lineage entry to the lineage column
    val withLineage = appendLineageEntry(
      withResult,
      F.col(ResultLineage)
    )

    // Extract metadata and update the dataframe
    val withMetadata = withLineage
      .withColumn(Metadata, F.col(ResultData))
      .drop(Result, LineageEntry)

    // Set MIME type from metadata or use octet-stream as fallback
    withMetadata.withColumn(
      Mime,
      F.coalesce(
        F.expr(s"$Metadata['Content-Type']"),
        F.lit("application/octet-stream")
      )
    )
  }
}

// Register a default instance for backward compatibility
object DetectMime {
  val default: DetectMime = DetectMime()
  SparkPipelineRegistry.register(default)
}
