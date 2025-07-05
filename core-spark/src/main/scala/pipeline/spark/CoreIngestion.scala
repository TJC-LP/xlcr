package com.tjclp.xlcr
package pipeline.spark

import org.apache.spark.sql.functions.md5
import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}

/**
 * Convenience helpers for turning the output of `spark.read.binaryFiles` into a DataFrame that
 * already satisfies the CoreSchema contract. This is the fastest way for users to “init” an XLCR
 * Spark pipeline.
 */
object CoreIngestion {

  /**
   * Read files with Spark’s built-in binaryFiles reader and conform them to the core schema. The
   * resulting DataFrame has at least the following columns (non-nullable): id, content, mime,
   * metadata, lineage, last_step, chunk_* …
   *
   * Additional columns coming from binaryFiles (path, length, modificationTime) are preserved.
   *
   * id – MD5 hash of the binary content (duplicates collapse) mime – initial value
   * "application/octet-stream" (detect later) lineage – empty array metadata – null (MapType)
   */
  def binaryFiles(path: String)(implicit spark: SparkSession): DataFrame = {
    val raw = spark.read.format("binaryFile").load(path)
    fromBinaryFilesDf(raw)
  }

  /**
   * Conform an existing DataFrame that came from `spark.read.binaryFiles` (or an equivalent source)
   * to the core schema.
   *
   * The input is expected to contain at least two columns: – `content` (binary) – raw bytes –
   * `path` (string) – original file path Other columns (length, modificationTime, …) are kept
   * intact.
   */
  def fromBinaryFilesDf(df: DataFrame): DataFrame = {
    val initial = df
      .withColumn(CoreSchema.Id, md5(F.col("content")))
      .withColumn(CoreSchema.Mime, F.lit("application/octet-stream"))
      .withColumn(CoreSchema.Lineage, F.array())

    CoreSchema.ensure(initial)
  }
}
