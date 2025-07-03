# Splitter Failure Modes: Implementation Design

## Executive Summary

This document outlines the implementation plan for adding configurable failure modes to XLCR's document splitters. The goal is to provide users with control over how splitting failures are handled while maintaining backward compatibility.

## Design Principles

1. **Backward Compatibility**: Default behavior must remain unchanged
2. **Consistency**: All splitters should handle failures uniformly
3. **Observability**: Failures should be trackable and debuggable
4. **Performance**: Failure handling should not impact successful operations
5. **Flexibility**: Support different failure modes for different use cases

## Core Components

### 1. Failure Mode Enumeration

```scala
// core/src/main/scala/splitters/SplitFailureMode.scala
package com.tjclp.xlcr.splitters

/**
 * Defines how document splitters should handle failures.
 */
sealed trait SplitFailureMode {
  def name: String
}

object SplitFailureMode {
  /**
   * Preserve document as single chunk (current behavior).
   * Maintains backward compatibility.
   */
  case object PreserveAsChunk extends SplitFailureMode {
    val name = "preserve"
  }
  
  /**
   * Throw exception immediately on failure.
   * Best for data quality validation.
   */
  case object ThrowException extends SplitFailureMode {
    val name = "throw"
  }
  
  /**
   * Drop documents that cannot be split.
   * Best for performance-critical pipelines.
   */
  case object DropDocument extends SplitFailureMode {
    val name = "drop"
  }
  
  /**
   * Tag with error metadata and preserve.
   * Best for monitoring and debugging.
   */
  case object TagAndPreserve extends SplitFailureMode {
    val name = "tag"
  }
  
  def fromString(s: String): Option[SplitFailureMode] = s.toLowerCase match {
    case "preserve" => Some(PreserveAsChunk)
    case "throw" => Some(ThrowException)
    case "drop" => Some(DropDocument)
    case "tag" => Some(TagAndPreserve)
    case _ => None
  }
}
```

### 2. Enhanced SplitConfig

```scala
// core/src/main/scala/splitters/SplitConfig.scala
case class SplitConfig(
  strategy: Option[SplitStrategy] = None,
  chunkRange: Option[Range] = None,
  maxChars: Int = 4000,
  recursive: Boolean = false,
  maxRecursionDepth: Int = 10,
  maxTotalSize: Long = 1024L * 1024 * 1024,
  maxFileCount: Long = 1000L,
  // New field with backward-compatible default
  failureMode: SplitFailureMode = SplitFailureMode.PreserveAsChunk,
  // Optional failure context for better error messages
  failureContext: Map[String, String] = Map.empty
) {
  // ... existing methods ...
  
  /**
   * Create a copy with specific failure mode.
   */
  def withFailureMode(mode: SplitFailureMode): SplitConfig = 
    copy(failureMode = mode)
    
  /**
   * Add context information for failure handling.
   */
  def withFailureContext(key: String, value: String): SplitConfig =
    copy(failureContext = failureContext + (key -> value))
}
```

### 3. Split Exception Hierarchy

```scala
// core/src/main/scala/splitters/SplitException.scala
package com.tjclp.xlcr.splitters

/**
 * Base exception for all splitting failures.
 */
class SplitException(
  message: String,
  cause: Throwable = null,
  val mimeType: String = "",
  val strategy: Option[String] = None,
  val context: Map[String, String] = Map.empty
) extends RuntimeException(message, cause) {
  
  def withContext(key: String, value: String): SplitException =
    new SplitException(message, cause, mimeType, strategy, context + (key -> value))
    
  override def getMessage: String = {
    val contextStr = if (context.isEmpty) "" else s" [${context.mkString(", ")}]"
    s"$message (mime: $mimeType, strategy: ${strategy.getOrElse("none")})$contextStr"
  }
}

/**
 * Thrown when requested strategy doesn't match document type.
 */
class InvalidStrategyException(
  mimeType: String,
  requestedStrategy: String
) extends SplitException(
  s"Strategy '$requestedStrategy' not supported for MIME type '$mimeType'",
  mimeType = mimeType,
  strategy = Some(requestedStrategy)
)

/**
 * Thrown when document is corrupted or malformed.
 */
class CorruptedDocumentException(
  mimeType: String,
  details: String,
  cause: Throwable = null
) extends SplitException(
  s"Document appears to be corrupted: $details",
  cause,
  mimeType = mimeType
)
```

### 4. Enhanced DocChunk with Metadata

