# XLCR Spark Retry Framework Design

## Overview

With the new configurable UDF timeout feature, we can build a robust retry framework that handles document processing failures intelligently. Since the binary content remains unchanged in the core schema when transformations fail, we can retry failed documents with different configurations.

## Architecture

### Core Concepts

1. **Error Table**: A designated table storing failed documents with their original binary content and error metadata
2. **Retry Strategies**: Different approaches based on failure type (timeout, memory, parsing errors)
3. **Progressive Timeouts**: Gradually increasing timeouts for difficult documents
4. **Circuit Breaker**: Preventing infinite retry loops for permanently failing documents

### Implementation Strategy

```scala
import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}
import com.tjclp.xlcr.pipeline.spark._
import scala.concurrent.duration._

case class RetryConfig(
  maxRetries: Int = 3,
  backoffMultiplier: Double = 2.0,
  initialTimeout: Duration = 30.seconds,
  maxTimeout: Duration = 10.minutes,
  errorTablePath: String
)

class RetryFramework(config: RetryConfig)(implicit spark: SparkSession) {
  
  /**
   * Process documents with automatic retry handling.
   * Failed documents are written to an error table for later retry.
   */
  def processWithRetry(
    inputDf: DataFrame,
    pipeline: SparkStep,
    retryAttempt: Int = 0
  ): DataFrame = {
    
    // Add retry metadata to track attempts
    val dfWithRetryMeta = inputDf
      .withColumn("retry_attempt", F.lit(retryAttempt))
      .withColumn("process_timestamp", F.current_timestamp())
    
    // Process documents
    val processed = pipeline(dfWithRetryMeta)
    
    // Separate successful and failed documents
    val (successful, failed) = partitionBySuccess(processed)
    
    // Write failed documents to error table with metadata
    if (failed.count() > 0) {
      writeToErrorTable(failed, pipeline.name, retryAttempt)
    }
    
    successful
  }
  
  /**
   * Retry documents from the error table with progressive timeout increases.
   */
  def retryFailedDocuments(): DataFrame = {
    val errorDf = spark.read.parquet(config.errorTablePath)
    
    // Group by failure type and retry with appropriate strategy
    val timeoutFailures = errorDf.filter(F.array_contains(F.col("lineage.error"), "timeout"))
    val otherFailures = errorDf.filter(!F.array_contains(F.col("lineage.error"), "timeout"))
    
    // Retry timeout failures with increased timeouts
    val timeoutRetries = retryWithProgressiveTimeout(timeoutFailures)
    
    // Retry other failures with same configuration
    val otherRetries = retryWithSameConfig(otherFailures)
    
    timeoutRetries.unionByName(otherRetries)
  }
  
  private def partitionBySuccess(df: DataFrame): (DataFrame, DataFrame) = {
    val hasError = F.col("lineage").getItem(F.size(F.col("lineage")) - 1).getField("error").isNotNull
    
    val successful = df.filter(!hasError)
    val failed = df.filter(hasError)
    
    (successful, failed)
  }
  
  private def writeToErrorTable(
    failed: DataFrame,
    pipelineName: String,
    retryAttempt: Int
  ): Unit = {
    import CoreSchema._
    
    // Preserve original binary content and add error metadata
    val errorRecords = failed
      .withColumn("pipeline_name", F.lit(pipelineName))
      .withColumn("retry_attempt", F.lit(retryAttempt))
      .withColumn("error_timestamp", F.current_timestamp())
      .withColumn("last_error", 
        F.col("lineage").getItem(F.size(F.col("lineage")) - 1).getField("error")
      )
      .withColumn("total_attempts", F.lit(retryAttempt + 1))
    
    // Append to error table
    errorRecords.write
      .mode("append")
      .parquet(config.errorTablePath)
  }
  
  private def retryWithProgressiveTimeout(timeoutDf: DataFrame): DataFrame = {
    // Calculate new timeout based on retry attempt
    val retriesWithTimeout = timeoutDf.withColumn(
      "new_timeout_seconds",
      F.least(
        F.col("retry_attempt") * config.backoffMultiplier * config.initialTimeout.toSeconds,
        F.lit(config.maxTimeout.toSeconds)
      )
    )
    
    // Group by timeout and process batches
    val timeoutGroups = retriesWithTimeout
      .select("new_timeout_seconds")
      .distinct()
      .collect()
      .map(_.getDouble(0))
    
    timeoutGroups.map { timeoutSeconds =>
      val batchDf = retriesWithTimeout.filter(F.col("new_timeout_seconds") === timeoutSeconds)
      val timeout = Duration(timeoutSeconds, "seconds")
      
      // Rebuild pipeline with new timeout
      val pipeline = rebuildPipelineWithTimeout(batchDf, timeout)
      
      processWithRetry(
        batchDf.drop("new_timeout_seconds"),
        pipeline,
        batchDf.select(F.max("retry_attempt")).first().getInt(0) + 1
      )
    }.reduce(_ unionByName _)
  }
  
  private def rebuildPipelineWithTimeout(df: DataFrame, timeout: Duration): SparkStep = {
    // Extract pipeline steps from lineage
    val pipelineSteps = df
      .select(F.col("lineage.name"))
      .first()
      .getSeq[String](0)
      .distinct
    
    // Rebuild pipeline with new timeout
    val steps = pipelineSteps.map { stepName =>
      val baseStep = SparkPipelineRegistry.get(stepName)
      SparkStep.withUdfTimeout(baseStep, timeout)
    }
    
    steps.reduce(_ andThen _)
  }
}

/**
 * Advanced retry strategies for specific failure patterns.
 */
object RetryStrategies {
  
  /**
   * Retry with content chunking for large documents that timeout.
   */
  def chunkedRetry(df: DataFrame, originalPipeline: SparkStep): DataFrame = {
    // Add aggressive splitting for large documents
    val chunkingPipeline = SplitStep(
      udfTimeout = 5.minutes,
      config = SplitConfig(
        strategy = Some(SplitStrategy.BySize),
        maxChunkSize = Some(1024 * 1024) // 1MB chunks
      )
    ).andThen(originalPipeline)
    
    chunkingPipeline(df)
  }
  
  /**
   * Retry with fallback converters for problematic formats.
   */
  def fallbackConverterRetry(df: DataFrame, targetMime: MimeType): DataFrame = {
    // Try alternative conversion bridges
    val fallbackPipeline = ConvertStep(
      to = MimeType.TextPlain, // Simpler target format
      udfTimeout = 2.minutes
    ).andThen(
      ConvertStep(to = targetMime, udfTimeout = 2.minutes)
    )
    
    fallbackPipeline(df)
  }
  
  /**
   * Circuit breaker to prevent infinite retries.
   */
  def shouldRetry(errorDf: DataFrame, maxRetries: Int): DataFrame = {
    errorDf.filter(F.col("total_attempts") < maxRetries)
  }
}

/**
 * Example usage of the retry framework.
 */
object RetryExample {
  def main(args: Array[String]): Unit = {
    implicit val spark = SparkSession.builder()
      .appName("xlcr-retry-example")
      .getOrCreate()
    
    val retryConfig = RetryConfig(
      maxRetries = 3,
      backoffMultiplier = 2.0,
      initialTimeout = 30.seconds,
      maxTimeout = 10.minutes,
      errorTablePath = "/data/xlcr/error_table"
    )
    
    val framework = new RetryFramework(retryConfig)
    
    // Build initial pipeline
    val pipeline = DetectMime()
      .andThen(SplitStep())
      .andThen(ConvertStep(to = MimeType.ApplicationPdf))
      .andThen(ExtractText)
    
    // Process new documents
    val inputDf = spark.read
      .format("binaryFile")
      .load("/data/documents/new")
    
    val successful = framework.processWithRetry(inputDf, pipeline)
    
    // Periodic retry job for failed documents
    val retried = framework.retryFailedDocuments()
    
    // Handle permanently failed documents
    val permanentFailures = spark.read
      .parquet(retryConfig.errorTablePath)
      .filter(F.col("total_attempts") >= retryConfig.maxRetries)
    
    // Alert or manual review for permanent failures
    permanentFailures.write
      .mode("overwrite")
      .parquet("/data/xlcr/permanent_failures")
  }
}
```

