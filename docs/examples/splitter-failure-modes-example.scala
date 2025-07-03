// Splitter Failure Modes - Usage Examples

import com.tjclp.xlcr._
import com.tjclp.xlcr.splitters._
import com.tjclp.xlcr.pipeline.spark._
import org.apache.spark.sql.SparkSession

object SplitterFailureModesExample {
  
  implicit val spark: SparkSession = SparkSession.builder()
    .appName("SplitterFailureModes")
    .master("local[*]")
    .getOrCreate()
    
  import spark.implicits._
  
  // Example 1: Production Pipeline with Monitoring
  def productionPipeline(): Unit = {
    println("=== Production Pipeline with TagAndPreserve ===")
    
    val documents = Seq(
      ("report.pdf", loadFile("report.pdf"), "application/pdf"),
      ("corrupted.pdf", Array[Byte](0, 1, 2, 3), "application/pdf"),
      ("data.xlsx", loadFile("data.xlsx"), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
      ("empty.pdf", Array.empty[Byte], "application/pdf")
    ).toDF("filename", "content", "mime_type")
    
    // Configure for monitoring - tag failures but continue processing
    val splitConfig = SplitConfig(
      strategy = Some(SplitStrategy.Auto),
      failureMode = SplitFailureMode.TagAndPreserve,
      failureContext = Map("environment" -> "production", "version" -> "1.0")
    )
    
    val results = documents
      .transform(SplitStep(config = splitConfig).transform)
      .cache()
    
    // Monitor failures
    println("Split Results Summary:")
    results
      .groupBy($"metadata.split_status")
      .count()
      .show()
    
    // Investigate failures
    println("Failed Documents:")
    results
      .filter($"metadata.split_status" === "failed")
      .select($"filename", $"metadata.error_type", $"metadata.error_message")
      .show(truncate = false)
    
    // Process only successful splits
    val successfulChunks = results
      .filter($"metadata.split_status" =!= "failed")
      .select($"filename", $"chunk_index", $"chunk_label")
    
    println(s"Successfully processed ${successfulChunks.count()} chunks")
  }
  
  // Example 2: Data Validation Pipeline with Strict Mode
  def validationPipeline(): Unit = {
    println("\n=== Validation Pipeline with ThrowException ===")
    
    val testDocuments = Seq(
      ("test1.pdf", loadFile("valid.pdf"), "application/pdf"),
      ("test2.pdf", Array[Byte](255, 254, 253), "application/pdf") // Invalid PDF
    ).toDF("filename", "content", "mime_type")
    
    // Strict validation - fail immediately on any error
    val strictConfig = SplitConfig(
      strategy = Some(SplitStrategy.Page),
      failureMode = SplitFailureMode.ThrowException,
      chunkRange = Some(0 until 10) // Also limit pages
    )
    
    try {
      val validated = testDocuments
        .transform(SplitStep(config = strictConfig).transform)
        .count()
        
      println(s"All $validated documents passed validation!")
      
    } catch {
      case e: SplitException =>
        println(s"Validation failed: ${e.getMessage}")
        println(s"Failed document type: ${e.mimeType}")
        println(s"Context: ${e.context}")
        // In real scenario, would trigger alerts or retry logic
    }
  }
  
  // Example 3: Performance-Optimized Pipeline
  def performancePipeline(): Unit = {
    println("\n=== Performance Pipeline with DropDocument ===")
    
    val largeBatch = spark.read
      .option("binaryFormat", "true")
      .load("s3://bucket/documents/*")
      .withColumn("mime_type", detectMimeType($"content"))
    
    // Drop problematic documents to maintain throughput
    val performanceConfig = SplitConfig(
      strategy = Some(SplitStrategy.Auto),
      failureMode = SplitFailureMode.DropDocument,
      maxChars = 5000,
      chunkRange = Some(0 until 50) // Limit chunks per document
    )
    
    val processed = largeBatch
      .transform(SplitStep(config = performanceConfig).transform)
      .filter($"content".isNotNull) // Dropped documents have null content
    
    // Log dropped document count for monitoring
    val droppedCount = largeBatch.count() - processed.select($"source_id").distinct().count()
    println(s"Dropped $droppedCount problematic documents")
    
    // Continue with high-performance processing
    processed
      .transform(ConvertStep(MimeType.TextPlain).transform)
      .write
      .mode("overwrite")
      .parquet("s3://bucket/processed/")
  }
  
  // Example 4: Retry Logic with Different Strategies
  def retryPipeline(): Unit = {
    println("\n=== Retry Pipeline with Fallback Strategies ===")
    
    val documents = loadDocuments()
    
    // First attempt with specific strategy
    val firstAttempt = documents
      .withColumn("attempt", lit(1))
      .transform(
        SplitStep(config = SplitConfig(
          strategy = Some(SplitStrategy.Page),
          failureMode = SplitFailureMode.TagAndPreserve
        )).transform
      )
    
    // Identify failures
    val failures = firstAttempt
      .filter($"metadata.split_status" === "failed")
      .drop("metadata") // Clear previous attempt metadata
    
    // Retry with different strategy
    val secondAttempt = failures
      .withColumn("attempt", lit(2))
      .transform(
        SplitStep(config = SplitConfig(
          strategy = Some(SplitStrategy.Chunk), // Try chunk-based splitting
          failureMode = SplitFailureMode.TagAndPreserve,
          maxChars = 10000 // Larger chunks
        )).transform
      )
    
    // Combine results
    val allResults = firstAttempt
      .filter($"metadata.split_status" =!= "failed")
      .union(secondAttempt)
    
    println("Retry Results:")
    allResults
      .groupBy($"attempt", $"metadata.split_status")
      .count()
      .orderBy($"attempt")
      .show()
  }
  
  // Example 5: Custom Failure Handling
  def customFailureHandling(): Unit = {
    println("\n=== Custom Failure Handling ===")
    
    val documents = loadDocuments()
    
    // Process with tagging
    val tagged = documents.transform(
      SplitStep(config = SplitConfig(
        failureMode = SplitFailureMode.TagAndPreserve
      )).transform
    )
    
    // Custom handling based on error type
    val processed = tagged.map { row =>
      val metadata = row.getAs[Map[String, String]]("metadata")
      metadata.get("error_type") match {
        case Some("CorruptedDocumentException") =>
          // Send to document repair service
          repairDocument(row)
          
        case Some("InvalidStrategyException") =>
          // Log for configuration update
          logInvalidStrategy(row)
          
        case Some("TimeoutException") =>
          // Queue for async processing
          queueForAsync(row)
          
        case _ =>
          // Standard processing
          row
      }
    }
  }
  
  // Example 6: Monitoring and Alerting
  def monitoringExample(): Unit = {
    println("\n=== Monitoring Example ===")
    
    val results = processDocumentsWithTagging()
    
    // Create monitoring metrics
    val metrics = results
      .select(
        $"metadata.split_status".as("status"),
        $"metadata.error_type".as("error_type"),
        $"mime_type",
        $"metadata.error_message".as("error_message")
      )
      .groupBy($"status", $"error_type", $"mime_type")
      .agg(
        count("*").as("count"),
        first($"error_message").as("sample_error")
      )
    
    // Alert on high failure rates
    val failureRate = results
      .agg(
        sum(when($"metadata.split_status" === "failed", 1).otherwise(0)).as("failures"),
        count("*").as("total")
      )
      .select(($"failures" / $"total").as("failure_rate"))
      .first()
      .getDouble(0)
    
    if (failureRate > 0.05) { // 5% threshold
      sendAlert(s"High splitter failure rate: ${failureRate * 100}%")
    }
    
    // Export metrics
    metrics.write
      .mode("append")
      .partitionBy("status")
      .json("s3://metrics/splitter-failures/")
  }
  
  // Helper functions
  def loadFile(name: String): Array[Byte] = {
    // Load file content
    ???
  }
  
  def detectMimeType(content: Column): Column = {
    // UDF to detect MIME type
    ???
  }
  
  def loadDocuments(): DataFrame = {
    // Load test documents
    ???
  }
  
  def processDocumentsWithTagging(): DataFrame = {
    // Process with tagging enabled
    ???
  }
  
  def repairDocument(row: Row): Unit = {
    println(s"Sending document for repair: ${row.getString(0)}")
  }
  
  def logInvalidStrategy(row: Row): Unit = {
    println(s"Invalid strategy for document: ${row.getString(0)}")
  }
  
  def queueForAsync(row: Row): Unit = {
    println(s"Queueing document for async processing: ${row.getString(0)}")
  }
  
  def sendAlert(message: String): Unit = {
    println(s"ALERT: $message")
  }
  
  // Run examples
  def main(args: Array[String]): Unit = {
    productionPipeline()
    validationPipeline()
    performancePipeline()
    retryPipeline()
    customFailureHandling()
    monitoringExample()
    
    spark.stop()
  }
}