```scala
// core/src/main/scala/splitters/DocChunk.scala
case class DocChunk[+T <: MimeType](
  content: FileContent[T],
  label: String,
  index: Int,
  total: Int,
  metadata: Map[String, String] = Map.empty
) {
  /**
   * Check if this chunk represents a splitting failure.
   */
  def isFailed: Boolean = metadata.get("split_status").contains("failed")
  
  /**
   * Get error message if this is a failed chunk.
   */
  def errorMessage: Option[String] = metadata.get("error_message")
  
  /**
   * Add metadata to this chunk.
   */
  def withMetadata(key: String, value: String): DocChunk[T] =
    copy(metadata = metadata + (key -> value))
    
  /**
   * Mark this chunk as a failure with error details.
   */
  def asFailure(error: String, errorType: String = "Unknown"): DocChunk[T] =
    copy(metadata = metadata ++ Map(
      "split_status" -> "failed",
      "error_message" -> error,
      "error_type" -> errorType,
      "failed_at" -> java.time.Instant.now().toString
    ))
}
```

### 5. Failure Handler Trait

```scala
// core/src/main/scala/splitters/SplitFailureHandler.scala
package com.tjclp.xlcr.splitters

import models.FileContent
import types.MimeType

/**
 * Handles splitting failures according to configured mode.
 */
trait SplitFailureHandler {
  
  protected def handleSplitFailure[T <: MimeType](
    mode: SplitFailureMode,
    content: FileContent[T],
    error: String,
    cause: Throwable = null,
    context: Map[String, String] = Map.empty
  ): Seq[DocChunk[_ <: MimeType]] = mode match {
    
    case SplitFailureMode.ThrowException =>
      val ex = new SplitException(error, cause, content.mimeType.mimeType)
      context.foreach { case (k, v) => ex.withContext(k, v) }
      throw ex
      
    case SplitFailureMode.PreserveAsChunk =>
      Seq(DocChunk(content, "document", 0, 1))
      
    case SplitFailureMode.DropDocument =>
      Seq.empty
      
    case SplitFailureMode.TagAndPreserve =>
      val errorType = Option(cause).map(_.getClass.getSimpleName).getOrElse("SplitError")
      Seq(DocChunk(
        content,
        "failed_document",
        0,
        1,
        metadata = Map(
          "split_status" -> "failed",
          "error_message" -> error,
          "error_type" -> errorType,
          "mime_type" -> content.mimeType.mimeType
        ) ++ context
      ))
  }
  
  /**
   * Wrap splitting logic with failure handling.
   */
  protected def withFailureHandling[T <: MimeType](
    content: FileContent[T],
    config: SplitConfig
  )(
    splitLogic: => Seq[DocChunk[_ <: MimeType]]
  ): Seq[DocChunk[_ <: MimeType]] = {
    try {
      val chunks = splitLogic
      // Add success metadata if in tag mode
      if (config.failureMode == SplitFailureMode.TagAndPreserve) {
        chunks.map(_.withMetadata("split_status", "success"))
      } else {
        chunks
      }
    } catch {
      case e: SplitException => 
        handleSplitFailure(
          config.failureMode,
          content,
          e.getMessage,
          e,
          config.failureContext ++ e.context
        )
      case e: Exception =>
        handleSplitFailure(
          config.failureMode,
          content,
          s"Unexpected error during splitting: ${e.getMessage}",
          e,
          config.failureContext
        )
    }
  }
}
```

## Implementation Strategy

### Phase 1: Core Infrastructure (Week 1)
1. Implement `SplitFailureMode` enumeration
2. Update `SplitConfig` with new field
3. Create exception hierarchy
4. Enhance `DocChunk` with metadata
5. Implement `SplitFailureHandler` trait

### Phase 2: Update Splitters (Week 2-3)
1. Update each splitter to extend `SplitFailureHandler`
2. Wrap split logic with `withFailureHandling`
3. Add specific error handling for known failure cases
4. Ensure consistent error messages

Example migration:
```scala
object PdfPageSplitter extends DocumentSplitter[MimeType.ApplicationPdf.type] 
    with SplitFailureHandler {
  
  override def split(
    content: FileContent[MimeType.ApplicationPdf.type],
    cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    
    // Validate strategy
    if (!cfg.hasStrategy(SplitStrategy.Page)) {
      return handleSplitFailure(
        cfg.failureMode,
        content,
        s"Invalid strategy '${cfg.strategy.map(_.displayName).getOrElse("none")}' for PDF",
        new InvalidStrategyException(content.mimeType.mimeType, 
          cfg.strategy.map(_.displayName).getOrElse("none"))
      )
    }
    
    // Wrap main logic
    withFailureHandling(content, cfg) {
      val doc = PDDocument.load(content.data)
      try {
        // ... existing split logic ...
      } finally {
        doc.close()
      }
    }
  }
}
```

### Phase 3: Spark Integration (Week 3-4)
1. Update UDF wrappers to handle failure modes
2. Add lineage tracking for failures
3. Create helper functions for filtering failed chunks
4. Add metrics collection

