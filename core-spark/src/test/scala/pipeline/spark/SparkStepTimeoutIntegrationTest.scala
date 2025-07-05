package com.tjclp.xlcr
package pipeline.spark

import scala.concurrent.duration._

import org.apache.spark.sql.SparkSession
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import pipeline.spark.steps._

class SparkStepTimeoutIntegrationTest extends AnyFunSuite with Matchers {

  test("UDF timeout should be properly applied when using SparkStep.withUdfTimeout") {
    implicit val spark: SparkSession = SparkSession
      .builder()
      .appName("SparkStep Timeout Integration Test")
      .master("local[*]")
      .getOrCreate()

    try {
      import org.apache.spark.sql.Row
      import org.apache.spark.sql.types._
      import spark.implicits._
      
      // Create a test DataFrame with some binary content
      val schema = StructType(Seq(
        StructField("path", StringType, false),
        StructField("content", BinaryType, false)
      ))
      
      val rows = Seq(
        Row("test1.pdf", Array[Byte](1, 2, 3, 4, 5)),
        Row("test2.pdf", Array[Byte](6, 7, 8, 9, 10))
      )
      
      val testData = spark.createDataFrame(spark.sparkContext.parallelize(rows), schema)

      // Ensure core schema
      val coreData = CoreSchema.ensure(testData
        .withColumn(CoreSchema.Id, org.apache.spark.sql.functions.md5($"content"))
        .withColumn(CoreSchema.Mime, org.apache.spark.sql.functions.lit("application/pdf"))
        .withColumn(CoreSchema.Lineage, org.apache.spark.sql.functions.array()))

      // Create steps with different timeouts
      val detectStep           = DetectMime()
      val splitStepDefault     = SplitStep()
      val splitStepWithTimeout = SparkStep.withUdfTimeout(SplitStep(), 120.seconds)

      // Verify the timeouts are set correctly
      detectStep.udfTimeout shouldBe 30.seconds
      splitStepDefault.udfTimeout shouldBe 60.seconds
      splitStepWithTimeout.udfTimeout shouldBe 120.seconds

      // Apply the steps (this will use the configured timeouts internally)
      val detected         = detectStep.transform(coreData)
      val splitDefault     = splitStepDefault.transform(detected)
      val splitWithTimeout = splitStepWithTimeout.transform(detected)

      // Both should produce results (we're not testing timeout behavior, just that the configuration works)
      splitDefault.count() should be > 0L
      splitWithTimeout.count() should be > 0L

      // The lineage should show the correct step names
      import org.apache.spark.sql.functions._
      val defaultLineage = splitDefault.select(explode(col(CoreSchema.Lineage)).as("lineage"))
        .select("lineage.name")
        .distinct()
        .collect()
        .map(_.getString(0))

      val timeoutLineage = splitWithTimeout.select(explode(col(CoreSchema.Lineage)).as("lineage"))
        .select("lineage.name")
        .distinct()
        .collect()
        .map(_.getString(0))

      defaultLineage should contain("splitAuto")
      timeoutLineage should contain("splitAuto")

    } finally
      spark.stop()
  }
}
