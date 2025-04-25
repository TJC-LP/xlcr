package com.tjclp.xlcr
package pipeline.spark

import org.apache.spark.sql.types._
import org.apache.spark.sql.{Column, DataFrame, functions => F}

/** Central place that defines the **core contract** that every row in the Spark
  * pipeline must satisfy.
  *
  *  – Only these columns are *required* and validated after each `SparkStep`.
  *  – Additional user-defined columns can appear / disappear freely.
  *
  * Having the contract in one file makes evolution (adding a column, changing a
  * type) a single-point change.
  */
object CoreSchema {
  /* --------------------------------------------------------------------- */
  /* Column names & nested types                                           */
  /* --------------------------------------------------------------------- */

  val Id = "id"
  val Content = "content"
  val Mime = "mime"
  val Metadata = "metadata"
  val Lineage = "lineage"
  val ChunkIndex = "chunkIndex"
  val ChunkLabel = "chunkLabel"
  val ChunkTotal = "chunkTotal"

  // lineage element
  val LineageType: DataType = StructType(
    Seq(
      StructField("startTimeMs", LongType, nullable = true),
      StructField("endTimeMs", LongType, nullable = true),
      StructField("durationMs", LongType, nullable = true),
      StructField("error", StringType, nullable = true),
      StructField("name", StringType, nullable = true),
      StructField("implementation", StringType, nullable = true),
      StructField("params", MapType(StringType, StringType), nullable = true)
    )
  )
  val LineageArrayType: DataType = ArrayType(LineageType, containsNull = true)

  // Temporary columns
  val LineageEntry = "lineageEntry"
  val LineageEntryError = "result.lineage.error"
  val Result = "result"
  val ResultData = "result.data"
  val ResultLineage = "result.lineage"
  val Chunks = "chunks"
  val Chunk = "chunk"

  /* --------------------------------------------------------------------- */
  /* Public API                                                            */
  /* --------------------------------------------------------------------- */

  /** List of StructField that must be present (case-sensitive). */
  val required: Seq[StructField] = Seq(
    StructField(Id, StringType, nullable = false),
    StructField(
      Content,
      BinaryType,
      nullable = true
    ), // may be null after extraction
    StructField(Mime, StringType, nullable = true),
    StructField(
      Metadata,
      MapType(StringType, StringType),
      nullable = true
    ),
    StructField(
      Lineage,
      LineageArrayType,
      nullable = true
    ),
    // chunk context – null when row is not a chunk
    StructField(ChunkIndex, LongType, nullable = true),
    StructField(ChunkLabel, StringType, nullable = true),
    StructField(ChunkTotal, LongType, nullable = true)
  )

  /** Validate that the provided DataFrame contains the core columns with the
   * expected data types. Throws `IllegalStateException` on mismatch.
   */
  def requireCore(df: DataFrame, stepName: String = "<unknown>"): Unit = {
    val missing = required.filterNot(f => hasField(df, f.name, f.dataType))
    if (missing.nonEmpty) {
      // Create a StructType from the required fields for comparison
      val goldenSchema = StructType(required)

      val msg =
        s"Step $stepName violated core contract; missing / mistyped columns: ${missing.map(_.name).mkString(", ")}.\n" +
          s"Expected schema:\n${goldenSchema.treeString}\n" +
          s"Actual schema:\n${df.schema.treeString}"
      throw new IllegalStateException(msg)
    }
  }


  /** Initialise an arbitrary DataFrame so that it satisfies the core schema.
    * Missing columns are added with NULL / default values; existing columns are
    * kept as-is.
    */
  def ensure(df: DataFrame): DataFrame = {

    // add each required column if it does not exist
    val withCore = required.foldLeft(df) { case (acc, field) =>
      if (acc.columns.contains(field.name)) acc
      else {
        val col: Column = field.dataType match {
          case BinaryType       => F.lit(null).cast(BinaryType)
          case StringType       => F.lit(null).cast(StringType)
          case MapType(_, _, _) => F.lit(null).cast(field.dataType)
          case ArrayType(_, _) =>
            F.array()
              .cast(field.dataType) // empty array of correct element type
          case StructType(_) => F.lit(null).cast(field.dataType)
          case _             => F.lit(null).cast(field.dataType)
        }
        acc.withColumn(field.name, col)
      }
    }

    // Ensure column order is stable (nice-to-have)
    withCore.select(
      required.map(f => F.col(f.name)) ++ withCore.columns
        .filterNot(c => required.exists(_.name == c))
        .map(F.col): _*
    )
  }

  /* --------------------------------------------------------------------- */
  /* Internals                                                             */
  /* --------------------------------------------------------------------- */

  private def hasField(df: DataFrame, name: String, dt: DataType): Boolean =
    df.schema.fields.exists(f => f.name == name && f.dataType == dt)
}
