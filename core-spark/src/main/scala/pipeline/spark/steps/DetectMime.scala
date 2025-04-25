package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.{SparkPipelineRegistry, SparkStep, CoreSchema, UdfHelpers}

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}
import org.apache.tika.io.TikaInputStream
import org.apache.tika.metadata.{Metadata => TikaMetadata}
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.sax.BodyContentHandler
import org.apache.tika.parser.ParseContext
import java.io.IOException
import org.apache.tika.exception.TikaException
import org.xml.sax.SAXException

/** MIME type detection using Apache Tika with comprehensive error handling and metrics.
  * Detects MIME type and metadata from binary content.
  */
object DetectMime extends SparkStep {
  override val name: String = "detectMime"

  // Wrap Tika detection in a UDF with timing and error handling
  import UdfHelpers._

  private val detectUdf = wrapUdf(
    name,
    scala.concurrent.duration.Duration(30, "seconds"),
    Some("TikaAutoDetectParser")
  ) { bytes: Array[Byte] =>
    val md = new TikaMetadata()
    try {
      val parser = new AutoDetectParser()
      val handler =
        new BodyContentHandler(1) // body truncated, we only need headers
      val stream = TikaInputStream.get(bytes)
      parser.parse(stream, handler, md, new ParseContext())
    } catch {
      case e @ (_: IOException | _: TikaException | _: SAXException) =>
      // Log the exception if needed
      // logger.warn(s"Error during MIME detection: ${e.getMessage}")
    }
    
    // Always return the metadata, even if an exception occurred
    val metadataMap = md.names().map(n => n -> md.get(n)).toMap
    
    // Get detected MIME type for parameters
    val detectedMime = Option(md.get("Content-Type")).getOrElse("application/octet-stream")
    val params = Map("detectedMime" -> detectedMime)
    
    (metadataMap, Some("TikaAutoDetectParser"), Some(params))
  }

  override def doTransform(
      df: DataFrame
  )(implicit spark: SparkSession): DataFrame = {
    // Apply the UDF and capture result in a StepResult
    import CoreSchema._
    val withResult = df.withColumn(Result, detectUdf(F.col(Content)))
    
    // Append the lineage entry to the lineage column
    val withLineage = UdfHelpers.appendLineageEntry(
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

  // Register with the pipeline registry
  SparkPipelineRegistry.register(this)
}
