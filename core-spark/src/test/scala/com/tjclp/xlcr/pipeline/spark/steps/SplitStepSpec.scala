package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.{CoreSchema, UdfHelpers}
import types.MimeType
import utils.{SplitConfig, SplitStrategy}

import org.apache.spark.sql.SparkSession
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.File
import java.nio.file.{Files, Paths}

class SplitStepSpec extends AnyFlatSpec with Matchers with org.scalatest.BeforeAndAfterAll {
  
  // Initialize Spark
  implicit val spark: SparkSession = SparkSession.builder()
    .appName("SplitStepTest")
    .master("local[2]")
    .getOrCreate()
  
  import spark.implicits._
  import org.apache.spark.sql.functions._
  
  "SplitStep" should "split text file using Chunk strategy" in {
    // Load the test text file
    val textPath = getClass.getResource("/test.txt").getPath
    val textBytes = Files.readAllBytes(Paths.get(textPath))
    
    // Create a simple DataFrame with the text file
    val textDf = Seq(
      ("text-1", textBytes, MimeType.TextPlain.mimeType, Map.empty[String, String])
    ).toDF(CoreSchema.Id, CoreSchema.Content, CoreSchema.Mime, CoreSchema.Metadata)
    
    // Initialize with empty lineage array
    val inputDf = textDf.withColumn(CoreSchema.Lineage, array())
    
    // Create the SplitStep with Chunk strategy
    val splitStep = SplitStep(
      config = SplitConfig(
        strategy = Some(SplitStrategy.Chunk),
        maxChars = 100 // Small enough to split our sample
      )
    )
    
    // Apply the split
    val result = splitStep.transform(inputDf)
    
    // Print results for inspection
    println("=== Text Splitting Results ===")
    result.select(
      col(CoreSchema.Id),
      col(CoreSchema.ChunkLabel),
      col(CoreSchema.ChunkIndex),
      col(CoreSchema.ChunkTotal)
    ).show(false)
    
    // Verify that we have multiple chunks
    val chunkCount = result.count()
    chunkCount should be > 1L
    
    // Verify that the chunks have the expected labels
    val labels = result.select(CoreSchema.ChunkLabel).collect().map(_.getString(0))
    
    // Check that at least one label follows our paragraph pattern
    val paragraphLabels = labels.filter(_.startsWith("paragraphs"))
    paragraphLabels.nonEmpty shouldBe true
    
    // Verify lineage captures the implementation
    val lineageImplementation = result.select(
      expr(s"explode(${CoreSchema.Lineage}) as l")
    ).select("l.implementation").collect().head.getString(0)
    
    lineageImplementation shouldBe "TextSplitter"
  }
  
  it should "split CSV file using Row strategy" in {
    // Load the test CSV file
    val csvPath = getClass.getResource("/test.csv").getPath
    val csvBytes = Files.readAllBytes(Paths.get(csvPath))
    
    // Create a simple DataFrame with the CSV file
    val csvDf = Seq(
      ("csv-1", csvBytes, MimeType.TextCsv.mimeType, Map.empty[String, String])
    ).toDF(CoreSchema.Id, CoreSchema.Content, CoreSchema.Mime, CoreSchema.Metadata)
    
    // Initialize with empty lineage array
    val inputDf = csvDf.withColumn(CoreSchema.Lineage, array())
    
    // Create the SplitStep with Row strategy
    val splitStep = SplitStep(
      config = SplitConfig(
        strategy = Some(SplitStrategy.Row)
      )
    )
    
    // Apply the split
    val result = splitStep.transform(inputDf)
    
    // Print results for inspection
    println("=== CSV Splitting Results ===")
    result.select(
      col(CoreSchema.Id),
      col(CoreSchema.ChunkLabel),
      col(CoreSchema.ChunkIndex),
      col(CoreSchema.ChunkTotal)
    ).show(false)
    
    // Verify that we have one chunk per row (excluding header)
    val expectedRows = 4L
    result.count() shouldBe expectedRows
    
    // Verify that the chunks have the expected labels
    val labels = result.select(CoreSchema.ChunkLabel).collect().map(_.getString(0))
    
    // Check that labels follow our row pattern
    labels.forall(_.startsWith("row")) shouldBe true
    
    // Verify lineage captures the implementation
    val lineageImplementation = result.select(
      expr(s"explode(${CoreSchema.Lineage}) as l")
    ).select("l.implementation").collect().head.getString(0)
    
    lineageImplementation shouldBe "CsvSplitter"
  }
  
  // Cleanup Spark session after tests
  override def afterAll(): Unit = {
    spark.stop()
  }
}