## Key Features

### 1. Progressive Timeout Strategy
- Start with default timeout (30s)
- Double timeout on each retry attempt
- Cap at maximum timeout (10 minutes)
- Group documents by timeout to optimize processing

### 2. Error Table Schema
The error table preserves the full core schema plus retry metadata:
```scala
root
 |-- id: string (md5 hash)
 |-- path: string (original file path)
 |-- content: binary (original file content - unchanged!)
 |-- mime: string
 |-- metadata: map<string, string>
 |-- lineage: array<struct>
 |-- pipeline_name: string
 |-- retry_attempt: integer
 |-- error_timestamp: timestamp
 |-- last_error: string
 |-- total_attempts: integer
```

### 3. Retry Patterns

#### Timeout-Based Retry
```scala
// Documents that timed out get progressively longer timeouts
val timeoutRetry = failed
  .filter(_.lineage.last.error == "timeout")
  .map(doc => (doc, calculateNewTimeout(doc.retryAttempt)))
  .map { case (doc, timeout) =>
    SparkStep.withUdfTimeout(pipeline, timeout)(doc)
  }
```

#### Content-Based Retry
```scala
// Large documents get chunked into smaller pieces
val largeDocRetry = failed
  .filter(_.content.length > 50 * 1024 * 1024) // > 50MB
  .transform(chunkedRetry)
```