### Phase 4: Testing (Week 4-5)
1. Unit tests for each failure mode
2. Integration tests with Spark
3. Performance benchmarks
4. Migration tests

### Phase 5: Documentation & Rollout (Week 5-6)
1. Update API documentation
2. Create migration guide
3. Add configuration examples
4. Deploy with feature flag

## Testing Strategy

### Unit Tests
```scala
class SplitFailureModeSpec extends AnyFlatSpec with Matchers {
  
  "SplitFailureHandler" should "throw exception in ThrowException mode" in {
    val config = SplitConfig(failureMode = SplitFailureMode.ThrowException)
    val content = FileContent("invalid".getBytes, MimeType.ApplicationPdf)
    
    assertThrows[SplitException] {
      PdfPageSplitter.split(content, config)
    }
  }
  
  it should "return empty sequence in DropDocument mode" in {
    val config = SplitConfig(failureMode = SplitFailureMode.DropDocument)
    val content = FileContent("invalid".getBytes, MimeType.ApplicationPdf)
    
    val result = PdfPageSplitter.split(content, config)
    result shouldBe empty
  }
  
  it should "tag errors in TagAndPreserve mode" in {
    val config = SplitConfig(failureMode = SplitFailureMode.TagAndPreserve)
    val content = FileContent("invalid".getBytes, MimeType.ApplicationPdf)
    
    val result = PdfPageSplitter.split(content, config)
    result.size shouldBe 1
    result.head.isFailed shouldBe true
    result.head.errorMessage shouldBe defined
  }
}
```

### Integration Tests
```scala
class SparkSplitFailureModeIntegrationTest extends SparkTestBase {
  
  test("Pipeline should handle mixed success/failure documents") {
    val df = spark.createDataFrame(Seq(
      ("valid.pdf", validPdfBytes, "application/pdf"),
      ("invalid.pdf", invalidPdfBytes, "application/pdf"),
      ("doc.xlsx", excelBytes, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    )).toDF("path", "content", "mime")
    
    val config = SplitConfig(
      strategy = Some(SplitStrategy.Page),
      failureMode = SplitFailureMode.TagAndPreserve
    )
    
    val result = df.transform(SplitStep(config = config).transform)
    
    // Check success rate
    val stats = result
      .groupBy(col("metadata.split_status"))
      .count()
      .collect()
      .map(r => r.getString(0) -> r.getLong(1))
      .toMap
      
    stats("success") shouldBe 1  // Only valid.pdf succeeds
    stats("failed") shouldBe 2   // Others fail due to wrong strategy
  }
}
```

## Performance Considerations

1. **Metadata Overhead**: Keep metadata maps small
2. **Exception Creation**: Avoid creating exceptions in PreserveAsChunk mode
3. **Lazy Evaluation**: Don't perform expensive checks unless needed
4. **Caching**: Cache failure mode checks in hot paths

## Monitoring & Metrics

### Recommended Metrics
- `splitter.failures.total` - Counter by mime_type, strategy, error_type
- `splitter.failures.rate` - Rate of failures over time
- `splitter.mode.usage` - Which failure modes are being used
- `splitter.recovery.success` - Successful retries after failure

### Dashboard Example
```scala
// Spark SQL for monitoring
spark.sql("""
  SELECT 
    date_trunc('hour', timestamp) as hour,
    mime_type,
    split_status,
    error_type,
    COUNT(*) as count,
    AVG(file_size) as avg_size
  FROM split_results
  WHERE split_status = 'failed'
  GROUP BY 1, 2, 3, 4
  ORDER BY 1 DESC
""")
```

## Configuration Examples

### Application.conf
```hocon
xlcr {
  splitter {
    # Global default failure mode
    default-failure-mode = "tag"
    
    # Per-mime-type overrides
    mime-overrides {
      "application/pdf" = "throw"
      "application/zip" = "drop"
    }
    
    # Failure mode for specific strategies
    strategy-overrides {
      "page" = "throw"
      "attachment" = "preserve"
    }
  }
}
```

### Environment Variables
```bash
export XLCR_SPLIT_FAILURE_MODE=tag
export XLCR_SPLIT_FAILURE_CONTEXT_SOURCE=s3://bucket/path
```

### Spark Configuration
```scala
spark.conf.set("xlcr.splitter.failureMode", "tag")
spark.conf.set("xlcr.splitter.metrics.enabled", "true")
```

## Future Enhancements

1. **Retry Mechanism**: Built-in retry with configurable policies
2. **Fallback Strategies**: Try alternative strategies on failure
3. **Smart Mode Selection**: ML-based failure mode selection
4. **Partial Success**: Return partial results when possible
5. **Async Error Reporting**: Send failures to monitoring systems
6. **Circuit Breaker**: Temporarily disable splitting for problematic sources