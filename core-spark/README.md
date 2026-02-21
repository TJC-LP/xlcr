# XLCR Spark Module

`core-spark` brings the composable XLCR document‑processing pipeline to Apache Spark 3.x.

Each **SparkStep** is serialisable and composable (`andThen`, `fanOut`, `withTimeout`) just like the in‑memory core steps, with automatic lineage tracking for full provenance.

## Key Features

- **Unified ZIO-powered execution model** - All steps use ZIO for consistent timeout handling and error recovery
- **Per-row timeout controls** - Fine-grained timeout control at both the step and row level
- **Detailed metrics** - Comprehensive timing and performance metrics for each operation
- **Automatic lineage tracking** - Every transformation is recorded in a lineage column
- **Robust error handling** - Failed operations maintain the original content with detailed error information
- **Thread-safety** - All registries use concurrent collections for Spark executor safety

## Built-in steps

| Step name        | Description                                                   |
|------------------|---------------------------------------------------------------|
| `detectMime`     | Run Tika to produce `metadata` map + `mime`                   |
| `splitAuto`      | Auto-split based on mime type (PDF→pages, Excel→sheets, etc.) |
| `splitByPage`    | Split PDFs into pages                                         |
| `splitBySheet`   | Split Excel files into sheets                                 |
| `splitBySlide`   | Split PowerPoint files into slides                            |
| `splitRecursive` | Recursive splitting (ZIP→files→pages, etc.)                   |
| `toPdf`          | Convert documents to PDF format                               |
| `toPng`          | Convert documents to PNG images                               |
| `toText`         | Convert documents to plain text                               |
| `extractText`    | Extract text from documents                                   |
| `extractXml`     | Extract XML from documents                                    |

All steps self-register in `SparkPipelineRegistry`, so you can reference them in a DSL string.

## Timeout Configuration

Each step that uses UDFs can be configured with a custom timeout duration. This timeout applies **per row/file**, not to the entire DataFrame operation. When a timeout occurs, the error is captured in the lineage with the message "timeout", making it easy to identify and retry timed-out files.

### Configuring Timeouts

```scala
import scala.concurrent.duration._

// Configure timeout at step creation
val detectMime = DetectMime(udfTimeout = Duration(60, "seconds"))
val convertStep = ConvertStep(to = MimeType.ApplicationPdf, udfTimeout = Duration(120, "seconds"))
val splitStep = SplitStep(udfTimeout = Duration(180, "seconds"))

// Or use the factory method to override timeout on existing steps
val slowDetect = SparkStep.withUdfTimeout(DetectMime.default, Duration(90, "seconds"))

// Build pipeline with custom timeouts
val pipeline = detectMime
  .andThen(splitStep)
  .andThen(convertStep)
```

## Universal Chunk Limiting

You can limit the number of chunks extracted during splitting for any document type:

```scala
import scala.concurrent.duration._

// Limit to first 10 chunks (pages for PDFs, sheets for Excel, slides for PowerPoint, etc.)
val splitFirst10 = SplitStep.withChunkLimit(10)

// Extract specific chunk range (0-based)
val splitMiddleChunks = SplitStep.withChunkRange(10, 20)  // Chunks 11-20

// Auto strategy with chunk limit (applies to any document type)
val autoSplitLimited = SplitStep.auto(chunkLimit = Some(50))

// Combine with timeout configuration and specific strategy
val splitWithTimeout = SplitStep.withChunkLimit(20, Some(SplitStrategy.Sheet), udfTimeout = 120.seconds)

// Example pipeline for processing large documents
val pipeline = DetectMime()
  .andThen(SplitStep.withChunkLimit(100, udfTimeout = 180.seconds))
  .andThen(ConvertStep(MimeType.ImagePng))
  .andThen(ExtractText())
```

This universal approach works for:
- **PDFs**: Limits the number of pages extracted
- **Excel**: Limits the number of sheets extracted
- **PowerPoint**: Limits the number of slides extracted
- **Archives**: Limits the number of entries extracted
- **Emails**: Limits the number of attachments extracted
- **Any future splitter**: Will respect the chunk range automatically

This is especially useful when:
- Processing very large documents that might cause memory issues
- You only need to process a sample of chunks for analysis
- Building pipelines that need to handle documents of varying sizes reliably

### Identifying Timed-Out Files

Files that timeout during processing can be easily identified and retried:

```scala
// Process documents
val processed = pipeline(inputDf)

// Find rows that timed out
val timedOut = processed.filter(
  array_contains(col("lineage.error"), "timeout")
)

// Retry with longer timeout
val retryPipeline = pipeline.map(step =>
  SparkStep.withUdfTimeout(step, Duration(5, "minutes"))
)
val retryResults = retryPipeline(timedOut)
```

## Streaming example (binaryFile source)

```scala
import org.apache.spark.sql.SparkSession
import com.tjclp.xlcr.pipeline.spark._
import org.apache.spark.sql.functions._

implicit val spark = SparkSession.builder()
  .appName("xlcr-document-processor")
  .getOrCreate()

// ------------------------------------------------------------------
// 1) Build pipeline from DSL
// ------------------------------------------------------------------

val dsl = "detectMime|splitByPage|toPdf|extractText"
val pipeline = dsl.split("\\|").map(SparkPipelineRegistry.get).reduce(_ andThen _)

// ------------------------------------------------------------------
// 2) Read binary files as streaming DataFrame
// ------------------------------------------------------------------

val input = spark.readStream.format("binaryFile").load("/mnt/raw")
  .withColumnRenamed("content", "content")
  .withColumn("lineage", array())      // seed lineage column

// ------------------------------------------------------------------
// 3) foreachBatch to add custom sinks / error routing
// ------------------------------------------------------------------

val query = input.writeStream.foreachBatch { (batch, _) =>
  val processed = pipeline(batch)

  // Route based on success/failure
  val ok = processed.filter("error IS NULL")
  val ko = processed.filter("error IS NOT NULL")

  // Write to appropriate destinations
  ok.write.mode("append").parquet("/mnt/processed/success")
  ko.write.mode("append").parquet("/mnt/processed/failed")

  // Optional: collect metrics
  processed.groupBy("step_name")
    .agg(
      count("*").as("total_rows"),
      avg("duration_ms").as("avg_duration_ms"),
      max("duration_ms").as("max_duration_ms")
    )
    .write.mode("append").format("delta").saveAsTable("metrics.step_performance")
}.start()

query.awaitTermination()
```

## Extend with your own step

Create a new step by extending `SparkStep`:

```scala
import scala.concurrent.duration._

// Basic implementation
case class MyCustomStep() extends SparkStep {
  override val name: String = "myCustomStep"

  override protected def doTransform(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
    // Your transformation logic here
    df.withColumn("custom_field", lit("my value"))
  }
}

// Step with UDF and timing metrics
case class MyAdvancedStep() extends SparkStep {
  override val name: String = "myAdvancedStep"

  // Define UDF with timeout using UdfHelpers
  import UdfHelpers._
  private val customUdf = wrapUdf(30.seconds) { input: String =>
    // Complex processing with automatic timeout handling
    input.toUpperCase
  }

  override protected def doTransform(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
    // Apply UDF and get StepResult with timing metrics
    val withResult = df.withColumn("result", customUdf(col("text")))

    // Unpack the StepResult into standard columns
    UdfHelpers.unpackResult(withResult, dataCol = "processed_text")
  }
}

// Register your steps
SparkPipelineRegistry.register(MyCustomStep())
SparkPipelineRegistry.register(MyAdvancedStep())
```

## Aspose Integration

The module automatically integrates with Aspose when license files are detected or when explicitly enabled. There are several ways to use Aspose:

### Automatic License Detection

The system automatically detects Aspose licenses from:

1. Environment variables with Base64-encoded license data:
   - `ASPOSE_TOTAL_LICENSE_B64` - For all Aspose products
   - `ASPOSE_WORDS_LICENSE_B64` - For Word documents
   - `ASPOSE_CELLS_LICENSE_B64` - For Excel spreadsheets
   - `ASPOSE_SLIDES_LICENSE_B64` - For PowerPoint presentations
   - `ASPOSE_EMAIL_LICENSE_B64` - For email formats
   - `ASPOSE_ZIP_LICENSE_B64` - For archives

2. License files in the current working directory or classpath:
   - `Aspose.Total.Java.lic` - For all Aspose products
   - `Aspose.Words.Java.lic` - For Word documents
   - `Aspose.Cells.Java.lic` - For Excel spreadsheets
   - `Aspose.Slides.Java.lic` - For PowerPoint presentations
   - `Aspose.Email.Java.lic` - For email formats
   - `Aspose.Zip.Java.lic` - For archives

### Explicit Enablement

You can also explicitly enable Aspose:

```
export XLCR_ASPOSE_ENABLED=true
```

This enables high-fidelity document processing for Office formats.

### Running Without a License

If Aspose is enabled but no valid license is found, the system will operate in evaluation mode with watermarks on output documents.
