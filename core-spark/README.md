# XLCR Spark Module

`core-spark` brings the composable XLCR document‑processing pipeline to Apache Spark 3.x.

Each **SparkPipelineStep** is serialisable and composable ( `andThen`, `fanOut`, `withTimeout`) just like the in‑memory `PipelineStep`, while automatically appending a lineage array column for provenance.

## Built‑in steps

| Step name    | Description                                        |
|--------------|----------------------------------------------------|
| `detectMime` | Run Tika once, produce `metadata` map + `mime`      |
| `splitAuto`  | Split pages / sheets / slides / attachments         |
| `toPdf`      | Convert any supported format ➜ **PDF**              |
| `toPng`      | Convert (PDF page, SVG, …) ➜ **PNG**               |
| `extractText`| Extract plain text                                 |
| `extractXml` | Extract Tika XML                                   |

All singletons self‑register in `SparkPipelineRegistry`, so you can reference them in a DSL string.

## Streaming example (binaryFile source)

```scala
import org.apache.spark.sql.SparkSession
import com.tjclp.xlcr.pipeline.spark._
import pipeline.spark.SparkPipelineRegistry

implicit val spark = SparkSession.builder()
  .appName("xlcr-stream")
  .getOrCreate()

// ------------------------------------------------------------------
// 1) Build pipeline from DSL
// ------------------------------------------------------------------

val dsl = "detectMime|splitAuto|toPdf|extractText"
val pipeline = dsl.split("\\|").map(SparkPipelineRegistry.get).reduce(_ andThen _)

// ------------------------------------------------------------------
// 2) Read binary files as streaming DataFrame
// ------------------------------------------------------------------

val input = spark.readStream.format("binaryFile").load("/mnt/raw")
  .withColumnRenamed("content", "content")
  .withColumn("lineage", expr("array()"))      // seed lineage

// ------------------------------------------------------------------
// 3) foreachBatch to add custom sinks / error routing
// ------------------------------------------------------------------

val query = input.writeStream.foreachBatch { (batch, _) =>
  val processed = pipeline(batch)

  val ok = processed.filter("error IS NULL")
  val ko = processed.filter("error IS NOT NULL")

  ok.write.mode("append").parquet("/mnt/processed/success")
  ko.write.mode("append").parquet("/mnt/processed/failed")
}.start()

query.awaitTermination()
```

Deploy this snippet as a **jar task** in Databricks or run locally with `spark-submit`.

## Extend with your own step

```scala
object ToSvg extends ConvertStep(MimeType.ImageSvgXml) {
  SparkPipelineRegistry.register(this)
}
```

Compile, and the DSL can now reference `toSvg` without code changes elsewhere.