#### Format-Based Retry
```scala
// Problematic formats use alternative conversion paths
val formatRetry = failed
  .filter(_.mime.contains("application/vnd.ms-"))
  .transform(fallbackConverterRetry)
```

### 4. Monitoring and Alerting

```scala
// Monitor retry success rates
val retryMetrics = errorTable
  .groupBy("pipeline_name", "retry_attempt")
  .agg(
    F.count("*").as("total_documents"),
    F.sum(F.when(F.col("last_error").isNull, 1).otherwise(0)).as("successful_retries"),
    F.collect_list("last_error").as("error_types")
  )

// Alert on documents that fail repeatedly
val alertThreshold = 3
val needsAttention = errorTable
  .filter(F.col("total_attempts") >= alertThreshold)
  .select("path", "mime", "last_error", "total_attempts")
```

## Benefits

1. **Automatic Recovery**: Transient failures (network, temporary resource constraints) are automatically retried
2. **Progressive Handling**: Difficult documents get more resources on each retry
3. **No Data Loss**: Original binary content is preserved, allowing unlimited retry strategies
4. **Observability**: Full lineage tracking shows exactly where and why documents failed
5. **Flexibility**: Different retry strategies for different failure types
6. **Cost Optimization**: Only allocate extra resources (time/memory) for documents that need it

## Best Practices

1. **Set Reasonable Defaults**: Start with 30s timeout, max 10 minutes
2. **Monitor Error Rates**: Track retry success to tune parameters
3. **Implement Circuit Breakers**: Prevent infinite retry loops
4. **Use Batching**: Group retries by timeout to optimize Spark job execution
5. **Clean Up**: Periodically archive or purge old error records
6. **Alert on Patterns**: Identify systematic failures (e.g., all PDFs from specific source failing)

## Future Enhancements

1. **ML-Based Timeout Prediction**: Use document features to predict optimal timeout
2. **Dynamic Pipeline Selection**: Choose different processing paths based on document characteristics
3. **Cost-Based Optimization**: Balance processing time vs. infrastructure cost
4. **Automated Root Cause Analysis**: Analyze lineage patterns to identify common failure modes