# Splitter Failure Modes: Configurable Error Handling

## Overview

Document splitters in XLCR can encounter various failure scenarios where they cannot properly split a document into chunks. This document describes the configurable failure mode system that allows users to choose how these failures are handled based on their specific use case requirements.

## Current Behavior

Currently, splitters follow a **fail-safe approach** where they return the entire document as a single chunk when splitting fails. This happens in scenarios such as:

- Incorrect strategy (e.g., requesting page splitting for an Excel file)
- Empty content (e.g., email with no body or attachments)
- Corrupted or malformed documents
- Missing required dependencies or libraries
- No splitter available for the MIME type

```scala
// Current behavior example
if (!cfg.hasStrategy(SplitStrategy.Page))
  return Seq(DocChunk(content, "document", 0, 1))  // Silent fallback
```

## Problem Statement

### Issues with Silent Preservation

1. **Hidden Failures**: Problems are not immediately visible, making debugging difficult
2. **Resource Consumption**: Large unsplit documents consume excessive memory and processing time
3. **Unexpected Downstream Behavior**: Systems expecting multiple small chunks receive single large documents
4. **Difficult Recovery**: Hard to identify which documents need reprocessing

### Issues with Failing Loudly

1. **Pipeline Interruption**: One problematic document can halt entire batch processing
2. **Data Loss Risk**: Documents might not reach storage systems
3. **Breaking Changes**: Existing workflows depend on current behavior

## Proposed Solution: Configurable Failure Modes

Implement a flexible system where users can configure how splitters handle failures based on their specific requirements.

### Failure Mode Types

```scala
sealed trait SplitFailureMode
case object PreserveAsChunk extends SplitFailureMode  // Current behavior
case object ThrowException extends SplitFailureMode   // Fail loudly
case object DropDocument extends SplitFailureMode     // Skip failed documents
case object TagAndPreserve extends SplitFailureMode   // Add error metadata
```

### Configuration

```scala
case class SplitConfig(
  strategy: Option[SplitStrategy] = None,
  chunkRange: Option[Range] = None,
  maxChars: Int = 4000,
  recursive: Boolean = false,
  maxRecursionDepth: Int = 10,
  maxTotalSize: Long = 1024L * 1024 * 1024,
  maxFileCount: Long = 1000L,
  // New field with backward-compatible default
  failureMode: SplitFailureMode = PreserveAsChunk
)
```

## Implementation Examples

### 1. PreserveAsChunk Mode (Default)

Maintains current behavior for backward compatibility.

```scala
object PdfPageSplitter extends DocumentSplitter[MimeType.ApplicationPdf.type] {
  override def split(
    content: FileContent[MimeType.ApplicationPdf.type],
    cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    
    if (!cfg.hasStrategy(SplitStrategy.Page)) {
      cfg.failureMode match {
        case PreserveAsChunk => 
          Seq(DocChunk(content, "document", 0, 1))
        case _ => 
          handleFailure(cfg.failureMode, 
            "Invalid strategy for PDF splitting", content)
      }
    }
    
    try {
      val doc = PDDocument.load(content.data)
      // ... normal splitting logic ...
    } catch {
      case e: Exception =>
        handleFailure(cfg.failureMode, e.getMessage, content)
    }
  }
}
```

### 2. ThrowException Mode

Fails immediately with descriptive error message.

```scala
private def handleFailure[T <: MimeType](
  mode: SplitFailureMode,
  error: String,
  content: FileContent[T]
): Seq[DocChunk[_ <: MimeType]] = mode match {
  case ThrowException =>
    throw new SplitException(
      s"Failed to split ${content.mimeType}: $error"
    )
  case PreserveAsChunk =>
    Seq(DocChunk(content, "document", 0, 1))
  case DropDocument =>
    Seq.empty
  case TagAndPreserve =>
    Seq(DocChunk(
      content, 
      "document", 
      0, 
      1,
      metadata = Map(
        "split_error" -> error,
        "split_failed" -> "true",
        "attempted_strategy" -> cfg.strategy.map(_.displayName).getOrElse("none")
      )
    ))
}
```

### 3. DropDocument Mode

Silently skips documents that cannot be split.

```scala
// In Spark pipeline
val splitResults = df
  .withColumn("split_result", splitUdf(col("content"), col("mime")))
  .filter(col("split_result").isNotNull)  // Dropped documents are null
```

### 4. TagAndPreserve Mode

Adds error metadata for observability while preserving data flow.

```scala
case class DocChunk[+T <: MimeType](
  content: FileContent[T],
  label: String,
  index: Int,
  total: Int,
  metadata: Map[String, String] = Map.empty  // Enhanced with error info
)

// Usage in downstream processing
df.filter(
  !col("metadata").getItem("split_failed").equalTo("true")
).select("successfully_split_documents")
```

## Spark Integration

### Enhanced UDF Wrapper

```scala
object UdfHelpers {
  def wrapSplitUdf(name: String, timeout: Duration, failureMode: SplitFailureMode)(
    f: (Array[Byte], String) => Seq[DocChunk[_ <: MimeType]]
  ): UserDefinedFunction = {
    
    val wrapped = (bytes: Array[Byte], mimeStr: String) => {
      try {
        val result = withTimeout(timeout) {
          f(bytes, mimeStr)
        }
        
        // Add success metadata
        result.map(chunk => chunk.copy(
          metadata = chunk.metadata + ("split_status" -> "success")
        ))
        
      } catch {
        case e: SplitException if failureMode == ThrowException =>
          throw e  // Propagate to Spark
          
        case e: Exception =>
          failureMode match {
            case TagAndPreserve =>
              Seq(DocChunk(
                FileContent(bytes, MimeType.fromString(mimeStr)),
                "error_document",
                0,
                1,
                Map(
                  "split_status" -> "failed",
                  "error_message" -> e.getMessage,
                  "error_type" -> e.getClass.getSimpleName
                )
              ))
            case DropDocument =>
              null  // Spark will filter nulls
            case PreserveAsChunk =>
              Seq(DocChunk(
                FileContent(bytes, MimeType.fromString(mimeStr)),
                "document",
                0,
                1
              ))
            case ThrowException =>
              throw new RuntimeException(s"Split failed: ${e.getMessage}", e)
          }
      }
    }
    
    udf(wrapped)
  }
}
```

### Pipeline Configuration

```scala
// Conservative approach for production
val productionConfig = SplitConfig(
  strategy = Some(SplitStrategy.Auto),
  failureMode = TagAndPreserve  // Monitor failures without breaking pipeline
)

// Strict approach for data quality validation
val validationConfig = SplitConfig(
  strategy = Some(SplitStrategy.Page),
  failureMode = ThrowException  // Fail fast on any issues
)

// Performance-optimized approach
val performanceConfig = SplitConfig(
  strategy = Some(SplitStrategy.Auto),
  failureMode = DropDocument,  // Skip problematic documents
  chunkRange = Some(0 until 100)  // Limit chunks per document
)
```

## Monitoring and Observability

### Metrics Collection

```scala
val splitMetrics = df
  .select(
    col("metadata.split_status"),
    col("metadata.error_type")
  )
  .groupBy("split_status", "error_type")
  .count()
  
splitMetrics.show()
// +-------------+------------------+-----+
// |split_status |error_type        |count|
// +-------------+------------------+-----+
// |success      |null              |9823 |
// |failed       |IOException       |12   |
// |failed       |InvalidPdfError   |3    |
// +-------------+------------------+-----+
```

### Error Analysis

```scala
// Find documents that failed to split
val failedSplits = df.filter(
  col("metadata.split_status") === "failed"
).select(
  col("source_path"),
  col("mime_type"),
  col("metadata.error_message"),
  col("metadata.attempted_strategy")
)

// Retry with different configuration
val retryConfig = SplitConfig(
  strategy = Some(SplitStrategy.Chunk),  // Try different strategy
  failureMode = TagAndPreserve,
  maxChars = 10000  // Larger chunks
)
```

## Migration Guide

### Phase 1: Add Monitoring (No Breaking Changes)
```scala
// Update configuration to add metadata
val config = existingConfig.copy(failureMode = TagAndPreserve)

// Monitor failure rates
df.filter(col("metadata.split_failed") === "true").count()
```

### Phase 2: Selective Strict Mode
```scala
// Enable strict mode for specific document types
val config = content.mimeType match {
  case MimeType.ApplicationPdf => 
    SplitConfig(failureMode = ThrowException)
  case _ => 
    SplitConfig(failureMode = PreserveAsChunk)
}
```

### Phase 3: Full Migration
```scala
// Once monitoring confirms low failure rates
val config = SplitConfig(
  failureMode = ThrowException,  // Strict by default
  strategy = Some(SplitStrategy.Auto)
)
```

## Best Practices

1. **Start with TagAndPreserve** mode to understand failure patterns
2. **Use ThrowException** for data quality critical applications
3. **Use DropDocument** only when document loss is acceptable
4. **Monitor split_status** metrics in production
5. **Implement retry logic** for transient failures
6. **Test failure modes** in staging environments

## Future Enhancements

1. **Retry Policies**: Automatic retry with exponential backoff
2. **Fallback Strategies**: Try alternative splitting strategies on failure
3. **Custom Failure Handlers**: Plugin system for domain-specific handling
4. **ML-Based Strategy Selection**: Learn optimal strategies from failure patterns
5. **Partial Success Mode**: Return successfully split chunks even if some fail